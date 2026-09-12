package com.rollinkxx.velum

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/**
 * Menyambung ulang tunnel tanpa campur tangan pengguna pada dua peristiwa:
 *
 * 1. **`BOOT_COMPLETED`** — perangkat baru menyala.
 * 2. **`MY_PACKAGE_REPLACED`** — aplikasi ini baru diperbarui. Pembaruan mematikan proses,
 *    dan proses yang mati berarti tunnel ikut mati; tanpa peristiwa kedua ini tunnel tetap
 *    mati padahal [Prefs.wasUp] masih `true`, sampai jaringan kebetulan berganti atau
 *    pengguna membuka aplikasi. Untuk aplikasi yang dimaksudkan selalu aktif, diam-diam
 *    mati setiap kali diperbarui adalah kegagalan yang terlihat jelas oleh pengguna.
 *
 * Keduanya hanya dijalankan bila: terakhir tunnel memang UP ([Prefs.wasUp]), perangkat
 * sudah terdaftar, dan persetujuan VPN dari pengguna masih berlaku
 * (`VpnService.prepare == null`). Bila persetujuan hilang, tidak dilakukan apa pun —
 * pengguna menyambung manual dari aplikasi.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = Prefs.of(context)
        if (!prefs.isRegistered || !prefs.wasUp) return
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "$action: persetujuan VPN tidak ada, sambung ulang dibatalkan")
            return
        }
        val pending = goAsync()
        // RISIKO YANG DIKETAHUI DAN SENGAJA DIPERTAHANKAN (butuh perangkat untuk diputuskan):
        // `up()` di bawah bisa memakan 2 detik (GoBackend menunggu VpnService) ditambah
        // hingga 10 x 1 detik retry resolusi DNS (`DNS_RESOLUTION_RETRIES = 10` pada
        // GoBackend.java:43) bila endpoint berupa nama domain dan DNS belum siap — kondisi
        // khas saat boot. Totalnya bisa melewati anggaran receiver.
        //
        // Alternatif yang tampak lebih bersih — serahkan ke ReconnectMonitor lalu selesai
        // tanpa menunggu — TIDAK diambil, karena `goAsync()` juga menahan proses tetap
        // hidup selama pekerjaan berlangsung. Tanpa itu, proses yang baru lahir untuk
        // broadcast ini bisa dibunuh sebelum tunnel naik, dan kegagalannya sama senyapnya.
        // Jadi pilihannya bukan "aman vs berisiko", melainkan dua risiko berbeda:
        // melebihi anggaran receiver, atau kehilangan proses di tengah penyambungan.
        // Memutuskannya butuh pengukuran di perangkat (lihat docs/uji-perangkat.md),
        // bukan penalaran dari sandbox. Yang sudah dijaga di sini: kegagalan `up()` tidak
        // menghalangi `ReconnectMonitor.ensure()`, jadi peristiwa jaringan berikutnya
        // tetap punya peluang memulihkan tunnel.
        Thread {
            try {
                VelumTunnel.up(context, prefs)
            } catch (e: Exception) {
                Log.w(TAG, "$action: sambung ulang gagal", e)
            } finally {
                // Jaga sesi: bila peristiwa ini datang sebelum jaringan siap (khas saat
                // boot), callback Available milik pemantau yang akan memulihkan.
                ReconnectMonitor.ensure(context)
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "Velum"
    }
}
