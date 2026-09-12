package com.rollinkxx.velum

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orkestrasi koneksi & uji: memutuskan **apa** yang dilakukan, sementara
 * [MainActivity] hanya merender **bagaimana** hasilnya ditampilkan.
 *
 * **Batas umur kelas ini (jujur, sebelumnya dokumennya mengklaim sebaliknya):**
 * controller dibuat per-Activity dan dimatikan di `onDestroy`, jadi ia TIDAK bertahan
 * saat layar dibuat ulang. Yang membuatnya tidak merusak adalah keadaan koneksi tidak
 * lagi disimpan di sini:
 * - status & durasi tunnel hidup di [VelumTunnel] (umur proses),
 * - hasil uji terakhir hidup di [Prefs],
 * - [prevState] diawali dari status tunnel yang sebenarnya, sehingga layar yang baru
 *   tidak menganggap "sudah UP sejak tadi" sebagai transisi baru (tidak ada lagi
 *   durasi yang direset ke 00:00 dan auto-uji yang berjalan ulang tiap rotasi).
 *
 * **Aturan thread kelas ini:** tidak ada satu pun `ui.*` yang dipanggil langsung.
 * Semua lewat [onUi], yang menjalankan segera bila pemanggil sudah di main thread dan
 * mengantre bila tidak. Alasannya nyata, bukan gaya: jalur ulangan uji memanggil
 * `runTraceTest` dari `testWorker`, dan dulu baris itu menulis `TextView` dari thread
 * latar — kebetulan tidak crash hanya karena kedua view targetnya berukuran tetap,
 * bukan karena benar.
 */
class VelumController(context: Context, private val ui: Ui) {

    /** Semua hal yang bisa diminta controller kepada UI. */
    interface Ui {
        fun setBusy(busy: Boolean)
        fun setStatusText(resId: Int)
        fun setMessageRes(resId: Int)
        fun setMessage(text: String)
        fun setTestTextRes(resId: Int)
        fun showTest(result: VelumTestResult?)
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

    /**
     * Diawali dari status tunnel yang SEBENARNYA, bukan dari `DOWN` tetap.
     *
     * Bila layar dibuat ulang saat tunnel masih UP, nilai awal `DOWN` membuat
     * `applyCurrentState()` melihat transisi DOWN->UP yang tidak pernah terjadi:
     * durasi direset, auto-uji dijalankan ulang, dan notifikasi diposting lagi.
     */
    private var prevState: Tunnel.State = VelumTunnel.state

    private var testedSinceUp = false

    /** Atomik: dinaikkan dari main thread DAN dari `testWorker`, jadi tidak boleh `++` polos. */
    private val testJobId = AtomicInteger(0)

    @Volatile
    private var pendingTest: Runnable? = null

    /** Ada uji yang hasilnya belum pernah ditampilkan (dipakai membersihkan baris "Menunggu data…"). */
    @Volatile
    private var testInFlight = false

    /**
     * Menekan auto-uji selama pemutaran endpoint. Diset/lepas lewat [onUi] (lihat
     * [rotateEndpointAndReconnect]) supaya urutannya pasti terhadap applyState.
     */
    @Volatile
    private var testSuppressAuto = false

    /**
     * Generasi niat pengguna. Setiap aksi (Sambungkan/Putuskan/Daftar ulang) menaikkannya;
     * pekerjaan latar yang sudah usang melihat generasinya tidak cocok lalu berhenti
     * SEBELUM mengubah keadaan tunnel.
     *
     * Tanpa ini ada race nyata: `disconnect()` menulis `wasUp = false` lalu mengantre
     * `down()`, sementara `connect()` yang masih berjalan menulis `wasUp = true` setelah
     * `up()` selesai — hasilnya tunnel mati tetapi tercatat "niat UP", jadi BootReceiver
     * dan pemantau jaringan menyambungkannya lagi. Hanya dinaikkan dari main thread,
     * karena semua pemicu aksi berasal dari klik/kallback UI.
     */
    @Volatile
    private var intentGen = 0

    /** Controller sudah dimatikan: jangan sentuh UI, jangan jadwalkan ulangan baru. */
    @Volatile
    private var dead = false

    init {
        VelumTunnel.listener = { newState -> main.post { applyState(newState) } }
    }

