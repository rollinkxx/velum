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
    private const val MAX_PROBE_THREADS = 8
    private const val KEEP_ALIVE_SEC = 30L

    /** Kandidat anycast Cloudflare yang dikenal melayani WARP (IPv4). */
    private val CANDIDATES = listOf(
        "162.159.192.1",
        "162.159.193.1",
        "162.159.195.1",
        "188.114.96.1",
        "188.114.97.1",
        "188.114.98.1",
        "188.114.99.1"
    )

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
     * Pool bersama bert thread daemon (menganggur → mati sendiri) supaya tidak membuat
     * dan membuang sampai 8 thread setiap kali pengguna menekan Sambungkan.
     */
    private val pool: ExecutorService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // core == max supaya pengukuran benar-benar paralel; allowCoreThreadTimeOut
        // membuat thread menganggur mati sendiri setelah KEEP_ALIVE.
        ThreadPoolExecutor(
            MAX_PROBE_THREADS, MAX_PROBE_THREADS, KEEP_ALIVE_SEC, TimeUnit.SECONDS,
            LinkedBlockingQueue()
        ) { r -> Thread(r, "velum-probe").apply { isDaemon = true } }.apply {
            allowCoreThreadTimeOut(true)
        }
    }

    /** Host terurut dari tercepat; kosong bila semua gagal. Blocking ≤ ~6 detik. */
    private fun measure(registered: String?): List<String> {
        val hosts = LinkedHashSet<String>()
        registered?.let(VelumFormat::hostPart)?.takeIf { it.isNotEmpty() }?.let { hosts.add(it) }
        hosts.addAll(CANDIDATES)
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
