package com.rollinkxx.velum

/** Tindakan yang diambil setelah satu percobaan uji trace selesai. */
enum class TestAction {
    /** Ulangi (dengan soket baru, atau endpoint baru bila handshake belum terjadi). */
    RETRY,

    /** Tampilkan hasilnya ke pengguna. */
    PUBLISH,

    /** Tidak ada data yang lewat: tampilkan sebagai keadaan "belum ada data", bukan kegagalan. */
    PUBLISH_NO_DATA,

    /** Buang hasilnya: tunnel sudah turun, nilainya menyesatkan. */
    DROP
}

/**
 * Keputusan uji trace — murni tanpa Android framework supaya bisa diuji unit.
 *
 * Dua pelajaran nyata yang membentuk aturan di bawah:
 *
 * 1. `Tunnel.State.UP` hanya berarti antarmuka TUN sudah dibuat, **bukan** handshake
 *    selesai. Tanpa handshake, permintaan uji tidak akan keluar lewat tunnel — yang
 *    terlihat di perangkat adalah galat DNS ("Unable to resolve host ...") setelah
 *    menunggu belasan detik. Karena itu keadaan itu dipisahkan sendiri ([PUBLISH_NO_DATA])
 *    dan lebih dulu dicoba ulang dengan endpoint lain.
 * 2. Hasil negatif saat tunnel masih UP pernah palsu ("Belum lewat Velum" padahal aktif),
 *    jadi satu hasil negatif tidak langsung dipercaya — diulang sekali lebih dulu.
 */
object VelumTestDecision {

    fun decide(
        tunnelUp: Boolean,
        handshakeReady: Boolean,
        trace: VelumFormat.TraceInfo?,
        error: String?,
        attempt: Int,
        maxAttempts: Int
    ): TestAction = when {
        // Tunnel turun di tengah uji: hasilnya tidak menggambarkan keadaan akhir.
        !tunnelUp -> TestAction.DROP
        // Belum ada handshake: mengulang dengan endpoint lain jauh lebih berguna daripada
        // melaporkan galat DNS yang menyesatkan.
        !handshakeReady ->
            if (attempt + 1 < maxAttempts) TestAction.RETRY else TestAction.PUBLISH_NO_DATA
        // Sudah ada handshake, jadi kegagalan ini nyata (jaringan/HTTP): layak dicoba ulang
        // sekali dengan soket baru sebelum dinyatakan gagal ke pengguna.
        error != null -> if (attempt + 1 < maxAttempts) TestAction.RETRY else TestAction.PUBLISH
        // Positif: tak perlu diulang.
        trace != null && VelumFormat.isWarpActive(trace) -> TestAction.PUBLISH
        // Negatif/meragukan padahal UP dan handshake sudah ada → coba sekali lagi.
        attempt + 1 < maxAttempts -> TestAction.RETRY
        else -> TestAction.PUBLISH
    }
}