    /**
     * Melepas semua kaitan. Dipanggil dari `MainActivity.onDestroy`.
     *
     * `shutdown()` dipakai, BUKAN `shutdownNow()`: menginterupsi thread yang sedang berada
     * di dalam `VelumTunnel.up()` berarti memotong pembangunan tunnel di tengah jalan —
     * lebih berbahaya daripada membiarkan operasi yang sudah dimulai selesai. Hasilnya
     * sekadar tidak dirender, karena [dead] sudah menutup jalur ke UI.
     */
    fun destroy() {
        dead = true
        VelumTunnel.listener = null
        cancelPendingTest()
        worker.shutdown()
        testWorker.shutdown()
    }

    /**
     * Satu-satunya jalur ke UI: jalankan segera bila sudah di main thread, antre bila tidak.
     * Membuat kesalahan "menyentuh view dari thread latar" tidak mungkin terulang,
     * apa pun thread pemanggilnya.
     */
    private fun onUi(block: () -> Unit) {
        if (dead) return
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { if (!dead) block() }
    }

    /** Naikkan generasi niat; hanya dipanggil dari main thread. */
    private fun nextIntent(): Int {
        intentGen++
        return intentGen
    }

    /**
     * Menyerahkan pekerjaan latar dengan aman.
     *
     * `ExecutorService.execute` melempar `RejectedExecutionException` setelah `shutdown()`,
     * dan itu terjadi di main thread → crash. Jalurnya nyata: rotasi layar saat
     * [refreshStateAsync] berjalan membuat `onDone` memanggil [connect] pada controller
     * yang baru saja dimatikan. Jadi penyerahan selalu diperiksa, dan sisa race antara
     * pemeriksaan dan penyerahan ditelan di sini — bukan dibiarkan jadi crash.
     */
    private fun submit(executor: ExecutorService, block: () -> Unit) {
        if (dead) return
        try {
            executor.execute(block)
        } catch (_: RejectedExecutionException) {
            Log.i(TAG, "pekerjaan dilewati: controller sudah dimatikan")
        }
    }

    /** Apakah pekerjaan dengan generasi [gen] sudah digantikan aksi pengguna yang lebih baru. */
    private fun stale(gen: Int) = dead || gen != intentGen

    // ---------- Status ----------

    /**
     * Terapkan status: efek samping selalu jalan, teks status mengikuti UI.
     * Selalu dipanggil dari main thread; [onUi] tetap dipakai agar aturan
     * "tidak ada `ui.*` langsung" berlaku seragam dan tidak bergantung pada ingatan.
     */
    private fun applyState(newState: Tunnel.State) {
        if (newState != prevState) {
            prevState = newState
            if (newState == Tunnel.State.UP) {
                onUi { ui.onConnectedVisual() }
                if (!testedSinceUp && !testSuppressAuto && prefs.isRegistered) {
                    testedSinceUp = true
                    runTraceTest(fromButton = false)
                }
            } else {
                testedSinceUp = false
                cancelPendingTest()
                onUi { ui.onDisconnectedVisual() }
            }
        }
        onUi { ui.render(newState) }
    }

    /** Terapkan status yang diketahui saat ini ke UI (mis. setelah Activity hidup lagi). */
    fun applyCurrentState() = applyState(VelumTunnel.state)

