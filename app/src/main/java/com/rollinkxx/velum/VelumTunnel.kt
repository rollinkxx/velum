package com.rollinkxx.velum

import android.content.Context
import android.os.SystemClock
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

/**
 * Pengelola tunnel WARP berbasis WireGuard (GoBackend).
 * Singleton ringan: satu backend, satu tunnel, tanpa service tambahan —
 * VpnService milik library yang menjaga proses tetap hidup selama tersambung.
 *
 * **Kelas ini pemilik tunggal keadaan koneksi.** Dalam satu proses ada beberapa pelaku
 * yang bisa menyalakan atau mematikan tunnel — layar utama, ubin pengaturan cepat,
 * receiver boot, dan pemantau jaringan — dan masing-masing punya thread sendiri.
 * Karena itu:
 *
 * - [up], [down], dan [restart] memakai **satu kunci yang sama** (`@Synchronized` pada
 *   object ini). Tanpa itu, `down` dari satu pelaku bisa disela `up` dari pelaku lain
 *   dan tunnel hidup lagi setelah pengguna menekan Putuskan — untuk aplikasi VPN itu
 *   kebocoran niat pengguna, bukan sekadar kosmetik.
 * - [upSinceElapsedMs] dan notifikasi status diperbarui di sini, bukan di Activity:
 *   keduanya wajib mengikuti umur **tunnel** (proses), bukan umur **layar** (Activity).
 */
object VelumTunnel : Tunnel {
    private const val NAME = "velum"
    private const val MTU = 1280
    private const val DNS = "1.1.1.1, 1.0.0.1"
    private const val ALLOWED_IPS = "0.0.0.0/0, ::/0"

    @Volatile
    private var backend: GoBackend? = null

    /**
     * Context aplikasi, disimpan saat backend dibuat. Dipakai [updateNotification] yang
     * dipanggil dari thread backend — di sana tidak ada Context lain yang tersedia.
     */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    /**
     * Kapan tunnel terakhir naik, dalam basis [SystemClock.elapsedRealtime]; `0` bila turun.
     *
     * Sengaja disimpan di sini, bukan di Activity: layar dibuat ulang setiap rotasi, dan
     * durasi yang kembali ke `00:00` padahal koneksi tidak pernah putus adalah informasi
     * yang salah. `elapsedRealtime` dipakai (bukan jam dinding) karena tidak terpengaruh
     * perubahan waktu oleh pengguna atau operator.
     */
    @Volatile
    var upSinceElapsedMs: Long = 0L
        private set

    /** Callback UI; dipanggil dari thread backend, penerima harus pindah ke main thread sendiri. */
    @Volatile
    var listener: ((Tunnel.State) -> Unit)? = null

