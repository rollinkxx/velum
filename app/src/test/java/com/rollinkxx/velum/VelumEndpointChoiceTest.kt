package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji [VelumEndpointChoice] — keputusan yang menentukan boleh tidaknya aplikasi
 * mengklaim "endpoint sudah diganti".
 *
 * Yang paling penting dijaga adalah kasus [pemenangBukanLiteralIp_danEfektifnyaSamaDenganYangGagal]:
 * di situlah rotasi dulu melaporkan `false` ("tidak ada pengganti") sambil sudah
 * mengosongkan `workingEndpoint`, sehingga endpoint efektif berubah tanpa sepengetahuan
 * pemanggil dan uji ulang mengulang host yang sama persis.
 */
class VelumEndpointChoiceTest {

    private val port = 2408
    private val ipA = "162.159.192.1"
    private val ipB = "162.159.193.1"
    private val regHost = "engage.cloudflareclient.com"

    private fun putar(
        ranked: List<String>,
        current: String?,
        registration: String? = regHost
    ) = VelumEndpointChoice.rotate(ranked, current, registration, port)

    @Test
    fun kandidatBerbeda_dipilihDanPerpindahanNyata() {
        val d = putar(listOf(ipA, ipB), current = ipA)
        assertEquals(ipB, d.host)
        assertEquals("$ipB:$port", d.speedEndpoint)
        assertEquals(ipB, d.effectiveHost)
        assertTrue(d.changed)
    }

    @Test
    fun pemenangTercepatSamaDenganYangGagal_dilewati() {
        // Peringkat pertama justru host yang gagal handshake: bukan pengganti.
        val d = putar(listOf(ipA, ipB), current = ipA)
        assertEquals(ipB, d.host)
        assertTrue(d.changed)
    }

    @Test
    fun daftarKosong_tidakAdaKeputusan() {
        val d = putar(emptyList(), current = ipA)
        assertNull(d.host)
        assertNull(d.speedEndpoint)
        assertFalse(d.changed)
        // effectiveHost tetap yang lama: pemanggil tidak boleh mengira endpoint berubah.
        assertEquals(ipA, d.effectiveHost)
    }

    @Test
    fun satuSatunyaKandidatAdalahYangGagal_tidakAdaPengganti() {
        val d = putar(listOf(ipA), current = ipA)
        assertNull(d.host)
        assertFalse(d.changed)
    }

    @Test
    fun currentTidakDiketahui_kandidatPertamaDipakai() {
        val d = putar(listOf(ipA, ipB), current = null)
        assertEquals(ipA, d.host)
        assertEquals("$ipA:$port", d.speedEndpoint)
        assertTrue("tanpa pembanding, pemasangan apa pun adalah perpindahan", d.changed)
    }

    @Test
    fun pemenangBukanLiteralIp_speedEndpointKosongTapiEfektifnyaHostRegistrasi() {
        // Nama domain tidak dipasang sebagai speedEndpoint; yang berlaku lalu endpoint
        // registrasi. Selama itu berbeda dari yang gagal, rotasinya tetap nyata.
        val d = putar(listOf(regHost), current = ipA)
        assertEquals(regHost, d.host)
        assertNull(d.speedEndpoint)
        assertEquals(regHost, d.effectiveHost)
        assertTrue(d.changed)
    }

    @Test
    fun pemenangBukanLiteralIp_danEfektifnyaSamaDenganYangGagal() {
        // REGRESI: `next` berbeda dari yang gagal, tetapi karena ia bukan literal IPv4
        // tidak ada speedEndpoint yang terpasang, sehingga endpoint efektif jatuh kembali
        // ke host registrasi — yang justru host yang sedang gagal. `changed` harus false
        // supaya pemanggil tidak mengklaim ada perpindahan.
        val d = putar(listOf("kandidat-lain.example", regHost), current = regHost)
        assertEquals("kandidat-lain.example", d.host)
        assertNull(d.speedEndpoint)
        assertEquals(regHost, d.effectiveHost)
        assertFalse(d.changed)
    }

    @Test
    fun hostRegistrasiTidakAda_danPemenangBukanLiteralIp_tidakAdaYangBisaDipasang() {
        val d = putar(listOf(regHost), current = ipA, registration = null)
        assertEquals(regHost, d.host)
        assertNull(d.speedEndpoint)
        assertNull(d.effectiveHost)
        assertFalse("tanpa tujuan jatuh tempo, tidak ada endpoint efektif yang bisa dipastikan", d.changed)
    }

    @Test
    fun portYangDipakaiAdalahPortYangDiberikan() {
        val d = VelumEndpointChoice.rotate(listOf(ipA), currentHost = null, registrationHost = null, wgPort = 500)
        assertEquals("$ipA:500", d.speedEndpoint)
        // hostPart harus melepas port itu lagi, bukan menganggapnya bagian dari host.
        assertEquals(ipA, d.effectiveHost)
    }
}
