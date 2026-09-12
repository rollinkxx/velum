package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uji [VelumTestResult]: terjemahan hasil mentah uji menjadi keadaan yang bermakna,
 * dan penyandiannya (hasil disimpan sebagai satu baris teks di [Prefs]).
 *
 * Yang paling penting dijaga: keadaan **tanpa handshake tidak boleh menjadi FAILED** —
 * itulah yang dulu menampilkan "Kesalahan jaringan: Unable to resolve host ..." dan
 * menyalahkan jaringan pengguna padahal tunnelnya yang belum mengalirkan data.
 */
class VelumTestResultTest {

    private val aktif = VelumFormat.parseTrace("warp=on\ncolo=SIN\nip=1.2.3.4\n")
    private val nonaktif = VelumFormat.parseTrace("warp=off\ncolo=SIN\n")
    private val jam = 1_760_000_000_000L

    @Test
    fun tanpaHandshake_menjadiNoDataMeskiAdaGalatDns() {
        val hasil = VelumTestResult.of(
            handshakeReady = false,
            trace = null,
            error = "Unable to resolve host \"www.cloudflare.com\"",
            atEpochMs = jam
        )
        assertEquals(VelumTestResult.Kind.NO_DATA, hasil.kind)
        // Rincian galat sengaja tidak disimpan: itu gejala, bukan sebab.
        assertNull(hasil.detail)
    }

    @Test
    fun handshakeAda_traceAktif_menjadiActiveDenganColo() {
        val hasil = VelumTestResult.of(true, aktif, null, jam)
        assertEquals(VelumTestResult.Kind.ACTIVE, hasil.kind)
        assertEquals("SIN", hasil.colo)
    }

    @Test
    fun handshakeAda_traceNonaktif_menjadiOff() {
        assertEquals(VelumTestResult.Kind.OFF, VelumTestResult.of(true, nonaktif, null, jam).kind)
    }

    @Test
    fun handshakeAda_galatNyata_menjadiFailedDenganRincian() {
        val hasil = VelumTestResult.of(true, null, "timeout", jam)
        assertEquals(VelumTestResult.Kind.FAILED, hasil.kind)
        assertEquals("timeout", hasil.detail)
    }

    @Test
    fun coloKosong_ditulisTanya() {
        val tanpaColo = VelumFormat.parseTrace("warp=on\n")
        assertEquals("?", VelumTestResult.of(true, tanpaColo, null, jam).colo)
    }

    @Test
    fun sandiBolakBalik_utuhTermasukRincianBerPemisah() {
        val asli = VelumTestResult(
            kind = VelumTestResult.Kind.FAILED,
            atEpochMs = jam,
            colo = null,
            detail = "gagal | dengan | pemisah"
        )
        val kembali = VelumTestResult.decode(asli.encode())
        assertEquals(asli, kembali)
    }

    @Test
    fun teksRusak_menghasilkanNullBukanCrash() {
        for (rusak in listOf(null, "", "bukan|angka", "KIND_ANEh|1", "ACTIVE")) {
            assertNull(rusak, VelumTestResult.decode(rusak))
        }
    }
}
