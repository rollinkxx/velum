package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian unit murni JVM untuk [VelumFormat] — tidak memakai Android framework,
 * sehingga berjalan cepat di CI tanpa emulator (job `unitTest` di workflow build).
 */
class VelumFormatTest {

    @Test
    fun parseTrace_membacaBarisKunci() {
        val text = "fl=47f224\nip=188.114.99.7\nwarp=on\ncolo=DPS\nhttp=http/2\n"
        val t = VelumFormat.parseTrace(text)
        assertEquals("on", t.warp)
        assertEquals("DPS", t.colo)
        assertEquals("188.114.99.7", t.ip)
    }

    @Test
    fun parseTrace_kunciTakAda_jadiKosong() {
        val t = VelumFormat.parseTrace("fl=47f224\nhttp=http/2\n")
        assertEquals("", t.warp)
        assertEquals("", t.colo)
        assertEquals("", t.ip)
        assertFalse(VelumFormat.isWarpActive(t))
    }

    @Test
    fun isWarpActive_hanyaOnDanPlus() {
        assertTrue(VelumFormat.isWarpActive(VelumFormat.parseTrace("warp=on")))
        assertTrue(VelumFormat.isWarpActive(VelumFormat.parseTrace("warp=plus")))
        assertFalse(VelumFormat.isWarpActive(VelumFormat.parseTrace("warp=off")))
        assertFalse(VelumFormat.isWarpActive(VelumFormat.parseTrace("warp=")))
    }

    @Test
    fun formatBytes_skalaNaik() {
        // Pemisah desimalnya KOMA, sama dengan `formatSeconds` dan konvensi Indonesia:
        // baris `Data` dan baris `Boot` tampil berdampingan pada ringkasan yang sama.
        assertEquals("512 B", VelumFormat.formatBytes(512))
        assertEquals("1023 B", VelumFormat.formatBytes(1023))
        assertEquals("2,0 KB", VelumFormat.formatBytes(2048))
        assertEquals("5,0 MB", VelumFormat.formatBytes(5L * 1024 * 1024))
        assertEquals("3,00 GB", VelumFormat.formatBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun formatDuration_menitDanJam() {
        assertEquals("00:00", VelumFormat.formatDuration(0))
        assertEquals("01:05", VelumFormat.formatDuration(65_000))
        assertEquals("59:59", VelumFormat.formatDuration(3_599_000))
        assertEquals("1:01:01", VelumFormat.formatDuration(3_661_000))
    }

    @Test
    fun formatDuration_durasiNegatif_tidakBolehAneh() {
        // Bisa terjadi bila connectedSinceMs tertulis setelah clock berubah.
        assertEquals("00:00", VelumFormat.formatDuration(-5_000))
    }

    @Test
    fun formatClock_bentukHHmm() {
        val jam = VelumFormat.formatClock(1_700_000_000_000L)
        assertTrue("bentuk jam: $jam", jam.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun hostPart_memisahkanPort() {
        assertEquals("engage.cloudflareclient.com", VelumFormat.hostPart("engage.cloudflareclient.com:2408"))
        assertEquals("162.159.192.1", VelumFormat.hostPart("162.159.192.1:2408"))
        assertEquals("162.159.192.1", VelumFormat.hostPart("162.159.192.1"))
        assertEquals("fd00::1", VelumFormat.hostPart("[fd00::1]:2408"))
    }

    @Test
    fun hostPart_bukanHostPort_dibiarkanUtuh() {
        // Dua titik dua = kemungkinan IPv6 tanpa kurung siku; jangan dipotong sembarangan.
        assertEquals("fd00::1", VelumFormat.hostPart("fd00::1"))
    }

    @Test
    fun isUsable_menolakResponsYangBukanKeluaranTrace() {
        // Portal tawanan (captive portal) menjawab HTTP 200 dengan HTML. Tanpa penolakan
        // ini, keadaan itu dilaporkan sebagai "Belum aktif" — menuduh tunnel padahal
        // jaringannya yang meminta login lebih dulu.
        assertFalse(VelumFormat.isUsable(VelumFormat.parseTrace("<html><body>Login Wi-Fi</body></html>")))
        assertFalse(VelumFormat.isUsable(VelumFormat.parseTrace("")))
        assertFalse(VelumFormat.isUsable(VelumFormat.parseTrace("warp=")))
        // Satu bidang yang dikenal saja sudah cukup membuktikan ini keluaran trace.
        assertTrue(VelumFormat.isUsable(VelumFormat.parseTrace("warp=on")))
        assertTrue(VelumFormat.isUsable(VelumFormat.parseTrace("colo=DPS")))
        assertTrue(VelumFormat.isUsable(VelumFormat.parseTrace("ip=1.2.3.4")))
        assertTrue(VelumFormat.isUsable(VelumFormat.parseTrace("warp=off\ncolo=SIN")))
    }

    @Test
    fun isIpLiteral_hanyaIPv4() {
        assertTrue(VelumFormat.isIpLiteral("162.159.192.1"))
        assertFalse(VelumFormat.isIpLiteral("engage.cloudflareclient.com"))
        assertFalse(VelumFormat.isIpLiteral("162.159.192"))
        assertFalse(VelumFormat.isIpLiteral(""))
    }

    // ---------- ditambahkan 2026-09-13 untuk baris diagnostik baru ----------

    @Test
    fun formatSeconds_satuDesimalDenganKoma() {
        assertEquals("0,0 detik", VelumFormat.formatSeconds(0))
        assertEquals("1,0 detik", VelumFormat.formatSeconds(1_000))
        assertEquals("2,5 detik", VelumFormat.formatSeconds(2_500))
        assertEquals("14,2 detik", VelumFormat.formatSeconds(14_200))
        assertEquals("10,0 detik", VelumFormat.formatSeconds(10_000)) // batas anggaran goAsync
    }

    @Test
    fun formatSeconds_negatifDianggapNol() {
        // Jam perangkat bisa melompat; angka "-3,0 detik" di layar pengguna tidak berarti
        // apa-apa dan akan terbaca sebagai cacat.
        assertEquals("0,0 detik", VelumFormat.formatSeconds(-5_000))
    }

    @Test
    fun formatAge_satuanNaikOtomatis() {
        assertEquals("0 detik lalu", VelumFormat.formatAge(0))
        assertEquals("42 detik lalu", VelumFormat.formatAge(42))
        assertEquals("59 detik lalu", VelumFormat.formatAge(59))
        assertEquals("1 menit lalu", VelumFormat.formatAge(60))
        assertEquals("59 menit lalu", VelumFormat.formatAge(3_599))
        assertEquals("1 jam lalu", VelumFormat.formatAge(3_600))
        assertEquals("23 jam lalu", VelumFormat.formatAge(86_399))
        assertEquals("1 hari lalu", VelumFormat.formatAge(86_400))
        assertEquals("3 hari lalu", VelumFormat.formatAge(300_000))
    }

    @Test
    fun formatAge_negatifDianggapNol() {
        assertEquals("0 detik lalu", VelumFormat.formatAge(-10))
    }

}
