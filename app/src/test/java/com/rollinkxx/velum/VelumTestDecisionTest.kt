package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pengujian unit untuk [VelumTestDecision] — aturan yang mencegah terulangnya
 * "Belum lewat Velum" palsu **dan** "kesalahan jaringan" yang menyesatkan saat
 * handshake belum terjadi, tanpa harus menjalankan aplikasi di perangkat.
 */
class VelumTestDecisionTest {

    private val aktif = VelumFormat.parseTrace("warp=on\ncolo=DPS\n")
    private val nonaktif = VelumFormat.parseTrace("warp=off\ncolo=DPS\n")

    private fun decide(
        tunnelUp: Boolean = true,
        handshakeReady: Boolean = true,
        trace: VelumFormat.TraceInfo? = null,
        error: String? = null,
        attempt: Int = 0,
        maxAttempts: Int = 2
    ) = VelumTestDecision.decide(tunnelUp, handshakeReady, trace, error, attempt, maxAttempts)

    @Test
    fun tunnelTurun_hasilDibuang() {
        assertEquals(TestAction.DROP, decide(tunnelUp = false, trace = nonaktif))
    }

    @Test
    fun tunnelTurun_dibuangWalauHasilnyaPositif() {
        assertEquals(TestAction.DROP, decide(tunnelUp = false, trace = aktif, attempt = 1))
    }

    @Test
    fun hasilPositif_langsungDitampilkan() {
        assertEquals(TestAction.PUBLISH, decide(trace = aktif))
    }

    @Test
    fun hasilNegatifPertama_diulang() {
        // Gejala lama: hasil negatif langsung dipercaya padahal tunnel baru saja UP.
        assertEquals(TestAction.RETRY, decide(trace = nonaktif))
    }

    @Test
    fun hasilNegatifKedua_ditampilkanBukanDiulangTerus() {
        assertEquals(TestAction.PUBLISH, decide(trace = nonaktif, attempt = 1))
    }

    @Test
    fun gagalJaringanSaatHandshakeSudahAda_diulangSekali() {
        // Kegagalan nyata (handshake sudah terjadi) layak dicoba ulang dengan soket baru.
        assertEquals(TestAction.RETRY, decide(trace = null, error = "timeout"))
    }

    @Test
    fun gagalJaringanPadaPercobaanTerakhir_ditampilkan() {
        assertEquals(TestAction.PUBLISH, decide(trace = null, error = "timeout", attempt = 1))
    }

    // --- Keadaan tanpa handshake (temuan lapangan 2026-09-12) ---

    @Test
    fun tanpaHandshake_diulangDenganEndpointLain() {
        // Galat DNS pada keadaan ini hanyalah gejala; mengulang dengan endpoint lain
        // jauh lebih berguna daripada langsung melaporkan "kesalahan jaringan".
        assertEquals(
            TestAction.RETRY,
            decide(handshakeReady = false, trace = null, error = "Unable to resolve host")
        )
    }

    @Test
    fun tanpaHandshakePadaPercobaanTerakhir_bukanKegagalanJaringan() {
        assertEquals(
            TestAction.PUBLISH_NO_DATA,
            decide(handshakeReady = false, trace = null, error = "Unable to resolve host", attempt = 1)
        )
    }

    @Test
    fun tanpaHandshake_danTunnelTurun_tetapDibuang() {
        assertEquals(
            TestAction.DROP,
            decide(tunnelUp = false, handshakeReady = false, error = "Unable to resolve host")
        )
    }

    @Test
    fun tanpaBatasPercobaan_tidakPernahMengulang() {
        assertEquals(TestAction.PUBLISH, decide(trace = nonaktif, maxAttempts = 1))
        assertEquals(TestAction.PUBLISH_NO_DATA, decide(handshakeReady = false, maxAttempts = 1))
    }

    @Test
    fun percobaanDiLuarBatas_tidakMengulang() {
        assertEquals(TestAction.PUBLISH, decide(trace = nonaktif, attempt = 5))
    }
}
