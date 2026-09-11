package com.rollinkxx.velum

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Satu layar: status + tombol Sambungkan/Putuskan + Uji koneksi + panel info interaktif
 * (durasi, endpoint, hasil uji terakhir, trafik data). Pekerjaan jaringan/tunnel berjalan di satu
 * thread latar tunggal (tanpa coroutine library) agar footprint tetap kecil; tiker durasi
 * & animasi denyut hanya hidup selama tunnel UP.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var statusDot: View
    private lateinit var messageView: TextView
    private lateinit var toggleButton: Button
    private lateinit var testButton: Button
    private lateinit var resetButton: Button
    private lateinit var vpnSettingsButton: Button
    private lateinit var infoDuration: TextView
    private lateinit var infoEndpoint: TextView
    private lateinit var infoTest: TextView
    private lateinit var infoData: TextView

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    /** Terpisah dari [worker] agar uji yang lambat tidak menahan Sambungkan/Putuskan. */
    private val testWorker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var busy = false

    /** Apakah UI sedang terlihat; tiker durasi/trafik hanya hidup bila true (hemat baterai). */
    private var resumed = false

    private var prevState: Tunnel.State = Tunnel.State.DOWN
    private var connectedSinceMs = 0L
    private var testedSinceUp = false
    private var pulse: ValueAnimator? = null
    private var tickCount = 0
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var staleTicks = 0
    private var staleWarned = false
    private var lastPollMs = 0L
    /** Polling pertama setelah UP belum punya dasar pembanding → jangan dihitung sebagai laju. */
    private var statsBaseline = false

    /** Penanda uji berjalan: hasil uji lama dibuang bila nilainya sudah berganti. */
    private var testJobId = 0
    private var pendingTest: Runnable? = null

    private val ticker = object : Runnable {
        override fun run() {
            infoDuration.text = VelumFormat.formatDuration(SystemClock.elapsedRealtime() - connectedSinceMs)
            tickCount++
            if (tickCount % 5 == 0) pollStats()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs.of(this) // satu instance per proses: membuka prefs terenkripsi itu mahal

        statusView = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        messageView = findViewById(R.id.message)
        toggleButton = findViewById(R.id.toggle)
        testButton = findViewById(R.id.test)
        resetButton = findViewById(R.id.reset)
        vpnSettingsButton = findViewById(R.id.vpnSettings)
        infoDuration = findViewById(R.id.infoDuration)
        infoEndpoint = findViewById(R.id.infoEndpoint)
        infoTest = findViewById(R.id.infoTest)
        infoData = findViewById(R.id.infoData)

        toggleButton.setOnClickListener { onToggle() }
        testButton.setOnClickListener { onTest() }
        resetButton.setOnClickListener { onReset() }
        vpnSettingsButton.setOnClickListener { onOpenVpnSettings() }

        refreshStaticInfo()
        requestNotificationPermissionIfNeeded()

        VelumTunnel.listener = { state -> main.post { render(state) } }
    }

    override fun onStart() {
        super.onStart()
        resumed = true
        render(VelumTunnel.state)
        // Pulihkan tiker & denyut bila tunnel masih UP dari sesi sebelumnya.
        startTicker()
        if (VelumTunnel.state == Tunnel.State.UP) startPulse()
        worker.execute {
            val s = runCatching { VelumTunnel.refreshState(this) }.getOrDefault(VelumTunnel.state)
            main.post {
                render(s)
                resumeIfNeeded()
            }
        }
    }

    /**
     * Tiker durasi + pemantau trafik dimatikan selama UI tak terlihat. Proses aplikasi
     * ditahan hidup oleh VpnService library selama tunnel UP, jadi tanpa ini tiker 1 Hz
     * akan terus membangunkan CPU di latar belakang. Durasi tetap benar saat tiker
     * dinyalakan lagi karena dihitung mundur dari [connectedSinceMs].
     */
    override fun onStop() {
        resumed = false
        stopTicker()
        stopPulse() // animasi per-frame tak perlu berjalan saat UI tak terlihat
        super.onStop()
    }

    /**
     * Memulihkan sesi bila proses lahir ulang: diniatkan UP tapi tunnel DOWN dan
     * persetujuan VPN masih berlaku → sambung otomatis; monitor selalu dipastikan
     * aktif selama diniatkan UP.
     */
    private fun resumeIfNeeded() {
        if (!prefs.wasUp || !prefs.isRegistered) return
        ReconnectMonitor.ensure(this)
        if (!busy && VelumTunnel.state != Tunnel.State.UP && VpnService.prepare(this) == null) {
            connect()
        }
    }

    override fun onDestroy() {
        VelumTunnel.listener = null
        cancelPendingTest()
        stopTicker()
        pulse?.cancel()
        worker.shutdownNow()
        testWorker.shutdownNow()
        super.onDestroy()
    }

    // ---------- Aksi ----------

    private fun onToggle() {
        if (busy) return
        if (VelumTunnel.state == Tunnel.State.UP) {
            disconnect()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VPN)
        } else {
            connect()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            if (resultCode == Activity.RESULT_OK) connect() else showMessage(getString(R.string.err_vpn_denied))
        }
    }

    private fun connect() {
        setBusy(true)
        showMessage("")
        worker.execute {
            try {
                if (prefs.isRegistered && !prefs.warpEnabled) {
                    // Akun era lama tanpa flag WARP: coba sembuhkan otomatis (fail-safe,
                    // kegagalan tidak boleh menghalangi penyambungan).
                    try {
                        VelumApi.ensureWarpEnabled(prefs)
                        main.post { refreshStaticInfo() }
                    } catch (e: Exception) {
                        Log.w(TAG, "auto-heal akun gagal, lanjut tanpa heal", e)
                    }
                }
                if (!prefs.isRegistered) {
                    main.post { statusView.setText(R.string.status_registering) }
                    try {
                        VelumApi.register(prefs)
                    } catch (e: Exception) {
                        fail(getString(R.string.err_register, e.message ?: e.javaClass.simpleName))
                        return@execute
                    }
                }
                main.post { statusView.setText(R.string.status_probing) }
                EndpointProbe.refresh(prefs)
                main.post {
                    statusView.setText(R.string.status_connecting)
                    refreshStaticInfo()
                }
                VelumTunnel.up(this, prefs)
                prefs.wasUp = true // memo untuk sambung ulang saat boot
                ReconnectMonitor.ensure(this)
                main.post { setBusy(false); render(VelumTunnel.state) }
            } catch (e: Exception) {
                fail(getString(R.string.err_connect, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun disconnect() {
        setBusy(true)
        statusView.setText(R.string.status_disconnecting)
        prefs.wasUp = false // putus manual: jangan sambung lagi saat boot
        ReconnectMonitor.stop(this)
        worker.execute {
            runCatching { VelumTunnel.down(this) }
            main.post { setBusy(false); render(VelumTunnel.state) }
        }
    }

    private fun onTest() {
        if (busy) return
        runTraceTest(fromButton = true)
    }

    /**
     * Menjalankan uji trace; dipakai tombol Uji koneksi dan auto-uji saat tersambung.
     *
     * Uji SENGAJA tidak langsung menembak jaringan: `State.UP` dari backend hanya berarti
     * antarmuka TUN sudah dibuat, belum tentu handshake WireGuard-nya selesai. Permintaan
     * yang lewat sebelum handshake (atau memakai soket sisa sesi sebelum VPN aktif) keluar
     * bukan lewat WARP → `warp=off` → "Belum lewat Velum" palsu. Karena itu: tunggu
     * handshake nyata dulu, dan ulangi sekali bila hasilnya negatif padahal tunnel UP.
     */
    private fun runTraceTest(fromButton: Boolean, attempt: Int = 0) {
        cancelPendingTest(invalidate = false)
        val job = ++testJobId
        infoTest.setText(R.string.test_running)
        if (fromButton) showMessage(getString(R.string.test_running))
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
     * batas [maxWaitMs]; berhenti lebih awal bila tunnel turun. Blocking — jalankan di latar.
     */
    private fun awaitHandshake(maxWaitMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (VelumTunnel.state != Tunnel.State.UP) return false
            if ((VelumTunnel.traffic(this)?.latestHandshakeMs ?: 0L) > 0L) return true
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
        val active = trace != null && VelumFormat.isWarpActive(trace)
        if (!active && error == null && up && attempt + 1 < MAX_TEST_ATTEMPTS) {
            // Bisa jadi permintaannya masih memakai soket dari sebelum tunnel aktif
            // (keep-alive sudah dimatikan, jadi ulangan ini pasti memakai soket baru).
            val retry = Runnable { runTraceTest(fromButton, attempt + 1) }
            pendingTest = retry
            main.postDelayed(retry, TEST_RETRY_MS)
            return
        }
        if (!up) {
            // Tunnel turun di tengah uji: jangan simpan hasil yang menyesatkan.
            infoTest.setText(R.string.value_none)
            if (fromButton) showMessage("")
            return
        }
        val time = VelumFormat.formatClock(System.currentTimeMillis())
        infoTest.text = when {
            active && trace != null -> getString(R.string.test_on_dc, trace.colo.ifEmpty { "?" }, time)
            trace != null -> getString(R.string.test_off_time, time)
            else -> getString(R.string.test_failed)
        }
        if (fromButton) {
            showMessage(
                when {
                    active -> getString(R.string.test_on)
                    trace != null -> getString(R.string.test_off)
                    else -> getString(R.string.err_network, error ?: "")
                }
            )
        }
    }

    /** Membatalkan uji tertunda; [invalidate] juga membatalkan hasil uji yang sedang jalan. */
    private fun cancelPendingTest(invalidate: Boolean = true) {
        if (invalidate) testJobId++
        pendingTest?.let { main.removeCallbacks(it) }
        pendingTest = null
    }

    /**
     * Android 13+: minta izin notifikasi hanya bila belum diberikan; versi lama
     * auto-granted. Menghindari permintaan berulang setiap kali layar dibuat.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    /** Membuka pengaturan VPN sistem (always-on & blokir tanpa VPN dikelola Android). */
    private fun onOpenVpnSettings() {
        try {
            startActivity(Intent("android.settings.VPN_SETTINGS"))
        } catch (e: Exception) {
            showMessage(getString(R.string.err_no_settings))
        }
    }

    private fun onReset() {
        if (busy) return
        setBusy(true)
        cancelPendingTest()
        prefs.wasUp = false // daftar ulang manual = putus permanen: jangan sambung saat boot
        ReconnectMonitor.stop(this)
        worker.execute {
            runCatching { VelumTunnel.down(this) }
            VelumApi.unregister(prefs)
            main.post {
                setBusy(false)
                render(Tunnel.State.DOWN)
                refreshStaticInfo()
                infoTest.setText(R.string.value_none)
                showMessage(getString(R.string.reset_done))
            }
        }
    }

    // ---------- Tampilan ----------

    private fun fail(text: String) {
        main.post {
            setBusy(false)
            render(VelumTunnel.state)
            showMessage(text)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        toggleButton.isEnabled = !value
        resetButton.isEnabled = !value
    }

    private fun showMessage(text: String) {
        messageView.text = text
    }

    private fun refreshStaticInfo() {
        infoEndpoint.text = prefs.effectiveEndpoint ?: getString(R.string.value_none)
    }

    private fun onConnectedVisual() {
        connectedSinceMs = SystemClock.elapsedRealtime()
        lastRxBytes = 0L
        lastTxBytes = 0L
        lastPollMs = SystemClock.elapsedRealtime()
        tickCount = 0
        statsBaseline = true // polling pertama hanya jadi dasar hitungan laju
        staleTicks = 0
        staleWarned = false
        infoData.setText(R.string.value_none)
        startTicker()
        startPulse()
        refreshStaticInfo()
        StatusNotifier.show(this, getString(R.string.notif_connected))
        if (!testedSinceUp && prefs.isRegistered) {
            testedSinceUp = true
            runTraceTest(fromButton = false)
        }
    }

    private fun onDisconnectedVisual() {
        testedSinceUp = false
        cancelPendingTest()
        stopTicker()
        stopPulse()
        StatusNotifier.hide(this)
        infoDuration.setText(R.string.value_none)
        infoData.setText(R.string.value_none)
    }

    /** Menjalankan tiker hanya bila UI terlihat dan tunnel UP; aman dipanggil berulang. */
    private fun startTicker() {
        if (!resumed || VelumTunnel.state != Tunnel.State.UP) return
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun stopTicker() {
        main.removeCallbacks(ticker)
    }

    private fun startPulse() {
        stopPulse()
        pulse = ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { statusDot.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        statusDot.alpha = 1f
    }

    private fun render(state: Tunnel.State) {
        // Efek samping (tiker, notifikasi, reset uji) selalu dijalankan walau sedang sibuk,
        // supaya tak ada status yang tertinggal bila tunnel berubah di tengah aksi.
        if (state != prevState) {
            prevState = state
            if (state == Tunnel.State.UP) onConnectedVisual() else onDisconnectedVisual()
        }
        if (busy) return // jangan timpa teks status sementara: "Menyambung…", "Memutus…", dst.
        when (state) {
            Tunnel.State.UP -> {
                statusView.setText(R.string.status_connected)
                statusView.setTextColor(getColor(R.color.ok))
                statusDot.setBackgroundResource(R.drawable.dot_ok)
                toggleButton.setText(R.string.btn_disconnect)
            }
            else -> {
                statusView.setText(R.string.status_disconnected)
                statusView.setTextColor(getColor(R.color.fg))
                statusDot.setBackgroundResource(R.drawable.dot_off)
                toggleButton.setText(R.string.btn_connect)
            }
        }
    }

    /** Menampilkan laju trafik (KB/s) tiap 5 detik selama UP; mendeteksi tunnel basi. */
    private fun pollStats() {
        worker.execute {
            val t = VelumTunnel.traffic(this)
            main.post {
                if (VelumTunnel.state != Tunnel.State.UP) return@post
                if (t == null) {
                    infoData.setText(R.string.value_none)
                    return@post
                }
                val nowMs = SystemClock.elapsedRealtime()
                if (statsBaseline) {
                    // Sampel pertama: hitungan laju belum bermakna (selisihnya bisa
                    // memakai statistik sisa sesi sebelumnya) → jadikan dasar saja.
                    statsBaseline = false
                    lastRxBytes = t.rxBytes
                    lastTxBytes = t.txBytes
                    lastPollMs = nowMs
                    infoData.setText(R.string.value_none)
                    return@post
                }
                val dtSec = ((nowMs - lastPollMs).coerceAtLeast(1)) / 1000.0
                val rxRate = ((t.rxBytes - lastRxBytes).coerceAtLeast(0) / dtSec).toLong()
                val txRate = ((t.txBytes - lastTxBytes).coerceAtLeast(0) / dtSec).toLong()
                lastPollMs = nowMs
                infoData.text = getString(R.string.data_format, VelumFormat.formatBytes(rxRate), VelumFormat.formatBytes(txRate))
                if (t.rxBytes != lastRxBytes || t.txBytes != lastTxBytes) {
                    lastRxBytes = t.rxBytes
                    lastTxBytes = t.txBytes
                    staleTicks = 0
                    staleWarned = false
                    return@post
                }
                staleTicks++
                val hsAge = System.currentTimeMillis() - t.latestHandshakeMs
                if (!staleWarned && staleTicks >= 6 &&
                    (t.latestHandshakeMs == 0L || hsAge > 180_000)
                ) {
                    staleWarned = true
                    showMessage(getString(R.string.stale_warn))
                }
            }
        }
    }


    private companion object {
        const val REQ_VPN = 1
        const val REQ_NOTIF = 2
        const val TAG = "Velum"

        /** Batas menunggu handshake sebelum uji trace dijalankan. */
        const val HANDSHAKE_WAIT_MS = 6000L
        const val HANDSHAKE_POLL_MS = 250L
        /** Jeda ulangan bila hasil uji negatif padahal tunnel masih UP. */
        const val TEST_RETRY_MS = 1500L
        const val MAX_TEST_ATTEMPTS = 2
    }
}
