package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji [VelumDoh] — parse jawaban DNS-over-HTTPS untuk penyegaran kandidat anycast.
 *
 * Prinsip yang dikunci: jawaban aneh (rekaman non-A, bukan JSON, host yang menangkap
 * DNS) harus berarti "tidak ada kandidat baru" — BUKAN pengecualian, dan BUKAN
 * kandidat bohong yang membuat proba mengukur host milik orang lain.
 */
class VelumDohTest {

    private fun jawaban(vararg data: String, type: Int = 1): String {
        val entri = data.joinToString(",") {
            "{\"name\":\"engage.cloudflareclient.com.\",\"type\":$type,\"TTL\":60,\"data\":\"$it\"}"
        }
        return "{\"Status\":0,\"TC\":false,\"RD\":true,\"RA\":true,\"AD\":false,\"CD\":false," +
            "\"Question\":[{\"name\":\"engage.cloudflareclient.com.\",\"type\":1}],\"Answer\":[$entri]}"
    }

    @Test
    fun jawabanNormal_semuaRekamanAterambil() {
        val ips = VelumDoh.parseARecords(jawaban("162.159.192.7", "162.159.193.7", "188.114.96.7"))
        assertEquals(listOf("162.159.192.7", "162.159.193.7", "188.114.96.7"), ips)
    }

    @Test
    fun rekamanBukanA_diabaikan() {
        // type 5 = CNAME, type 28 = AAAA: kandidat proba kami memang IPv4.
        val campur = "{\"Answer\":[" +
            "{\"type\":5,\"data\":\"engage.cloudflareclient.com.\"}," +
            "{\"type\":1,\"data\":\"162.159.192.7\"}," +
            "{\"type\":28,\"data\":\"2606:4700:d0::a29f:c007\"}]}"
        assertEquals(listOf("162.159.192.7"), VelumDoh.parseARecords(campur))
    }

    @Test
    fun dataBukanIpv4Sah_dibuang() {
        assertEquals(emptyList<String>(), VelumDoh.parseARecords(jawaban("999.1.1.1", "bukan-ip", "")))
    }

    @Test
    fun jawabanBukanJson_daftarKosong() {
        // Portal tawanan menjawab HTML — tidak boleh menjadi kandidat apa pun.
        assertEquals(emptyList<String>(), VelumDoh.parseARecords("<html><body>login</body></html>"))
    }

    @Test
    fun tanpaBagianAnswer_daftarKosong() {
        assertEquals(emptyList<String>(), VelumDoh.parseARecords("{\"Status\":3}"))
    }

    @Test
    fun duplikat_dipadatkan_sekali() {
        assertEquals(listOf("162.159.192.7"), VelumDoh.parseARecords(jawaban("162.159.192.7", "162.159.192.7")))
    }

    @Test
    fun lebihDariBatas_dipotongDemiAnggaranProba() {
        val banyak = (1..20).map { "10.0.0.$it" }.toTypedArray()
        val ips = VelumDoh.parseARecords(jawaban(*banyak))
        assertEquals(VelumDoh.MAX_STORED, ips.size)
        assertTrue(ips.first() == "10.0.0.1")
    }
}
