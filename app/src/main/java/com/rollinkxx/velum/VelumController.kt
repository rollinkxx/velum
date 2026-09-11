package com.rollinkxx.velum

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Orkestrasi koneksi & uji: memutuskan **apa** yang dilakukan, sementara
 * [MainActivity] hanya merender **bagaimana** hasilnya ditampilkan.
 *
 * Pemisahan ini membuat logika tidak ikut mati saat Activity dibuat ulang
 * (rotasi, proses lahir ulang) dan membuat keputusan penting — seperti kapan
 * hasil uji boleh dipercaya — bisa diuji lewat [VelumTestDecision].
 */
class VelumController(context: Context, private val ui: Ui) {

    /** Semua hal yang bisa diminta controller kepada UI. */
    interface Ui {
        fun setBusy(busy: Boolean)
        fun setStatusText(resId: Int)
        fun setMessageRes(resId: Int)
        fun setMessage(text: String)
        fun setTestTextRes(resId: Int)
        fun setTestText(text: String)
        fun render(state: Tunnel.State)
        fun onConnectedVisual()
        fun onDisconnectedVisual()
        fun refreshStaticInfo()
    }

    private val app = context.applicationContext
    private val prefs = Prefs.of(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    /** Terpisah dari [worker] agar uji yang lambat tidak menahan Sambungkan/Putuskan. */
    private val testWorker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    var busy = false
        private set

    /** Status tunnel terakhir yang diketahui (sumber: backend WireGuard). */
    val state: Tunnel.State get() = VelumTunnel.state

    private var prevState: Tunnel.State = Tunnel.State.DOWN
    private var testedSinceUp = false
    private var testJobId = 0
    private var pendingTest: Runnable? = null

    init {
        VelumTunnel.listener = { newState -> main.post { applyState(newState) } }
    }

    fun destroy() {
        VelumTunnel.listener = null
        cancelPendingTest()
        worker.shutdownNow()
        testWorker.shutdownNow()
    }

    // ---------- Status ----------

    /** Terapkan status: efek samping selalu jalan, teks status mengikuti UI. */
    private fun applyState(newState: Tunnel.State) {
        if (newState != prevState) {
            prevState = newState
            if (newState == Tunnel.State.UP) {
                ui.onConnectedVisual()
                if (!testedSinceUp && prefs.isRegistered) {
                    testedSinceUp = true
                    runTraceTest(fromButton = false)
                }
            } else {
                testedSinceUp = false
                cancelPendingTest()
                ui.onDisconnectedVisual()
            }
        }
        ui.render(newState)
    }

    /** Terapkan status yang diketahui saat ini ke UI (mis. setelah Activity hidup lagi). */
    fun applyCurrentState() = applyState(VelumTunnel.state)

    /** Sinkronkan status dengan backend di latar, lalu jalankan [onDone] di main thread. */
    fun refreshStateAsync(onDone: () -> Unit) {
        worker.execute {
            val s = runCatching { VelumTunnel.refreshState(app) }.getOrDefault(VelumTunnel.state)
            main.post {
                applyState(s)
                onDone()
            }
        }
    }

    /**
     * Pulihkan sesi bila proses lahir ulang: diniatkan UP tapi tunnel DOWN dan
     * persetujuan VPN masih berlaku → sambung otomatis; monitor selalu dipastikan
     * aktif selama diniatkan UP.
     */
    fun resumeIfNeeded() {
        if (!prefs.wasUp || !prefs.isRegistered) return
        ReconnectMonitor.ensure(app)
        if (!busy && VelumTunnel.state != Tunnel.State.UP && VpnService.prepare(app) == null) connect()
    }

    // ---------- Aksi ----------

    /** Intent persetujuan VPN bila belum diberikan; null bila sudah boleh menyambung. */
    fun vpnIntent(): Intent? = VpnService.prepare(app)

    fun connect() {
        setBusy(true)
        ui.setMessage("")
        worker.execute {
            try {
                if (prefs.isRegistered && !prefs.warpEnabled) {
                    // Akun era lama tanpa flag WARP: coba sembuhkan otomatis (fail-safe,
                    // kegagalan tidak boleh menghalangi penyambungan).
                    try {
                        VelumApi.ensureWarpEnabled(prefs)
                        main.post { ui.refreshStaticInfo() }
                    } catch (e: Exception) {
                        Log.w(TAG, "auto-heal akun gagal, lanjut tanpa heal", e)
                    }
                }
                if (!prefs.isRegistered) {
                    main.post { ui.setStatusText(R.string.status_registering) }
                    try {
                        VelumApi.register(prefs)
                    } catch (e: Exception) {
                        fail(R.string.err_register, e.message ?: e.javaClass.simpleName)
                        return@execute
                    }
                }
                main.post { ui.setStatusText(R.string.status_probing) }
                EndpointProbe.refresh(prefs)
                main.post {
                    ui.setStatusText(R.string.status_connecting)
                    ui.refreshStaticInfo()
                }
                VelumTunnel.up(app, prefs)
                prefs.wasUp = true // memo untuk sambung ulang saat boot
                ReconnectMonitor.ensure(app)
                main.post { setBusy(false); applyState(VelumTunnel.state) }
            } catch (e: Exception) {
                fail(R.string.err_connect, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun disconnect() {
        setBusy(true)
        ui.setStatusText(R.string.status_disconnecting)
        prefs.wasUp = false // putus manual: jangan sambung lagi saat boot
        ReconnectMonitor.stop(app)
        worker.execute {
            runCatching { VelumTunnel.down(app) }
            main.post { setBusy(false); applyState(VelumTunnel.state) }
        }
    }

    /** Hapus registrasi dan putuskan; UI bertanggung jawab meminta konfirmasi dulu. */
    fun reset() {
        if (busy) return
        setBusy(true)
        cancelPendingTest()
        prefs.wasUp = false // daftar ulang manual = putus permanen: jangan sambung saat boot
        ReconnectMonitor.stop(app)
        worker.execute {
            runCatching { VelumTunnel.down(app) }
            VelumApi.unregister(prefs)
            main.post {
                setBusy(false)
                applyState(Tunnel.State.DOWN)
                ui.refreshStaticInfo()
                ui.setTestTextRes(R.string.value_none)
                ui.setMessageRes(R.string.reset_done)
            }
        }
    }

    /** Uji koneksi yang dipicu pengguna (tombol Uji koneksi). */
    fun runTest() {
        if (busy) return
        runTraceTest(fromButton = true)
    }

    /** Membaca statistik trafik di latar, lalu menyerahkannya ke [onResult] di main thread. */
    fun runStats(onResult: (VelumTunnel.TrafficStats?) -> Unit) {
        worker.execute {
            val stats = VelumTunnel.traffic(app)
            main.post { onResult(stats) }
        }
    }

    // ---------- Uji trace ----------

    /**
     * Menjalankan uji trace; dipakai tombol Uji koneksi dan auto-uji saat tersambung.
     *
     * Uji SENGAJA tidak langsung menembak jaringan: `State.UP` dari backend hanya berarti
     * antarmuka TUN sudah dibuat, belum tentu handshake WireGuard-nya selesai. Permintaan
     * yang lewat sebelum handshake (atau memakai soket sisa sesi sebelum VPN aktif) keluar
     * bukan lewat WARP → `warp=off` → "Belum lewat Velum" palsu.
     */
    private fun runTraceTest(fromButton: Boolean, attempt: Int = 0) {
        cancelPendingTest(invalidate = false)
        val job = ++testJobId
        ui.setTestTextRes(R.string.test_running)
        if (fromButton) ui.setMessageRes(R.string.test_running)
        testWorker.execute {
            val ready = awaitHandshake(HANDSHAKE_WAIT_MS)
            var trace: VelumFormat.TraceInfo? = null
            var error: String? = null
            if (ready) {
                try {
                    trace = VelumApi.fetchTrace()
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            main.post { publishTestResult(job, trace, error, fromButton, attempt) }
        }
    }

    /**
     * Menunggu handshake WireGuard pertama (bukti tunnel benar-benar bisa dilewati) dengan
     * batas [maxWaitMs]; berhenti lebih awal bila tunnel turun. Blocking — latar saja.
     */
    private fun awaitHandshake(maxWaitMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if ((VelumTunnel.traffic(app)?.latestHandshakeMs ?: 0L) > 0L) return true
            try {
                Thread.sleep(HANDSHAKE_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return VelumTunnel.state == Tunnel.State.UP
    }

    /** Menampilkan hasil uji; hasil dari uji yang sudah usang/turun tidak pernah ditulis. */
    private fun publishTestResult(
        job: Int,
        trace: VelumFormat.TraceInfo?,
        error: String?,
        fromButton: Boolean,
        attempt: Int
    ) {
        if (job != testJobId) return // uji ini sudah dibatalkan/diganti uji baru
        val up = VelumTunnel.state == Tunnel.State.UP
        when (
            VelumTestDecision.decide(
                tunnelUp = up,
                trace = trace,
                error = error,
                attempt = attempt,
                maxAttempts = MAX_TEST_ATTEMPTS
            )
        ) {
            TestAction.RETRY -> {
                val retry = Runnable { runTraceTest(fromButton, attempt + 1) }
                pendingTest = retry
                main.postDelayed(retry, TEST_RETRY_MS)
            }
            TestAction.DROP -> {
                ui.setTestTextRes(R.string.value_none)
                if (fromButton) ui.setMessage("")
            }
            TestAction.PUBLISH -> {
                val time = VelumFormat.formatClock(System.currentTimeMillis())
                val active = trace != null && VelumFormat.isWarpActive(trace)
                ui.setTestText(
                    when {
                        active && trace != null ->
                            app.getString(R.string.test_on_dc, trace.colo.ifEmpty { "?" }, time)
                        trace != null -> app.getString(R.string.test_off_time, time)
                        else -> app.getString(R.string.test_failed)
                    }
                )
                if (fromButton) {
                    ui.setMessage(
                        when {
                            active -> app.getString(R.string.test_on)
                            trace != null -> app.getString(R.string.test_off)
                            else -> app.getString(R.string.err_network, error ?: "")
                        }
                    )
                }
            }
        }
    }

    /** Membatalkan uji tertunda; [invalidate] juga membatalkan hasil uji yang sedang jalan. */
    private fun cancelPendingTest(invalidate: Boolean = true) {
        if (invalidate) testJobId++
        pendingTest?.let { main.removeCallbacks(it) }
        pendingTest = null
    }

    private fun setBusy(value: Boolean) {
        busy = value
        ui.setBusy(value)
    }

    private fun fail(resId: Int, arg: String) {
        main.post {
            setBusy(false)
            applyState(VelumTunnel.state)
            ui.setMessage(app.getString(resId, arg))
        }
    }

    private companion object {
        const val TAG = "Velum"

        /** Batas menunggu handshake sebelum uji trace dijalankan. */
        const val HANDSHAKE_WAIT_MS = 6000L
        const val HANDSHAKE_POLL_MS = 250L
        /** Jeda ulangan bila hasil uji negatif padahal tunnel masih UP. */
        const val TEST_RETRY_MS = 1500L
        const val MAX_TEST_ATTEMPTS = 2
    }
}
