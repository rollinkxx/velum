package com.rollinkxx.velum

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel

/**
 * Satu layar: merender status yang diputuskan [VelumController] dan mengurus hal yang
 * murni visual — tiker durasi, denyut titik status, notifikasi, dan laju trafik.
 *
 * Tidak ada lagi keputusan koneksi/uji di sini: semua ada di [VelumController] agar
 * tidak hilang saat Activity dibuat ulang dan bisa diuji secara terpisah.
 */
class MainActivity : AppCompatActivity(), VelumController.Ui {

    private lateinit var controller: VelumController
    private lateinit var statusView: TextView
    private lateinit var statusDot: View
    private lateinit var messageView: TextView
    private lateinit var toggleButton: Button
    private lateinit var testButton: Button
    private lateinit var resetButton: Button
    private lateinit var vpnSettingsButton: Button
    private lateinit var copyDiagButton: Button
    private lateinit var exclusionsButton: Button
    private lateinit var infoDuration: TextView
    private lateinit var infoEndpoint: TextView
    private lateinit var infoTest: TextView
    private lateinit var infoData: TextView

    private val main = Handler(Looper.getMainLooper())

    /** Persetujuan VPN sistem; hasilnya diteruskan ke controller. */
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.connect() else setMessageRes(R.string.err_vpn_denied)
    }

    /** Izin notifikasi Android 13+; hasilnya tidak kritis — aplikasi tetap jalan. */
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ditolak pun tidak apa-apa: hanya status bar yang hilang */ }

    /** Apakah UI sedang terlihat; tiker & denyut hanya hidup bila true (hemat baterai). */
    private var resumed = false
    private var connectedSinceMs = 0L
    private var pulse: ValueAnimator? = null
    private var tickCount = 0
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var staleTicks = 0
    private var staleWarned = false
    private var lastPollMs = 0L
    /** Polling pertama setelah UP belum punya dasar pembanding → jangan dihitung sebagai laju. */
    private var statsBaseline = false

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

        statusView = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        messageView = findViewById(R.id.message)
        toggleButton = findViewById(R.id.toggle)
        testButton = findViewById(R.id.test)
        resetButton = findViewById(R.id.reset)
        vpnSettingsButton = findViewById(R.id.vpnSettings)
        copyDiagButton = findViewById(R.id.copyDiag)
        exclusionsButton = findViewById(R.id.exclusions)
        infoDuration = findViewById(R.id.infoDuration)
        infoEndpoint = findViewById(R.id.infoEndpoint)
        infoTest = findViewById(R.id.infoTest)
        infoData = findViewById(R.id.infoData)

        toggleButton.setOnClickListener { onToggle() }
        testButton.setOnClickListener { controller.runTest() }
        resetButton.setOnClickListener { onReset() }
        vpnSettingsButton.setOnClickListener { onOpenVpnSettings() }
        copyDiagButton.setOnClickListener { copyDiagnostics() }
        exclusionsButton.setOnClickListener { onOpenExclusions() }

        controller = VelumController(this, this)

        refreshStaticInfo()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        resumed = true
        controller.applyCurrentState()
        // Pulihkan tiker & denyut bila tunnel masih UP dari sesi sebelumnya.
        startTicker()
        if (controller.state == Tunnel.State.UP) startPulse()
        controller.refreshStateAsync { controller.resumeIfNeeded() }
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

    override fun onDestroy() {
        controller.destroy()
        stopTicker()
        pulse?.cancel()
        super.onDestroy()
    }

    // ---------- Aksi yang masih milik UI ----------

    private fun onToggle() {
        if (controller.busy) return
        if (controller.state == Tunnel.State.UP) {
            controller.disconnect()
            return
        }
        val intent = controller.vpnIntent()
        if (intent != null) vpnLauncher.launch(intent) else controller.connect()
    }

    /** Daftar ulang menghapus registrasi: minta konfirmasi dulu. */
    private fun onReset() {
        if (controller.busy) return
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_body)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_reset) { _, _ -> controller.reset() }
            .show()
    }

    /**
     * Android 13+: minta izin notifikasi hanya bila belum diberikan; versi lama
     * auto-granted. Menghindari permintaan berulang setiap kali layar dibuat.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Membuka layar pemilihan aplikasi yang dikecualikan dari tunnel. */
    private fun onOpenExclusions() {
        startActivity(Intent(this, AppExclusionActivity::class.java))
    }

    /**
     * Salin ringkasan keadaan ke clipboard untuk dilampirkan ke laporan gangguan.
     * Isinya sengaja ramah privasi: tanpa kunci privat, identitas perangkat, atau
     * alamat IP (lihat `VelumDiagnostics`).
     */
    private fun copyDiagnostics() {
        controller.runStats { stats ->
            val up = controller.state == Tunnel.State.UP
            val snapshot = VelumDiagnostics.Snapshot(
                appVersion = appVersionName(),
                state = getString(if (up) R.string.status_connected else R.string.status_disconnected),
                endpoint = Prefs.of(this).effectiveEndpoint,
                handshakeAgeSec = stats?.latestHandshakeMs
                    ?.takeIf { it > 0L }
                    ?.let { (System.currentTimeMillis() - it) / 1000 },
                rxBytes = stats?.rxBytes ?: 0L,
                txBytes = stats?.txBytes ?: 0L,
                connectedSec = if (up) (SystemClock.elapsedRealtime() - connectedSinceMs) / 1000 else 0L,
                excludedApps = Prefs.of(this).excludedApps.toList()
            )
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("diagnostik velum", VelumDiagnostics.render(snapshot))
            )
            Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    /** Membuka pengaturan VPN sistem (always-on & blokir tanpa VPN dikelola Android). */
    private fun onOpenVpnSettings() {
        try {
            startActivity(Intent("android.settings.VPN_SETTINGS"))
        } catch (e: Exception) {
            messageView.setText(R.string.err_no_settings)
        }
    }

    // ---------- Implementasi VelumController.Ui ----------

    override fun setBusy(busy: Boolean) {
        toggleButton.isEnabled = !busy
        resetButton.isEnabled = !busy
    }

    override fun setStatusText(resId: Int) {
        statusView.setText(resId)
    }

    override fun setMessageRes(resId: Int) {
        messageView.setText(resId)
    }

    override fun setMessage(text: String) {
        messageView.text = text
    }

    override fun setTestTextRes(resId: Int) {
        infoTest.setText(resId)
    }

    override fun setTestText(text: String) {
        infoTest.text = text
    }

    override fun refreshStaticInfo() {
        infoEndpoint.text = Prefs.of(this).effectiveEndpoint ?: getString(R.string.value_none)
    }

    override fun onConnectedVisual() {
        connectedSinceMs = SystemClock.elapsedRealtime()
        lastRxBytes = 0L
        lastTxBytes = 0L
        lastPollMs = connectedSinceMs
        tickCount = 0
        statsBaseline = true // polling pertama hanya jadi dasar hitungan laju
        staleTicks = 0
        staleWarned = false
        infoData.setText(R.string.value_none)
        startTicker()
        startPulse()
        refreshStaticInfo()
        StatusNotifier.show(this, getString(R.string.notif_connected))
    }

    override fun onDisconnectedVisual() {
        stopTicker()
        stopPulse()
        StatusNotifier.hide(this)
        infoDuration.setText(R.string.value_none)
        infoData.setText(R.string.value_none)
    }

    override fun render(state: Tunnel.State) {
        // Efek samping sudah dijalankan controller; di sini hanya mengganti tampilan,
        // dan tidak menimpa teks status sementara ("Menyambung…", "Memutus…").
        if (controller.busy) return
        when (state) {
            Tunnel.State.UP -> {
                statusView.setText(R.string.status_connected)
                statusView.setTextColor(getColor(R.color.ok))
                statusDot.setBackgroundResource(R.drawable.dot_ok)
                statusDot.contentDescription = getString(R.string.cd_status_up)
                toggleButton.setText(R.string.btn_disconnect)
            }
            else -> {
                statusView.setText(R.string.status_disconnected)
                statusView.setTextColor(getColor(R.color.fg))
                statusDot.setBackgroundResource(R.drawable.dot_off)
                statusDot.contentDescription = getString(R.string.cd_status_down)
                toggleButton.setText(R.string.btn_connect)
            }
        }
    }

    // ---------- Tampilan ----------

    /** Menjalankan tiker hanya bila UI terlihat dan tunnel UP; aman dipanggil berulang. */
    private fun startTicker() {
        if (!resumed || controller.state != Tunnel.State.UP) return
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

    /** Menampilkan laju trafik (KB/s) tiap 5 detik selama UP; mendeteksi tunnel basi. */
    private fun pollStats() {
        controller.runStats { t ->
            if (controller.state != Tunnel.State.UP) return@runStats
            if (t == null) {
                infoData.setText(R.string.value_none)
                return@runStats
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
                return@runStats
            }
            val dtSec = ((nowMs - lastPollMs).coerceAtLeast(1)) / 1000.0
            val rxRate = ((t.rxBytes - lastRxBytes).coerceAtLeast(0) / dtSec).toLong()
            val txRate = ((t.txBytes - lastTxBytes).coerceAtLeast(0) / dtSec).toLong()
            lastPollMs = nowMs
            infoData.text = getString(
                R.string.data_format,
                VelumFormat.formatBytes(rxRate),
                VelumFormat.formatBytes(txRate)
            )
            if (t.rxBytes != lastRxBytes || t.txBytes != lastTxBytes) {
                lastRxBytes = t.rxBytes
                lastTxBytes = t.txBytes
                staleTicks = 0
                staleWarned = false
                return@runStats
            }
            staleTicks++
            val hsAge = System.currentTimeMillis() - t.latestHandshakeMs
            if (!staleWarned && staleTicks >= 6 &&
                (t.latestHandshakeMs == 0L || hsAge > 180_000)
            ) {
                staleWarned = true
                setMessage(getString(R.string.stale_warn))
            }
        }
    }
}
