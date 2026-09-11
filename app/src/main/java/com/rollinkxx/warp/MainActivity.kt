package com.rollinkxx.warp

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Satu layar: status + tombol Sambungkan/Putuskan + Uji koneksi.
 * Pekerjaan jaringan/tunnel dijalankan di satu thread latar tunggal (tanpa coroutine
 * library) agar footprint tetap kecil.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var statusDot: View
    private lateinit var messageView: TextView
    private lateinit var toggleButton: Button
    private lateinit var testButton: Button
    private lateinit var resetButton: Button

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var busy = false

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

        toggleButton.setOnClickListener { onToggle() }
        testButton.setOnClickListener { onTest() }
        resetButton.setOnClickListener { onReset() }

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
        showMessage(getString(R.string.test_running))
        worker.execute {
            val text = try {
                if (WarpApi.isWarpActive()) getString(R.string.test_on) else getString(R.string.test_off)
            } catch (e: Exception) {
                getString(R.string.err_network, e.message ?: e.javaClass.simpleName)
            }
            main.post { showMessage(text) }
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

    private fun render(state: Tunnel.State) {
        if (busy) return
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

    private companion object {
        const val REQ_VPN = 1
    }
}
