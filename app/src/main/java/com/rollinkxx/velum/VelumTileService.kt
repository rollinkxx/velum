package com.rollinkxx.velum

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors

/**
 * Ubin pengaturan cepat: menyambung/memutus tanpa membuka aplikasi.
 *
 * Berjalan di proses aplikasi yang sama. Bila proses sedang mati, status ubin
 * hanya mengikuti nilai yang tersimpan di backend WireGuard — cukup untuk
 * menyalakan, dan disegarkan begitu ubin terlihat.
 */
class VelumTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        val wasUp = VelumTunnel.state == Tunnel.State.UP
        if (!wasUp && VpnService.prepare(app) != null) {
            // Belum ada persetujuan VPN: hanya aplikasi yang bisa memintanya.
            openApp()
            return
        }
        worker.execute {
            val prefs = Prefs.of(app)
            try {
                if (wasUp) {
                    prefs.wasUp = false
                    ReconnectMonitor.stop(app)
                    VelumTunnel.down(app)
                } else if (prefs.isRegistered) {
                    EndpointProbe.refresh(prefs)
                    VelumTunnel.up(app, prefs)
                    prefs.wasUp = true
                    ReconnectMonitor.ensure(app)
                } else {
                    openApp()
                }
            } catch (e: Exception) {
                Log.w(TAG, "aksi ubin gagal", e)
            }
            updateTile()
        }
    }

    private fun updateTile() {
        main.post {
            val tile = qsTile ?: return@post
            tile.state =
                if (VelumTunnel.state == Tunnel.State.UP) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)
    }

    private companion object {
        const val TAG = "Velum"
    }
}
