package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pengujian unit untuk [VelumTestDecision] — aturan yang mencegah terulangnya
 * "Belum lewat Velum" palsu tanpa harus menjalankan aplikasi di perangkat.
 */
class VelumTestDecisionTest {

    private val aktif = VelumFormat.parseTrace("warp=on\ncolo=DPS\n")
    private val nonaktif = VelumFormat.parseTrace("warp=off\ncolo=DPS\n")

    @Test
    fun tunnelTurun_hasilDibuang() {
        assertEquals(
            TestAction.DROP,
            VelumTestDecision.decide(tunnelUp = false, trace = nonaktif, error = null, attempt = 0, maxAttempts = 2)
        )
    }

    @Test
    fun tunnelTurun_dibuangWalauHasilnyaPositif() {
        assertEquals(
            TestAction.DROP,
            VelumTestDecision.decide(tunnelUp = false, trace = aktif, error = null, attempt = 1, maxAttempts = 2)
        )
    }

    @Test
    fun gagalJaringan_langsungDitampilkan() {
        assertEquals(
            TestAction.PUBLISH,
            VelumTestDecision.decide(tunnelUp = true, trace = null, error = "timeout", attempt = 0, maxAttempts = 2)
        )
    }

    @Test
    fun hasilPositif_langsungDitampilkan() {
        assertEquals(
            TestAction.PUBLISH,
            VelumTestDecision.decide(tunnelUp = true, trace = aktif, error = null, attempt = 0, maxAttempts = 2)
        )
    }

    @Test
    fun hasilNegatifPertama_diulang() {
        // Gejala lama: hasil negatif langsung dipercaya padahal tunnel baru saja UP.
        assertEquals(
            TestAction.RETRY,
            VelumTestDecision.decide(tunnelUp = true, trace = nonaktif, error = null, attempt = 0, maxAttempts = 2)
        )
    }

    @Test
    fun hasilNegatifKedua_ditampilkanBukanDiulangTerus() {
        assertEquals(
            TestAction.PUBLISH,
            VelumTestDecision.decide(tunnelUp = true, trace = nonaktif, error = null, attempt = 1, maxAttempts = 2)
        )
    }

    @Test
    fun tanpaBatasPercobaan_tidakPernahMengulang() {
        assertEquals(
            TestAction.PUBLISH,
            VelumTestDecision.decide(tunnelUp = true, trace = nonaktif, error = null, attempt = 0, maxAttempts = 1)
        )
    }

    @Test
    fun percobaanDiLuarBatas_tidakMengulang() {
        assertEquals(
            TestAction.PUBLISH,
            VelumTestDecision.decide(tunnelUp = true, trace = nonaktif, error = null, attempt = 5, maxAttempts = 2)
        )
    }
}
