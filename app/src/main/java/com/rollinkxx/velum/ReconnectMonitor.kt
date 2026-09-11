package com.rollinkxx.velum

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
    private val BACKOFF_MS = longArrayOf(2000, 5000, 10000)

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
            override fun onAvailable(network: Network) = scheduleBounce(app, "tersedia")
            override fun onLost(network: Network) = scheduleBounce(app, "hilang")
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
