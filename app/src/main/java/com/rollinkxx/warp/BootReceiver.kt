package com.rollinkxx.warp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/**
 * Menyambung ulang tunnel setelah perangkat boot, bila:
 * - terakhir kali tunnel memang UP (memo [Prefs.wasUp]), dan
 * - perangkat sudah terdaftar, dan
 * - persetujuan VPN dari pengguna masih berlaku (VpnService.prepare == null).
 * Bila persetujuan hilang, tidak melakukan apa pun — pengguna menyambung manual dari aplikasi.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (!prefs.isRegistered || !prefs.wasUp) return
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "boot: persetujuan VPN tidak ada, sambung ulang dibatalkan")
            return
        }
        val pending = goAsync()
        Thread {
            try {
                WarpTunnel.up(context, prefs)
            } catch (e: Exception) {
                Log.w(TAG, "boot: sambung ulang gagal", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "WarpLite"
    }
}