    override fun getName(): String = NAME

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
        if (newState == Tunnel.State.UP) {
            // Hanya diisi bila belum terisi: pantulan down->up yang cepat tidak boleh
            // mereset durasi yang sudah berjalan.
            if (upSinceElapsedMs == 0L) upSinceElapsedMs = SystemClock.elapsedRealtime()
        } else {
            upSinceElapsedMs = 0L
        }
        updateNotification(newState)
        listener?.invoke(newState)
    }

    /**
     * Notifikasi status mengikuti **tunnel**, bukan Activity.
     *
     * Sebelumnya `show`/`hide` hanya dipanggil dari callback visual layar utama, akibatnya:
     * tunnel yang mati di latar (pantulan menyerah setelah 5 percobaan, atau diputus lewat
     * ubin) meninggalkan notifikasi "Tersambung" yang basi selamanya, dan menyambung lewat
     * ubin saat aplikasi tertutup tidak memunculkan notifikasi sama sekali.
     *
     * Aman dipanggil dari thread backend: `NotificationManager.notify` thread-safe, dan
     * `StatusNotifier.show` sudah menelan `SecurityException` bila izin notifikasi ditolak.
     */
    private fun updateNotification(newState: Tunnel.State) {
        val ctx = appContext ?: return
        if (newState == Tunnel.State.UP) {
            StatusNotifier.show(ctx, ctx.getString(R.string.notif_connected))
        } else {
            StatusNotifier.hide(ctx)
        }
    }

    private fun backend(context: Context): GoBackend {
        appContext = context.applicationContext
        return backend ?: synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }
    }

    /** Sinkronkan status dengan backend (mis. setelah proses dibuat ulang). Blocking. */
    @Synchronized
    fun refreshState(context: Context): Tunnel.State {
        val s = backend(context).getState(this)
        state = s
        return s
    }

    /** Menyalakan tunnel. Blocking; panggil dari thread latar. */
    @Synchronized
    @Throws(Exception::class)
    fun up(context: Context, prefs: Prefs) {
        backend(context).setState(this, Tunnel.State.UP, buildConfig(prefs))
    }

    /** Mematikan tunnel. Blocking; panggil dari thread latar. */
    @Synchronized
    @Throws(Exception::class)
    fun down(context: Context) {
        backend(context).setState(this, Tunnel.State.DOWN, null)
    }

    /**
     * Mematikan lalu menyalakan tunnel dengan konfigurasi terbaru, sebagai **satu operasi
     * atomik** terhadap pelaku lain.
     *
     * Dipakai saat memutar endpoint: pasangan `down` + `up` yang dipanggil terpisah bisa
     * disela `down` dari pelaku lain (mis. pengguna menekan Putuskan), sehingga tunnel
     * berakhir hidup padahal pengguna memintanya mati.
     *
     * Niat pengguna ([Prefs.wasUp]) dibaca ulang SETELAH `down` dan di dalam kunci:
     * bila ia memutus di tengah jalan, tunnel tidak dihidupkan lagi.
     */
    @Synchronized
    @Throws(Exception::class)
    fun restart(context: Context, prefs: Prefs) {
        val b = backend(context)
        b.setState(this, Tunnel.State.DOWN, null)
        if (!prefs.wasUp) return // pengguna memutus selama operasi ini mengantre
        b.setState(this, Tunnel.State.UP, buildConfig(prefs))
    }

    /** Hasil baca statistik transfer dari backend; null bila gagal. */
    class TrafficStats(val rxBytes: Long, val txBytes: Long, val latestHandshakeMs: Long)

    /**
     * Membaca statistik transfer (jumlah semua peer). Blocking ringan;
     * null bila backend gagal. Panggil dari thread latar.
     *
     * Sengaja TIDAK `@Synchronized`: dipanggil berulang dari [VelumController.awaitHandshake]
     * dan dari tiker UI, dan tidak boleh ikut mengantre di belakang `up()` yang lambat —
     * kalau mengantre, menunggu handshake justru bisa macet selama tunnel dibangun.
     */
    fun traffic(context: Context): TrafficStats? {
        return try {
            val stats = backend(context).getStatistics(this)
            var rx = 0L
            var tx = 0L
            var hs = 0L
            for (key in stats.peers()) {
                val p = stats.peer(key) ?: continue
                rx += p.rxBytes
                tx += p.txBytes
                if (p.latestHandshakeEpochMillis > hs) hs = p.latestHandshakeEpochMillis
            }
            TrafficStats(rx, tx, hs)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildConfig(prefs: Prefs): Config {
        val addresses = buildString {
            append(prefs.addressV4).append("/32")
            prefs.addressV6?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it).append("/128") }
        }
        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(requireNotNull(prefs.privateKey))
            .parseAddresses(addresses)
            .parseDnsServers(DNS)
            .parseMtu(MTU.toString())
        val excluded = prefs.excludedApps
        if (excluded.isNotEmpty()) ifaceBuilder.excludeApplications(excluded)
        val iface = ifaceBuilder.build()
        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(prefs.peerPublicKey))
            .parseAllowedIPs(ALLOWED_IPS)
            .parseEndpoint(prefs.effectiveEndpoint ?: VelumApi.DEFAULT_ENDPOINT)
            .parsePersistentKeepalive("25")
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }
}
