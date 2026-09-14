package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menjaga kontrak header dengan upstream Cloudflare WARP.
 *
 * Header adalah satu-satunya hal yang membuat upstream mengenali klien ini: versi yang
 * terlalu tua dijawab HTTP 426/403 dan aplikasi tidak bisa registrasi sama sekali.
 * Karena header dikumpulkan sebagai satu peta ([VelumUpstream.API_HEADERS]) dan dipasang
 * apa adanya oleh `VelumApi`, menguji peta ini setara dengan menguji header yang benar-
 * benar dikirim — tanpa mock HttpURLConnection dan tanpa dependensi pengujian baru.
 */
class VelumUpstreamTest {

    @Test
    fun versiKlien_mengikutiKlienResmiSaatIni() {
        // Versi usang (a-6.10-2158) pernah dipakai dan berisiko ditolak upstream.
        assertEquals("a-6.35-4471", VelumUpstream.CLIENT_VERSION)
    }

    @Test
    fun userAgent_identitasKlienBukanBawaanLibrary() {
        assertEquals("WARP for Android", VelumUpstream.USER_AGENT)
    }

    @Test
    fun headerApi_berisiTigaHeaderYangDikirim() {
        val h = VelumUpstream.API_HEADERS
        assertEquals("WARP for Android", h["User-Agent"])
        assertEquals("a-6.35-4471", h["CF-Client-Version"])
        assertEquals("application/json", h["Accept"])
        // Lebih dari tiga header berarti ada yang menyelipkan nilai tanpa pembaruan uji.
        assertEquals(3, h.size)
    }

    @Test
    fun basisApi_httpsKeHostCloudflare() {
        assertTrue(VelumUpstream.BASE.startsWith("https://api.cloudflareclient.com/"))
    }

    @Test
    fun penolakanKlien_limaKodeDikenali() {
        for (kode in intArrayOf(401, 403, 404, 410, 426)) {
            assertTrue("HTTP $kode harus dianggap penolakan klien", VelumUpstream.isClientRejected(kode))
        }
    }

    @Test
    fun penolakanKlien_kodeLainTidakSalahTuduh() {
        // 400/408/429 = salah bentuk/timeout/membatasi laju: masih layak ditangani sebagai
        // masalah jaringan; 5xx = salah server. Hanya 5 kode di atas yang berarti
        // "klien ini tidak diterima lagi".
        for (kode in intArrayOf(200, 204, 301, 400, 408, 418, 429, 500, 502, 503)) {
            assertFalse("HTTP $kode bukan penolakan klien", VelumUpstream.isClientRejected(kode))
        }
    }

    @Test
    fun kandidatAnycast_semuaLiteralIpTanpaPort() {
        assertTrue(VelumUpstream.CANDIDATES.isNotEmpty())
        for (host in VelumUpstream.CANDIDATES) {
            assertTrue("$host harus literal IP", VelumFormat.isIpLiteral(host))
            assertFalse("$host tidak boleh berport", host.endsWith(":2408"))
        }
    }
}