    /** Sinkronkan status dengan backend di latar, lalu jalankan [onDone] di main thread. */
    fun refreshStateAsync(onDone: () -> Unit) {
        submit(worker) {
            val s = runCatching { VelumTunnel.refreshState(app) }.getOrDefault(VelumTunnel.state)
            main.post {
                if (dead) return@post // layar sudah ditutup: jangan sentuh UI, jangan lanjut
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
        val gen = nextIntent()
        setBusy(true)
        onUi { ui.setMessage("") }
        submit(worker) {
            try {
                if (stale(gen)) return@submit
                if (prefs.isRegistered && !prefs.warpEnabled) {
                    // Akun era lama tanpa flag WARP: coba sembuhkan otomatis (fail-safe,
                    // kegagalan tidak boleh menghalangi penyambungan).
                    try {
                        VelumApi.ensureWarpEnabled(prefs)
                        onUi { ui.refreshStaticInfo() }
                    } catch (e: Exception) {
                        Log.w(TAG, "auto-heal akun gagal, lanjut tanpa heal", e)
                    }
                }
                if (stale(gen)) return@submit
                if (!prefs.isRegistered) {
                    onUi { ui.setStatusText(R.string.status_registering) }
                    try {
                        registerWithRetry()
                    } catch (e: Exception) {
                        fail(R.string.err_register, e)
                        return@submit
                    }
                }
                if (stale(gen)) return@submit
                onUi { ui.setStatusText(R.string.status_probing) }
                EndpointProbe.refresh(prefs)
                if (stale(gen)) return@submit
                onUi {
                    ui.setStatusText(R.string.status_connecting)
                    ui.refreshStaticInfo()
                }
                VelumTunnel.up(app, prefs)
                // Niat dibaca ulang SETELAH up(): bila pengguna menekan Putuskan selama
                // penyambungan, jangan menimpa niatnya dan jangan hidupkan pemantau.
                if (stale(gen)) return@submit
                prefs.wasUp = true // memo untuk sambung ulang saat boot
                ReconnectMonitor.ensure(app)
                onUi { setBusy(false); applyState(VelumTunnel.state) }
            } catch (e: Exception) {
                fail(R.string.err_connect, e)
            }
        }
    }

    /**
     * Registrasi dengan satu kali ulangan berjeda, khusus untuk kegagalan jaringan.
     * Jaringan yang baru saja bangun (habis boot, baru ganti Wi-Fi/data) sering gagal
     * pada percobaan pertama; penolakan dari server tidak pernah diulang karena
     * mengulang permintaan ke klien yang ditolak tidak ada gunanya.
     */
    private fun registerWithRetry() {
        try {
            VelumApi.register(prefs)
            return
        } catch (first: Exception) {
            if (VelumError.kindOf(first) != VelumError.Kind.NETWORK) throw first
            Log.i(TAG, "registrasi gagal, mengulang sekali setelah jeda", first)
        }
        try {
            Thread.sleep(REGISTER_RETRY_MS)
        } catch (_: InterruptedException) {
            throw IOException("registrasi dibatalkan")
        }
        VelumApi.register(prefs)
    }

    fun disconnect() {
        nextIntent()
        setBusy(true)
        onUi { ui.setStatusText(R.string.status_disconnecting) }
        prefs.wasUp = false // putus manual: jangan sambung lagi saat boot
        ReconnectMonitor.stop(app)
        submit(worker) {
            runCatching { VelumTunnel.down(app) }
            onUi { setBusy(false); applyState(VelumTunnel.state) }
        }
    }

    /** Hapus registrasi dan putuskan; UI bertanggung jawab meminta konfirmasi dulu. */
    fun reset() {
        if (busy) return
        nextIntent()
        setBusy(true)
        cancelPendingTest()
        prefs.wasUp = false // daftar ulang manual = putus permanen: jangan sambung saat boot
        ReconnectMonitor.stop(app)
        submit(worker) {
            runCatching { VelumTunnel.down(app) }
            VelumApi.unregister(prefs)
            onUi {
                setBusy(false)
                applyState(Tunnel.State.DOWN)
                ui.refreshStaticInfo()
                ui.showTest(prefs.lastTest) // registrasi dihapus → hasil uji lama ikut hilang
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
        submit(worker) {
            val stats = VelumTunnel.traffic(app)
            main.post { if (!dead) onResult(stats) }
        }
    }

    // ---------- Uji trace ----------

    /**
     * Menjalankan uji trace; dipakai tombol Uji koneksi dan auto-uji saat tersambung.
     *
     * Uji SENGAJA tidak langsung menembak jaringan: `State.UP` dari backend hanya berarti
     * antarmuka TUN sudah dibuat, belum tentu handshake WireGuard-nya selesai. Permintaan
     * yang keluar sebelum handshake (atau memakai soket sisa sesi sebelum VPN aktif) tidak
     * lewat WARP — dulu hasilnya "Belum lewat Velum" palsu, sekarang juga diketahui muncul
     * sebagai galat DNS menyesatkan ("Unable to resolve host ...") yang membuat pengguna
     * menyalahkan jaringannya sendiri.
     *
     * **Bisa dipanggil dari main thread ATAU dari `testWorker`** (jalur ulangan). Karena
     * itu setiap sentuhan UI di sini wajib lewat [onUi].
     */
    private fun runTraceTest(fromButton: Boolean, attempt: Int = 0) {
        cancelPendingTest(invalidate = false)
        val job = testJobId.incrementAndGet()
        testInFlight = true
        onUi { ui.setTestTextRes(R.string.test_waiting) }
        if (fromButton) onUi { ui.setMessageRes(R.string.test_waiting) }
        submit(testWorker) {
            val waitMs = if (attempt == 0) HANDSHAKE_WAIT_MS else HANDSHAKE_WAIT_RETRY_MS
            val ready = awaitHandshake(waitMs)
            val tunnelUp = VelumTunnel.state == Tunnel.State.UP
            // Handshake = bukti pertama ada data yang benar-benar lewat; hanya sejak itu
            // endpoint ini layak dicatat sebagai "terbukti bekerja".
            if (ready) rememberWorkingEndpoint()
            var trace: VelumFormat.TraceInfo? = null
            var error: String? = null
            if (tunnelUp && ready) {
                onUi { ui.setTestTextRes(R.string.test_running) }
                try {
                    trace = VelumApi.fetchTrace()
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            main.post { publishTestResult(job, tunnelUp, ready, trace, error, fromButton, attempt) }
        }
    }

    /**
     * Menunggu handshake WireGuard pertama (bukti tunnel benar-benar bisa dilewati).
     * Blocking — latar saja.
     *
     * Sengaja TIDAK mengembalikan "siap" hanya karena antarmuka UP: itu sumber kegagalan
     * senyap yang sudah terbukti di lapangan (lihat dokumen kelas ini).
     */
    private fun awaitHandshake(maxWaitMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (dead) return false
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if ((VelumTunnel.traffic(app)?.latestHandshakeMs ?: 0L) > 0L) return true
            try {
                Thread.sleep(HANDSHAKE_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }

    /** Mencatat endpoint yang terbukti menghasilkan handshake (bukti > perkiraan RTT). */
    private fun rememberWorkingEndpoint() {
        val current = prefs.effectiveEndpoint ?: return
        if (prefs.workingEndpoint != current) {
            prefs.workingEndpoint = current
            Log.i(TAG, "endpoint terbukti bekerja: $current")
        }
    }

    /** Menampilkan hasil uji; hasil dari uji yang sudah usang/turun tidak pernah ditulis. */
    private fun publishTestResult(
        job: Int,
        tunnelUp: Boolean,
        handshakeReady: Boolean,
        trace: VelumFormat.TraceInfo?,
        error: String?,
        fromButton: Boolean,
        attempt: Int
    ) {
        if (job != testJobId.get()) return // uji ini sudah dibatalkan/diganti uji baru
        testInFlight = false
        when (
            VelumTestDecision.decide(
                tunnelUp = tunnelUp,
                handshakeReady = handshakeReady,
                trace = trace,
                error = error,
                attempt = attempt,
                maxAttempts = MAX_TEST_ATTEMPTS
            )
        ) {
            TestAction.RETRY -> {
                val rotate = !handshakeReady
                val retry = Runnable {
                    if (dead) return@Runnable
                    submit(testWorker) {
                        if (dead) return@submit
                        // Tanpa handshake, mengulang saja tidak menolong: endpoint lain
                        // dicoba lebih dulu. Ini penambal nyata untuk jaringan yang
                        // memblokir endpoint WARP tertentu.
                        if (rotate) rotateEndpointAndReconnect()
                        runTraceTest(fromButton, attempt + 1)
                    }
                }
                pendingTest = retry
                main.postDelayed(retry, TEST_RETRY_MS)
            }
            // Tunnel turun di tengah uji: hasil dibuang, baris uji dikembalikan ke hasil sah
            // terakhir, dan pesan sementara dibersihkan agar tidak tertinggal.
            TestAction.DROP -> {
                onUi {
                    ui.showTest(prefs.lastTest)
                    if (fromButton) ui.setMessage("")
                }
            }
            TestAction.PUBLISH, TestAction.PUBLISH_NO_DATA -> {
                val result = VelumTestResult.of(
                    handshakeReady = handshakeReady,
                    trace = trace,
                    error = error,
                    atEpochMs = System.currentTimeMillis()
                )
                prefs.lastTest = result
                onUi {
                    ui.showTest(result)
                    // "Belum ada data" juga diberitahukan saat uji otomatis: pengguna melihat
                    // status "Tersambung" tetapi tidak ada yang berjalan, dan tanpa penjelasan
                    // keadaan itu tampak seperti kegagalan yang tidak bisa ditindaklanjuti.
                    if (fromButton || result.kind == VelumTestResult.Kind.NO_DATA) {
                        ui.setMessage(messageFor(result))
                    }
                }
            }
        }
    }

    /** Pesan rincian untuk tombol Uji koneksi; baris "Uji terakhir" memakai data yang sama. */
    private fun messageFor(result: VelumTestResult): String = when (result.kind) {
        VelumTestResult.Kind.ACTIVE -> app.getString(R.string.test_on)
        VelumTestResult.Kind.OFF -> app.getString(R.string.test_off)
        VelumTestResult.Kind.NO_DATA -> app.getString(R.string.test_no_data_hint)
        VelumTestResult.Kind.FAILED ->
            app.getString(R.string.err_network, result.detail ?: "?")
    }

    /**
     * Memutar endpoint lalu menyambung ulang: penambal untuk jaringan yang tidak
     * meneruskan endpoint WARP tertentu (antarmuka UP, handshake tidak pernah terjadi).
     *
     * Berjalan di `testWorker`. Down+up dilakukan lewat [VelumTunnel.restart] supaya
     * atomik terhadap pelaku lain (layar utama, ubin, pemantau jaringan) — pasangan yang
     * dipanggil terpisah bisa disela `down` milik pengguna dan berakhir menghidupkan
     * tunnel yang baru saja diminta mati.
     *
     * Auto-uji ditekan SEBELUM operasi ini berjalan dan dilepas sesudahnya, lewat [onUi]
     * yang mengantre ke main thread secara FIFO: `testSuppressAuto = true` pasti
     * diproses sebelum applyState(DOWN/UP), dan `= false` pasti sesudahnya. Tanpa itu,
     * uji akan berjalan dua kali — sekali dari sini, sekali lagi dari perubahan status.
     */
    private fun rotateEndpointAndReconnect() {
        if (!prefs.wasUp || !prefs.isRegistered) return // pengguna memutus di tengah jalan
        val current = prefs.effectiveEndpoint
        onUi {
            testSuppressAuto = true
            ui.setTestTextRes(R.string.test_searching)
        }
        try {
            if (!EndpointProbe.rotate(prefs, current)) {
                Log.w(TAG, "tidak ada endpoint pengganti; uji dilanjutkan dengan endpoint lama")
                return
            }
            VelumTunnel.restart(app, prefs)
            onUi { ui.refreshStaticInfo() }
        } catch (e: Exception) {
            Log.w(TAG, "putar endpoint & sambung ulang gagal", e)
        } finally {
            onUi { testSuppressAuto = false }
        }
    }

    /**
     * Membatalkan uji tertunda; [invalidate] juga membatalkan hasil uji yang sedang jalan.
     *
     * Bila ada uji yang batal di tengah jalan, baris "Uji terakhir" dikembalikan ke hasil
     * sah terakhir — sebelumnya ia bisa tertinggal selamanya di "Menunggu data…" karena
     * hasil yang dibatalkan tidak pernah ditampilkan (keluhan nyata di perangkat).
     */
    private fun cancelPendingTest(invalidate: Boolean = true) {
        if (invalidate) testJobId.incrementAndGet()
        pendingTest?.let { main.removeCallbacks(it) }
        pendingTest = null
        if (invalidate && testInFlight) {
            testInFlight = false
            onUi { ui.showTest(prefs.lastTest) }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        onUi { ui.setBusy(value) }
    }

    /** Tampilkan kegagalan dengan pesan yang sesuai jenisnya (bukan sekadar teks exception). */
    private fun fail(resId: Int, e: Exception) {
        onUi {
            setBusy(false)
            applyState(VelumTunnel.state)
            ui.setMessage(messageFor(resId, e))
        }
    }

    private fun messageFor(resId: Int, e: Exception): String {
        val detail = e.message ?: e.javaClass.simpleName
        return when (VelumError.kindOf(e)) {
            VelumError.Kind.NETWORK -> app.getString(R.string.err_network, detail)
            VelumError.Kind.SERVER_REJECT -> app.getString(
                R.string.err_server_reject,
                (e as? VelumApi.HttpError)?.code?.toString() ?: detail
            )
            VelumError.Kind.SERVICE_BLOCKED -> app.getString(R.string.err_connect_closed)
            VelumError.Kind.UNKNOWN -> app.getString(resId, detail)
        }
    }

    private companion object {
        const val TAG = "Velum"

        /** Batas menunggu handshake sebelum uji trace dijalankan. */
        const val HANDSHAKE_WAIT_MS = 8000L
        /**
         * Percobaan kedua menunggu lebih lama: tunnel baru saja dibangun ulang dengan
         * endpoint yang berbeda, jadi wajar bila handshake-nya butuh beberapa detik lagi.
         */
        const val HANDSHAKE_WAIT_RETRY_MS = 10000L
        const val HANDSHAKE_POLL_MS = 250L
        /** Jeda ulangan bila hasil uji negatif padahal tunnel masih UP. */
        const val TEST_RETRY_MS = 1500L
        const val MAX_TEST_ATTEMPTS = 2
        /** Jeda sebelum registrasi diulang satu kali. */
        const val REGISTER_RETRY_MS = 1500L
    }
}
