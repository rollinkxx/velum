package com.rollinkxx.velum

/**
 * Ringkasan keadaan aplikasi untuk dilampirkan pada laporan gangguan.
 *
 * Sengaja **ramah privasi**: tidak memuat kunci privat, identitas perangkat, token,
 * maupun alamat IP **pengguna**. Yang dikirim hanya keadaan teknis yang aman dibagikan.
 *
 * Batas yang jujur: baris `Endpoint` memang memuat sebuah alamat IP, tetapi itu IP PoP
 * anycast Cloudflare yang dipakai bersama oleh semua pelanggan di wilayah tersebut —
 * bukan pengenal pengguna. Kalimat catatan di dalam ringkasan ditulis mengikuti batas
 * itu, supaya tidak mengklaim sesuatu yang bisa dibantah dengan menunjuk laporannya sendiri.
 *
 * Murni (tanpa Android framework) supaya teruji unit.
 */
object VelumDiagnostics {

    data class Snapshot(
        val appVersion: String,
        val state: String,
        val endpoint: String?,
        /** Umur handshake terakhir dalam detik; null bila belum pernah handshake. */
        val handshakeAgeSec: Long?,
        val rxBytes: Long,
        val txBytes: Long,
        val connectedSec: Long,
        val excludedApps: List<String> = emptyList(),
        /** Hasil uji terakhir yang sudah siap dibaca (mis. "Aktif · DC SIN · 15:25"). */
        val lastTest: String? = null,
        /**
         * Apakah penyimpanan jatuh ke berkas POLOS karena keystore perangkat gagal,
         * sehingga kunci privat tersimpan tanpa enkripsi.
         *
         * Bernilai bawaan `false` supaya ringkasan tetap 9 baris pada keadaan normal;
         * baris peringatan hanya muncul ketika memang ada yang perlu diperingatkan.
         */
        val plaintextFallback: Boolean = false
    )

    /** Teks ringkasan siap salin. */
    fun render(s: Snapshot): String = buildString {
        append("Velum ").append(s.appVersion).append('\n')
        append("Status      : ").append(s.state).append('\n')
        append("Endpoint    : ").append(s.endpoint ?: "-").append('\n')
        append("Handshake   : ").append(
            if (s.handshakeAgeSec == null) "belum ada" else "${s.handshakeAgeSec} detik lalu"
        ).append('\n')
        append("Durasi      : ").append(VelumFormat.formatDuration(s.connectedSec * 1000)).append('\n')
        append("Trafik      : turun ").append(VelumFormat.formatBytes(s.rxBytes))
            .append(" · naik ").append(VelumFormat.formatBytes(s.txBytes)).append('\n')
        append("Uji terakhir: ").append(
            if (s.lastTest.isNullOrEmpty()) "belum ada" else s.lastTest
        ).append('\n')
        append("Dikecualikan: ").append(
            if (s.excludedApps.isEmpty()) "tidak ada" else "${s.excludedApps.size} aplikasi"
        ).append('\n')
        // Dulu berbunyi "tanpa kunci, identitas perangkat, atau alamat IP" — padahal baris
        // Endpoint di atas jelas memuat sebuah alamat IP. Yang dimaksud memang IP pengguna,
        // bukan IP PoP Cloudflare, tetapi bagi aplikasi yang menawarkan privasi kalimat yang
        // bisa dibantah dengan menunjuk laporannya sendiri adalah kerugian yang tak perlu.
        append("Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda").append('\n')
        if (s.plaintextFallback) {
            append("Peringatan  : penyimpanan TIDAK terenkripsi (keystore perangkat gagal)").append('\n')
        }
    }
}
