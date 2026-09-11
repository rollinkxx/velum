package com.rollinkxx.warp

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Satu layar: status + tombol Sambungkan/Putuskan + Uji koneksi + panel info interaktif
 * (durasi, endpoint, hasil uji terakhir). Pekerjaan jaringan/tunnel berjalan di satu
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
    private lateinit var infoDuration: TextView
    private lateinit var infoEndpoint: TextView
    private lateinit var infoTest: TextView

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var busy = false

    private var prevState: Tunnel.State = Tunnel.State.DOWN
    private var connectedSinceMs = 0L
    private var testedSinceUp = false
    private var pulse: ValueAnimator? = null

    private val ticker = object : Runnable {
        override fun run() {
            infoDuration.text = formatDuration(SystemClock.elapsedRealtime() - connectedSinceMs)
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusView = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        messageView = findViewById(R.id.message)
        toggleButton = findViewById(R.id.toggle)
        testButton = findViewById(R.id.test)
        resetButton = findViewById(R.id.reset)
        infoDuration = findViewById(R.id.infoDuration)
        infoEndpoint = findViewById(R.id.infoEndpoint)
        infoTest = findViewById(R.id.infoTest)

        toggleButton.setOnClickListener { onToggle() }
        testButton.setOnClickListener { onTest() }
        resetButton.setOnClickListener { onReset() }

        refreshStaticInfo()

        WarpTunnel.listener = { state -> main.post { render(state) } }
    }

    override fun onStart() {
        super.onStart()
        render(WarpTunnel.state)
        worker.execute {
            val s = runCatching { WarpTunnel.refreshState(this) }.getOrDefault(WarpTunnel.state)
            main.post { render(s) }
        }
    }

    override fun onDestroy() {
        WarpTunnel.listener = null
        main.removeCallbacks(ticker)
        pulse?.cancel()
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---------- Aksi ----------

    private fun onToggle() {
        if (busy) return
        if (WarpTunnel.state == Tunnel.State.UP) {
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
                if (!prefs.isRegistered) {
                    main.post { statusView.setText(R.string.status_registering) }
                    try {
                        WarpApi.register(prefs)
                    } catch (e: Exception) {
                        fail(getString(R.string.err_register, e.message ?: e.javaClass.simpleName))
                        return@execute
                    }
                }
                main.post { statusView.setText(R.string.status_connecting) }
                WarpTunnel.up(this, prefs)
                main.post { setBusy(false); render(WarpTunnel.state) }
            } catch (e: Exception) {
                fail(getString(R.string.err_connect, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun disconnect() {
        setBusy(true)
        statusView.setText(R.string.status_disconnecting)
        worker.execute {
            runCatching { WarpTunnel.down(this) }
            main.post { setBusy(false); render(WarpTunnel.state) }
        }
    }

    private fun onTest() {
        if (busy) return
        runTraceTest(fromButton = true)
    }

    /** Menjalankan uji trace; dipakai tombol Uji koneksi dan auto-uji saat tersambung. */
    private fun runTraceTest(fromButton: Boolean) {
        if (fromButton) showMessage(getString(R.string.test_running))
        infoTest.setText(R.string.test_running)
        worker.execute {
            try {
                val trace = WarpApi.fetchTrace()
                val active = trace.warp == "on" || trace.warp == "plus"
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                main.post {
                    infoTest.text = if (active) {
                        getString(R.string.test_on_dc, trace.colo.ifEmpty { "?" }, time)
                    } else {
                        getString(R.string.test_off_time, time)
                    }
                    if (fromButton) {
                        showMessage(getString(if (active) R.string.test_on else R.string.test_off))
                    }
                }
            } catch (e: Exception) {
                main.post {
                    infoTest.setText(R.string.test_failed)
                    if (fromButton) {
                        showMessage(getString(R.string.err_network, e.message ?: e.javaClass.simpleName))
                    }
                }
            }
        }
    }

    private fun onReset() {
        if (busy) return
        setBusy(true)
        worker.execute {
            runCatching { WarpTunnel.down(this) }
            WarpApi.unregister(prefs)
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
            render(WarpTunnel.state)
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
        infoEndpoint.text = prefs.endpoint ?: getString(R.string.value_none)
    }

    private fun onConnectedVisual() {
        connectedSinceMs = SystemClock.elapsedRealtime()
        main.removeCallbacks(ticker)
        main.post(ticker)
        startPulse()
        refreshStaticInfo()
        if (!testedSinceUp && prefs.isRegistered) {
            testedSinceUp = true
            runTraceTest(fromButton = false)
        }
    }

    private fun onDisconnectedVisual() {
        testedSinceUp = false
        main.removeCallbacks(ticker)
        stopPulse()
        infoDuration.setText(R.string.value_none)
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
        if (busy) return
        if (state != prevState) {
            prevState = state
            if (state == Tunnel.State.UP) onConnectedVisual() else onDisconnectedVisual()
        }
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

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private companion object {
        const val REQ_VPN = 1
    }
}
