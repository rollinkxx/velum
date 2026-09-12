package com.rollinkxx.velum

/**
 * Ringkasan keadaan aplikasi untuk dilampirkan pada laporan gangguan.
 *
 * Sengaja **ramah privasi**: tidak ada kunci privat, identitas perangkat, token,
 * maupun alamat IP. Yang dikirim hanya keadaan teknis yang aman dibagikan.
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
        val lastTest: String? = null
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
        append("Uji terakhir : ").append(
            if (s.lastTest.isNullOrEmpty()) "belum ada" else s.lastTest
        ).append('\n')
        append("Dikecualikan: ").append(
            if (s.excludedApps.isEmpty()) "tidak ada" else "${s.excludedApps.size} aplikasi"
        ).append('\n')
        append("Catatan     : tanpa kunci, identitas perangkat, atau alamat IP").append('\n')
    }
}
