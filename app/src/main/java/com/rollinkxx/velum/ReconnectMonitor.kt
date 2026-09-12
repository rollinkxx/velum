package com.rollinkxx.velum

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import android.util.Log
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Menjaga tunnel tetap tersambung saat konektivitas berubah (pindah Wi-Fi/data,
 * putus sesaat) dengan memantul tunnel sekali pakai backoff.
 *
 * Lingkup aplikasi, bukan Activity: tetap bekerja walau UI ditutup, karena proses
 * aplikasi dijaga hidup oleh foreground service library selama tunnel UP.
 * Aktif hanya bila diniatkan tersambung ([Prefs.wasUp]); putus manual menghentikannya.
 */
object ReconnectMonitor {
    private const val TAG = "Velum"
    private const val DEBOUNCE_MS = 3000L

    /**
     * Jeda pantulan bertahap. Tiga percobaan cepat saja terlalu mudah menyerah:
     * jaringan yang baru berganti (habis pindah Wi-Fi, baru keluar dari mode pesawat,
     * baru menyala setelah boot) sering butuh belasan detik sebelum benar-benar siap.
     * Pantulan tetap dibatalkan begitu pengguna menekan Putuskan.
     */
    private val BACKOFF_MS = longArrayOf(2000, 5000, 10000, 30000, 60000)

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastBounceMs = 0L

    @Volatile
    private var bouncing = false

    /** Mulai memantau; aman dipanggil berulang. Panggil setelah tersambung. */
    @Synchronized
    fun ensure(context: Context) {
        if (callback != null) return
        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (fromOwnTunnel(app, network)) return
                scheduleBounce(app, "tersedia")
            }

            override fun onLost(network: Network) {
                if (fromOwnTunnel(app, network)) return
                scheduleBounce(app, "hilang")
            }
        }
        // Abaikan callback lengket awal untuk jaringan yang sedang aktif.
        lastBounceMs = SystemClock.elapsedRealtime()
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "gagal mendaftar network callback", e)
            return
        }
        callback = cb
    }

    /** Berhenti memantau; aman dipanggil berulang. Panggil saat putus manual. */
    @Synchronized
    fun stop(context: Context) {
        val cb = callback ?: return
        callback = null
        try {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            Log.w(TAG, "gagal melepas network callback", e)
        }
    }

    /**
     * Apakah peristiwa jaringan ini berasal dari tunnel Velum sendiri.
     *
     * `registerDefaultNetworkCallback` melaporkan jaringan **default**, dan begitu tunnel
     * naik, jaringan VPN itulah yang menjadi default — jadi kenaikan tunnel memicu
     * `onAvailable` untuk dirinya sendiri. Bila peristiwa itu ikut memicu pantulan, tunnel
     * yang baru saja sehat justru dimatikan lagi.
     *
     * Celah ini nyata pada satu kondisi spesifik: debounce 3 detik hanya menahan peristiwa
     * susulan bila pantulan berhasil pada percobaan PERTAMA (jeda 2 detik < 3 detik). Bila
     * berhasil pada percobaan ke-2 atau ke-3 (jeda 5/10 detik > 3 detik), peristiwa akibat
     * tunnel sendiri lolos debounce dan memicu pantulan berikutnya.
     *
     * Menyaring lewat `TRANSPORT_VPN` benar **terlepas dari apakah skenario itu sudah
     * pernah terjadi di lapangan**: peristiwa yang disebabkan tunnel ini memang bukan
     * alasan yang sah untuk memantulkannya. `getNetworkCapabilities(Network)` ada sejak
     * API 23 dan `TRANSPORT_VPN` sejak API 21 — keduanya di bawah minSdk 24.
     */
    private fun fromOwnTunnel(app: Context, network: Network): Boolean = try {
        app.getSystemService(ConnectivityManager::class.java)
            ?.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) {
        false // ragu: perlakukan sebagai peristiwa jaringan biasa
    }

    private fun scheduleBounce(app: Context, reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceMs < DEBOUNCE_MS || bouncing) return
        lastBounceMs = now
        bouncing = true
        worker.execute {
            try {
                val prefs = Prefs.of(app)
                if (!prefs.wasUp || !prefs.isRegistered) return@execute
                if (VelumTunnel.state != Tunnel.State.UP) {
                    tryUpOnce(app, prefs)
                    return@execute
                }
                Log.i(TAG, "jaringan $reason: memantul tunnel")
                bounceWithBackoff(app, prefs)
            } finally {
                bouncing = false
            }
        }
    }

    /** Menyalakan tunnel yang mati padahal diniatkan UP (mis. proses lahir ulang). */
    private fun tryUpOnce(app: Context, prefs: Prefs) {
        if (!Prefs.of(app).wasUp) return // pengguna memutus di tengah jalan
        try {
            VelumTunnel.refreshState(app)
            if (VelumTunnel.state == Tunnel.State.UP) return
            if (VpnService.prepare(app) == null) {
                // Jaringan baru: endpoint terbaik bisa berubah (refresh() mengabaikan
                // hasil yang masih segar <1 jam, jadi murah di jalur cepat ini).
                EndpointProbe.refresh(prefs)
                VelumTunnel.up(app, prefs)
                // Segarkan penanda waktu SETELAH berhasil, bukan hanya saat menjadwalkan:
                // peristiwa jaringan susulan yang dipicu oleh kenaikan tunnel ini sendiri
                // harus tetap tertahan debounce.
                lastBounceMs = SystemClock.elapsedRealtime()
                Log.i(TAG, "sambung ulang latar berhasil")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sambung ulang latar gagal", e)
        }
    }

    private fun bounceWithBackoff(app: Context, prefs: Prefs) {
        runCatching { VelumTunnel.down(app) }
        for (delay in BACKOFF_MS) {
            try {
                TimeUnit.MILLISECONDS.sleep(delay)
            } catch (_: InterruptedException) {
                return
            }
            if (!Prefs.of(app).wasUp) return // pengguna memutus di tengah pantulan
            try {
                VelumTunnel.refreshState(app)
                if (VelumTunnel.state == Tunnel.State.UP) return
                EndpointProbe.refresh(prefs)
                VelumTunnel.up(app, prefs)
                VelumTunnel.refreshState(app)
                if (VelumTunnel.state == Tunnel.State.UP) {
                    // Sama seperti di `tryUpOnce`: keberhasilan pada percobaan ke-2/ke-3
                    // terjadi LEBIH dari 3 detik setelah jadwal, jadi tanpa penyegaran ini
                    // peristiwa jaringan susulan lolos debounce dan memicu pantulan baru
                    // pada tunnel yang justru baru saja sehat.
                    lastBounceMs = SystemClock.elapsedRealtime()
                    Log.i(TAG, "pantulan tunnel berhasil")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "pantulan tunnel gagal, coba lagi", e)
            }
        }
        Log.w(TAG, "pantulan tunnel menyerah setelah ${BACKOFF_MS.size} percobaan")
    }
}
