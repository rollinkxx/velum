package com.rollinkxx.velum

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
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
    // Baris aksi pada kartu: wadah LinearLayout yang bisa ditekan, bukan Button,
    // supaya ikon + judul + subjudul bisa disusun bebas.
    private lateinit var resetRow: View
    private lateinit var vpnSettingsRow: View
    private lateinit var copyDiagRow: View
    private lateinit var exclusionsRow: View
    private lateinit var infoDuration: TextView
    private lateinit var infoEndpoint: TextView
    private lateinit var infoTest: TextView
    private lateinit var infoData: TextView
    private lateinit var vpnSettingsSub: TextView

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
    private var visible = false
    private var pulse: ValueAnimator? = null
    /** Pelacak laju trafik (jendela geser 5 dtk); satu per sesi tunnel. */
    private val rate = VelumRate.Tracker()
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    /** Kapan terakhir penghitung trafik berubah (elapsedRealtime); untuk deteksi basi. */
    private var lastTrafficMs = 0L
    private var staleWarned = false

    private val ticker = object : Runnable {
        override fun run() {
            infoDuration.text = VelumFormat.formatDuration(connectedMs())
            pollStats()
            main.postDelayed(this, 1000)
        }
    }

    /**
     * Durasi koneksi, dibaca dari [VelumTunnel] — bukan dari jam milik Activity.
     *
     * Sebelumnya layar menyimpan `connectedSinceMs` sendiri dan mengisinya ulang setiap
     * kali status diterapkan, sehingga rotasi layar (yang membuat ulang Activity)
     * mengembalikan durasi ke `00:00` padahal koneksi tidak pernah putus.
     */
    private fun connectedMs(): Long {
        val since = VelumTunnel.upSinceElapsedMs
        return if (since <= 0L) 0L else SystemClock.elapsedRealtime() - since
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Wajib sejak targetSdk 36: jendela menggambar sampai ke tepi layar.
        VelumInsets.applySystemBars(findViewById(R.id.root))

        statusView = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        messageView = findViewById(R.id.message)
        toggleButton = findViewById(R.id.toggle)
        testButton = findViewById(R.id.test)
        resetRow = findViewById(R.id.reset)
        vpnSettingsRow = findViewById(R.id.vpnSettings)
        copyDiagRow = findViewById(R.id.copyDiag)
        exclusionsRow = findViewById(R.id.exclusions)
        infoDuration = findViewById(R.id.infoDuration)
        infoEndpoint = findViewById(R.id.infoEndpoint)
        infoTest = findViewById(R.id.infoTest)
        infoData = findViewById(R.id.infoData)
        vpnSettingsSub = findViewById(R.id.subVpnSettings)

        toggleButton.setOnClickListener { onToggle() }
        testButton.setOnClickListener { controller.runTest() }
        resetRow.setOnClickListener { onReset() }
        vpnSettingsRow.setOnClickListener { onOpenVpnSettings() }
        copyDiagRow.setOnClickListener { copyDiagnostics() }
        exclusionsRow.setOnClickListener { onOpenExclusions() }

        controller = VelumController(this, this)

        refreshStaticInfo()
        requestNotificationPermissionIfNeeded()
        polishAppTitle()
    }

    /**
     * Membuat judul aplikasi tampil elegan: gradien gading→emas dengan pendar hangat.
     *
     * Gradien butuh lebar yang sudah terukur, jadi baru diterapkan pada `onPreDraw`
     * pertama dan pendengarnya langsung dilepas. Layer software dipilih supaya
     * pendar dan gradien tampil identik di semua perangkat — aman karena ini satu
     * TextView statis yang tidak pernah diubah isinya.
     */
    private fun polishAppTitle() {
        val title = findViewById<TextView>(R.id.appTitle)
        title.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        title.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                title.viewTreeObserver.removeOnPreDrawListener(this)
                val width = if (title.width > 0) {
                    title.width.toFloat()
                } else {
                    title.paint.measureText(title.text.toString())
                }
                title.paint.shader = LinearGradient(
                    0f, 0f, width, 0f,
                    resources.getColor(R.color.title_start, theme),
                    resources.getColor(R.color.title_end, theme),
                    Shader.TileMode.CLAMP
                )
                title.paint.setShadowLayer(
                    12f, 0f, 2f, resources.getColor(R.color.title_glow, theme)
                )
                title.invalidate()
                return true
            }
        })
    }

    override fun onStart() {
        super.onStart()
        visible = true
        controller.applyCurrentState()
        // Pulihkan tiker & denyut bila tunnel masih UP dari sesi sebelumnya.
        startTicker()
        if (controller.state == Tunnel.State.UP) {
            startPulse()
            // Layar ini bisa jadi hasil rotasi atau kembali dari latar, dan pada kedua
            // kasus itu `onConnectedVisual()` TIDAK dipanggil (tidak ada transisi).
            resetTrafficBaseline()
        }
        refreshAlwaysOn()
        controller.refreshStateAsync { controller.resumeIfNeeded() }
    }

    /**
     * Tiker durasi + pemantau trafik dimatikan selama UI tak terlihat. Proses aplikasi
     * ditahan hidup oleh VpnService library selama tunnel UP, jadi tanpa ini tiker 1 Hz
     * akan terus membangunkan CPU di latar belakang. Durasi tetap benar saat tiker
     * dinyalakan lagi karena dihitung dari [VelumTunnel.upSinceElapsedMs], bukan dari
     * jam milik Activity.
     */
    override fun onStop() {
        visible = false
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
                connectedSec = if (up) connectedMs() / 1000 else 0L,
                excludedApps = Prefs.of(this).excludedApps.toList(),
                // Ikut disertakan: tanpa ini laporan gangguan dari perangkat hanya memuat
                // keadaan saat itu, bukan alasan uji terakhir gagal.
                lastTest = renderTest(Prefs.of(this).lastTest),
                // Kejujuran keamanan: bila keystore perangkat gagal dan penyimpanan jatuh
                // ke berkas polos, pengguna dan penerima laporan berhak tahu — selama ini
                // keadaan itu hanya tercatat di logcat yang tidak dibaca siapa pun.
                plaintextFallback = Prefs.of(this).isPlainFallback,
                // Keadaan internal — ditambahkan 2026-09-13 atas persetujuan maintainer
                // karena pengujian dilakukan di perangkat TANPA adb. Baris-baris ini yang
                // mengubah uji konkurensi dan daya tahan proses dari "tidak bisa diperiksa"
                // menjadi "cukup dilihat". `Process.getStartElapsedRealtime()` ada sejak
                // API 24 (minSdk repo ini 24), jadi tanpa guard versi.
                wasUp = Prefs.of(this).wasUp,
                intentGen = VelumTunnel.currentIntent,
                monitorActive = ReconnectMonitor.isActive,
                processAgeSec = (SystemClock.elapsedRealtime() -
                    android.os.Process.getStartElapsedRealtime()) / 1000,
                boot = VelumDiagnostics.decodeBoot(Prefs.of(this).bootRecord),
                nowEpochMs = System.currentTimeMillis()
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

    /**
     * Menampilkan keadaan "Selalu aktif" sistem pada subjudul baris aksi.
     *
     * Pembacaan hanya berhasil saat tunnel UP (VpnService hidup di proses ini) dan
     * Android 29+; selain itu subjudul kembali ke teks netral "Pengaturan VPN sistem"
     * — kita tidak menebak keadaan yang tidak bisa dibaca (§11).
     */
    private fun refreshAlwaysOn() {
        if (controller.state != Tunnel.State.UP) {
            vpnSettingsSub.setText(R.string.sub_vpn_settings)
            return
        }
        controller.runAlwaysOnState { s ->
            vpnSettingsSub.setText(
                when {
                    s == null -> R.string.sub_vpn_settings
                    s.alwaysOn && s.lockdown -> R.string.sub_vpn_settings_on_lockdown
                    s.alwaysOn -> R.string.sub_vpn_settings_on
                    else -> R.string.sub_vpn_settings_off
                }
            )
        }
    }

    // ---------- Implementasi VelumController.Ui ----------

    override fun setBusy(busy: Boolean) {
        toggleButton.isEnabled = !busy
        resetRow.isEnabled = !busy
        // LinearLayout tak punya status visual seperti Button, jadi keadaan
        // nonaktif ditandai dengan peredupan agar tetap terlihat jelas.
        resetRow.alpha = if (busy) 0.45f else 1f
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

    /**
     * Teks sementara selama uji berjalan ("Menunggu data…", "Menguji…", "Mencari endpoint…").
     * Hasil akhirnya selalu lewat [showTest] supaya hanya ada satu jalur penerjemahan.
     */
    override fun setTestTextRes(resId: Int) {
        infoTest.setText(resId)
    }

    /** Menampilkan hasil uji terakhir; `null` berarti belum pernah diuji. */
    override fun showTest(result: VelumTestResult?) {
        infoTest.text = renderTest(result)
    }

    /**
     * Satu-satunya tempat [VelumTestResult] menjadi teks. Dipakai saat uji selesai **dan**
     * saat layar dibuat ulang, sehingga hasil terakhir tetap terlihat setelah restart —
     * sebelumnya baris ini kembali kosong karena hanya diisi peristiwa.
     */
    private fun renderTest(result: VelumTestResult?): String {
        if (result == null) return getString(R.string.value_none)
        val time = VelumFormat.formatClock(result.atEpochMs)
        return when (result.kind) {
            VelumTestResult.Kind.ACTIVE -> getString(R.string.test_on_dc, result.colo ?: "?", time)
            VelumTestResult.Kind.OFF -> getString(R.string.test_off_time, time)
            VelumTestResult.Kind.NO_DATA -> getString(R.string.test_no_data_time, time)
            VelumTestResult.Kind.FAILED -> getString(R.string.test_failed)
        }
    }

    override fun refreshStaticInfo() {
        val prefs = Prefs.of(this)
        infoEndpoint.text = prefs.effectiveEndpoint ?: getString(R.string.value_none)
        infoTest.text = renderTest(prefs.lastTest)
    }

    override fun onConnectedVisual() {
        resetTrafficBaseline()
        startTicker()
        startPulse()
        refreshStaticInfo()
        refreshAlwaysOn()
        // Notifikasi status sengaja TIDAK diposting dari sini. `VelumTunnel.onStateChange`
        // yang melakukannya, supaya tunnel yang tersambung lewat ubin pengaturan cepat
        // atau receiver boot (tanpa Activity sama sekali) tetap punya notifikasi, dan
        // tunnel yang mati di latar tetap dibersihkan — sebelumnya notifikasi "Tersambung"
        // bisa menetap selamanya karena satu-satunya pemanggil `hide()` ada di layar ini.
    }

    /**
     * Mengosongkan pelacak laju sehingga sesi baru (atau layar yang kembali terlihat
     * dengan tunnel UP) memulai pengukuran dari nol. Sampel pertama hanya menjadi dasar
     * hitungan; laju pertama muncul pada sampel kedua (±1 detik dengan polling 1 Hz) dan
     * total sesi muncul seketika karena dibaca langsung dari penghitung kumulatif backend.
     */
    private fun resetTrafficBaseline() {
        rate.reset()
        lastRxBytes = -1L
        lastTxBytes = -1L
        lastTrafficMs = SystemClock.elapsedRealtime()
        staleWarned = false
        infoData.setText(R.string.value_none)
    }

    override fun onDisconnectedVisual() {
        stopTicker()
        stopPulse()
        // `StatusNotifier.hide` dipanggil oleh VelumTunnel saat status berubah, bukan di
        // sini: layar bisa saja sudah tidak ada ketika tunnel mati di latar.
        infoDuration.setText(R.string.value_none)
        infoData.setText(R.string.value_none)
        refreshAlwaysOn()
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
        if (!visible || controller.state != Tunnel.State.UP) return
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

    /**
     * Menampilkan laju trafik (jendela geser 5 dtk) dan total sesi setiap detik selama
     * UP; mendeteksi tunnel basi. Dipanggil tiap tick tiker (1 Hz) — baca penghitung
     * murah, dan tiker mati saat UI tak terlihat, jadi tidak menambah beban latar.
     */
    private fun pollStats() {
        controller.runStats { t ->
            if (controller.state != Tunnel.State.UP) return@runStats
            if (t == null) {
                infoData.setText(R.string.value_none)
                return@runStats
            }
            val nowMs = SystemClock.elapsedRealtime()
            val rates = rate.add(t.rxBytes, t.txBytes, nowMs)
            infoData.text = renderData(rates, t.rxBytes, t.txBytes)
            if (t.rxBytes != lastRxBytes || t.txBytes != lastTxBytes) {
                lastRxBytes = t.rxBytes
                lastTxBytes = t.txBytes
                lastTrafficMs = nowMs
                staleWarned = false
                return@runStats
            }
            val hsAge = System.currentTimeMillis() - t.latestHandshakeMs
            if (!staleWarned && nowMs - lastTrafficMs >= STALE_MS &&
                (t.latestHandshakeMs == 0L || hsAge > 180_000)
            ) {
                staleWarned = true
                setMessage(getString(R.string.stale_warn))
            }
        }
    }

    /**
     * Merangkai baris Data: laju (jendela geser) + total sesi. Total dibaca langsung
     * dari penghitung kumulatif backend, jadi terlihat sejak sampel pertama; laju `null`
     * berarti belum ada dua sampel — ditampilkan sebagai "—", jujur daripada mengarang.
     */
    private fun renderData(rates: VelumRate.Rates?, rxTotal: Long, txTotal: Long): String {
        val laju = if (rates == null) {
            getString(R.string.value_none)
        } else {
            getString(
                R.string.data_format,
                VelumFormat.formatBytes(rates.rxBps.toLong()),
                VelumFormat.formatBytes(rates.txBps.toLong())
            )
        }
        val total = getString(
            R.string.data_total_format,
            VelumFormat.formatBytes(rxTotal),
            VelumFormat.formatBytes(txTotal)
        )
        return "$laju\n$total"
    }

    private companion object {
        /** Deteksi basi: tanpa perubahan trafik selama ini (ms) + handshake tua/tiada. */
        const val STALE_MS = 30_000L
    }
}
