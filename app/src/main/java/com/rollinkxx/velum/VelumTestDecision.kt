package com.rollinkxx.velum

/** Tindakan yang diambil setelah satu percobaan uji trace selesai. */
enum class TestAction {
    /** Ulangi sekali dengan soket baru (hasilnya meragukan padahal tunnel UP). */
    RETRY,

    /** Tampilkan hasilnya ke pengguna. */
    PUBLISH,

    /** Buang hasilnya: tunnel sudah turun, nilainya menyesatkan. */
    DROP
}

/**
 * Keputusan uji trace — murni tanpa Android framework supaya bisa diuji unit.
 *
 * Latar belakang: hasil "tidak lewat WARP" bisa palsu bila permintaan keluar
 * sebelum handshake selesai atau memakai soket sisa dari sebelum VPN aktif. Karena
 * itu satu hasil negatif saat tunnel masih UP **tidak langsung dipercaya**: ia
 * diulang sekali lebih dulu.
 */
object VelumTestDecision {

    fun decide(
        tunnelUp: Boolean,
        trace: VelumFormat.TraceInfo?,
        error: String?,
        attempt: Int,
        maxAttempts: Int
    ): TestAction = when {
        // Tunnel turun di tengah uji: hasilnya tidak menggambarkan keadaan akhir.
        !tunnelUp -> TestAction.DROP
        // Gagal jaringan (bukan "tidak lewat WARP") faktual: langsung tampilkan.
        error != null -> TestAction.PUBLISH
        // Positif: tak perlu diulang.
        trace != null && VelumFormat.isWarpActive(trace) -> TestAction.PUBLISH
        // Negatif/meragukan padahal UP → coba sekali lagi dengan soket baru.
        attempt + 1 < maxAttempts -> TestAction.RETRY
        else -> TestAction.PUBLISH
    }
}
