package com.rollinkxx.velum

import android.os.SystemClock
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Memilih endpoint tunnel tercepat: mengukur RTT koneksi TCP:443 (proksi RTT ke
 * PoP anycast yang sama) secara paralel ke endpoint registrasi + kandidat anycast
 * Cloudflare yang dikenal, memakai pemenang selama 1 jam.
 * Best-effort total: kegagalan/keraguan apa pun mempertahankan endpoint lama.
 * Semua fungsi blocking: panggil dari thread latar.
 */
object EndpointProbe {
    private const val TAG = "Velum"
    private const val PROBE_PORT = 443
    private const val CONNECT_TIMEOUT_MS = 2000
    private const val TOTAL_TIMEOUT_SEC = 6L
    private const val FRESH_MS = 3600_000L
    private const val WG_PORT = 2408
    private const val KEEP_ALIVE_SEC = 30L

    /**
     * Ukuran pool MENGIKUTI jumlah kandidat, bukan angka tetap yang ditulis tangan.
     *
     * Sebelumnya `MAX_PROBE_THREADS = 8` sementara kandidat ada 7 (+1 endpoint registrasi)
     * — pas-pasan, dan tidak ada yang menegakkan hubungan itu. Menambah satu kandidat saja
     * membuat tugas kesembilan mengantre di `LinkedBlockingQueue`, tidak sempat berjalan
     * dalam anggaran 6 detik, lalu dibatalkan **diam-diam**: proba tampak berhasil padahal
     * sebagian kandidat tidak pernah diukur, dan endpoint "tercepat" dipilih dari data
     * yang tidak lengkap.
     */
    private val probeThreads = VelumUpstream.CANDIDATES.size + 1


    /**
     * Menyegarkan [Prefs.speedEndpoint] bila basi (>1 jam). Tidak pernah melempar;
     * kegagalan total mempertahankan nilai lama.
     */
    fun refresh(prefs: Prefs) {
        try {
            if (System.currentTimeMillis() - prefs.speedEndpointAt < FRESH_MS) return
            val ranked = measure(prefs.endpoint)
            if (ranked.isEmpty()) return // gagal total: jangan sentuh apa pun
            val best = ranked.first()
            val regHost = prefs.endpoint?.let(VelumFormat::hostPart)
            prefs.speedEndpoint =
                if (best == regHost || !VelumFormat.isIpLiteral(best)) null else "$best:$WG_PORT"
            prefs.speedEndpointAt = System.currentTimeMillis()
            Log.i(TAG, "endpoint tercepat: ${prefs.effectiveEndpoint} (${ranked.size} terukur)")
        } catch (e: Exception) {
            Log.w(TAG, "proba endpoint gagal, pakai endpoint lama", e)
        }
    }

    /**
     * Memilih kandidat **berbeda** dari [exclude] sebagai endpoint pengganti.
     *
     * `refresh()` tidak bisa dipakai untuk keperluan ini: ia memilih pemenang RTT, dan bila
     * pemenang itu justru endpoint yang sedang gagal handshake, hasilnya tidak berubah.
     * Di sini pemenang yang sama dengan endpoint sekarang sengaja dilewati.
     *
     * Blocking ≤ ~6 detik. Tidak pernah melempar.
     *
     * @return true bila ada pengganti terukur yang sudah dipasang di [Prefs].
     */
    fun rotate(prefs: Prefs, exclude: String?): Boolean = try {
        val current = exclude?.let(VelumFormat::hostPart)
        val ranked = measure(prefs.endpoint)
        val next = ranked.firstOrNull { it != current }
        if (next == null) {
            Log.w(TAG, "putar endpoint: tidak ada kandidat lain yang terukur")
            false
        } else {
            // Endpoint yang tadinya dianggap terbukti bekerja baru saja gagal handshake.
            prefs.workingEndpoint = null
            prefs.speedEndpoint = if (VelumFormat.isIpLiteral(next)) "$next:$WG_PORT" else null
            prefs.speedEndpointAt = System.currentTimeMillis()
            val installed = prefs.effectiveEndpoint?.let(VelumFormat::hostPart)
            if (installed == null || installed == current) {
                // Bisa terjadi bila `next` bukan literal IPv4: speedEndpoint menjadi null
                // sehingga effectiveEndpoint jatuh ke endpoint registrasi, yang mungkin
                // justru host yang barusan gagal. Mengembalikan true di sini berarti
                // mengklaim "endpoint sudah diganti" padahal tidak ada yang berubah, dan
                // uji ulang lalu mengulang kegagalan yang sama persis.
                Log.w(TAG, "putar endpoint: hasil ($installed) sama dengan yang gagal; tidak ada pengganti")
                false
            } else {
                Log.i(TAG, "endpoint diputar ke ${prefs.effectiveEndpoint} (${ranked.size} kandidat terukur)")
                true
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "putar endpoint gagal", e)
        false
    }

    /**
     * Pool bersama bert thread daemon (menganggur → mati sendiri) supaya tidak membuat
     * dan membuang sampai 8 thread setiap kali pengguna menekan Sambungkan.
     */
    private val pool: ExecutorService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // core == max supaya pengukuran benar-benar paralel; allowCoreThreadTimeOut
        // membuat thread menganggur mati sendiri setelah KEEP_ALIVE.
        ThreadPoolExecutor(
            probeThreads, probeThreads, KEEP_ALIVE_SEC, TimeUnit.SECONDS,
            LinkedBlockingQueue()
        ) { r -> Thread(r, "velum-probe").apply { isDaemon = true } }.apply {
            allowCoreThreadTimeOut(true)
        }
    }

    /** Host terurut dari tercepat; kosong bila semua gagal. Blocking ≤ ~6 detik. */
    private fun measure(registered: String?): List<String> {
        val hosts = LinkedHashSet<String>()
        registered?.let(VelumFormat::hostPart)?.takeIf { it.isNotEmpty() }?.let { hosts.add(it) }
        hosts.addAll(VelumUpstream.CANDIDATES)
        if (hosts.isEmpty()) return emptyList()
        val tasks = hosts.map { host -> Callable { host to tcpRttMs(host) } }
        return try {
            pool.invokeAll(tasks, TOTAL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .mapNotNull {
                    try {
                        if (it.isCancelled) null else it.get()
                    } catch (_: Exception) {
                        null
                    }
                }
                .filter { it.second >= 0 }
                .sortedBy { it.second }
                .map { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** RTTms koneksi TCP, atau -1 bila gagal. */
    private fun tcpRttMs(host: String): Long {
        val start = SystemClock.elapsedRealtime()
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(host, PROBE_PORT), CONNECT_TIMEOUT_MS) }
            SystemClock.elapsedRealtime() - start
        } catch (_: Exception) {
            -1
        }
    }

}
