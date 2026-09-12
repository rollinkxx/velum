package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VelumDiagnosticsTest {

    private val contoh = VelumDiagnostics.Snapshot(
        appVersion = "0.1.0",
        state = "Tersambung",
        endpoint = "162.159.192.1:2408",
        handshakeAgeSec = 42,
        rxBytes = 1_258_291,
        txBytes = 246_272,
        connectedSec = 83,
        lastTest = "Aktif · DC SIN · 15:25"
    )

    @Test
    fun ringkasanMemuatKeadaanUtama() {
        val teks = VelumDiagnostics.render(contoh)
        assertTrue(teks.contains("Velum 0.1.0"))
        assertTrue(teks.contains("Status      : Tersambung"))
        assertTrue(teks.contains("Endpoint    : 162.159.192.1:2408"))
        assertTrue(teks.contains("Handshake   : 42 detik lalu"))
        assertTrue(teks.contains("Durasi      : 01:23"))
        assertTrue(teks.contains("Uji terakhir: Aktif · DC SIN · 15:25"))
    }

    @Test
    fun tanpaHasilUji_ditulisBelumAda() {
        val teks = VelumDiagnostics.render(contoh.copy(lastTest = null))
        assertTrue(teks.contains("Uji terakhir: belum ada"))
    }

    @Test
    fun tanpaHandshake_ditulisBukanAngkaAneh() {
        val teks = VelumDiagnostics.render(contoh.copy(handshakeAgeSec = null))
        assertTrue(teks.contains("Handshake   : belum ada"))
    }

    @Test
    fun endpointKosong_ditulisStrip() {
        val teks = VelumDiagnostics.render(contoh.copy(endpoint = null))
        assertTrue(teks.contains("Endpoint    : -"))
    }

    @Test
    fun ringkasanTidakPernahMemuatRahasia() {
        // Jaga-jaga bila kelak ada yang menambahkan kunci/token/IP ke snapshot.
        val teks = VelumDiagnostics.render(
            contoh.copy(endpoint = "engage.cloudflareclient.com:2408", excludedApps = listOf("com.a", "com.b"))
        )
        assertFalse(teks.contains("private_key"))
        assertFalse(teks.contains("token"))
        assertFalse(teks.contains("device_id"))
        assertTrue(teks.contains("Dikecualikan: 2 aplikasi"))
    }

    @Test
    fun daftarKosong_ditulisTidakAda() {
        val teks = VelumDiagnostics.render(contoh)
        assertTrue(teks.contains("Dikecualikan: tidak ada"))
    }

    @Test
    fun durasiNolMasukAkal() {
        val teks = VelumDiagnostics.render(contoh.copy(connectedSec = 0))
        assertTrue(teks.contains("Durasi      : 00:00"))
        // 13 baris = 9 baris semula + 4 baris keadaan internal (Niat, Pemantau, Proses,
        // Boot) yang ditambahkan 2026-09-13 atas persetujuan maintainer, karena pengujian
        // dilakukan di perangkat tanpa adb. Angka ini memang HARUS diperbarui oleh fitur
        // tersebut; yang dijaga tetap adalah maksud aslinya — baris Peringatan hanya muncul
        // bila perlu (lihat uji di bawah).
        assertEquals(13, teks.trim().lines().size)
    }

    @Test
    fun penyimpananPolos_dimunculkanSebagaiPeringatan() {
        val teks = VelumDiagnostics.render(contoh.copy(plaintextFallback = true))
        assertTrue(teks.contains("Peringatan  : penyimpanan TIDAK terenkripsi"))
        // Maksud asli uji ini dipertahankan lewat SELISIH, bukan angka mutlak: baris
        // Peringatan tepat satu baris lebih panjang daripada ringkasan normal, dan hanya
        // muncul bila memang perlu. Angka mutlaknya (14 dan 13) mengikuti penambahan empat
        // baris keadaan internal 2026-09-13.
        assertEquals(14, teks.trim().lines().size)
        assertEquals(13, VelumDiagnostics.render(contoh).trim().lines().size)
        assertEquals(1, teks.trim().lines().size - VelumDiagnostics.render(contoh).trim().lines().size)
    }

    // ---------- keadaan internal (ditambahkan 2026-09-13; maintainer menguji tanpa adb) ----------

    /** Ambil satu baris ringkasan berdasarkan awalan labelnya, untuk asersi yang persis. */
    private fun baris(teks: String, label: String): String =
        teks.lines().first { it.startsWith(label) }

    private fun barisBoot(teks: String): String = baris(teks, "Boot")

    @Test
    fun barisNiat_membedakanHidupDanMati() {
        // Inilah baris yang membuat kebocoran niat terlihat tanpa logcat: Status "Terputus"
        // sementara Niat "Hidup" berarti sesuatu akan menyambungkannya lagi tanpa diminta.
        assertTrue(VelumDiagnostics.render(contoh.copy(wasUp = true, intentGen = 7))
            .contains("Niat        : Hidup · aksi ke-7"))
        assertTrue(VelumDiagnostics.render(contoh.copy(wasUp = false, intentGen = 0))
            .contains("Niat        : Mati · aksi ke-0"))
    }

    @Test
    fun barisPemantau_hidupDanMati() {
        assertTrue(VelumDiagnostics.render(contoh.copy(monitorActive = true))
            .contains("Pemantau    : aktif"))
        assertTrue(VelumDiagnostics.render(contoh.copy(monitorActive = false))
            .contains("Pemantau    : mati"))
    }

    @Test
    fun barisProses_memakaiFormatDurasiYangSudahAda() {
        assertTrue(VelumDiagnostics.render(contoh.copy(processAgeSec = 83))
            .contains("Proses      : hidup 01:23"))
        assertTrue(VelumDiagnostics.render(contoh.copy(processAgeSec = 3_661))
            .contains("Proses      : hidup 1:01:01"))
    }

    @Test
    fun barisBoot_tanpaRekaman_ditulisBelumAda() {
        assertTrue(VelumDiagnostics.render(contoh)
            .contains("Boot        : belum ada percobaan"))
    }

    @Test
    fun barisBoot_menampilkanDurasiHasilDanUmur() {
        val boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_OK, 14_200L, 1_000_000L)
        val teks = VelumDiagnostics.render(contoh.copy(boot = boot, nowEpochMs = 1_000_000L + 7_200_000L))
        // Angka 14,2 detik inilah yang menjawab "apakah goAsync() melewati anggaran 10 detik"
        // (uji F2) tanpa perlu adb.
        assertTrue(teks.contains("Boot        : 14,2 detik · berhasil · 2 jam lalu"))
    }

    @Test
    fun barisBoot_gagalDitulisHurufBesarSupayaTidakTerlewat() {
        // 2.500 ms dipakai karena 2,5 tepat terwakili sebagai double, jadi pembulatannya
        // tidak bergantung mode pembulatan. (2.050 ms SENGAJA tidak dipakai: double-nya
        // 2,0499… sehingga "%.1f" menghasilkan "2,0" — asersi awal di uji ini salah dan
        // ketahuan dari simulasi, bukan dari CI.)
        val boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_FAIL, 2_500L, 5_000L)
        val teks = VelumDiagnostics.render(contoh.copy(boot = boot, nowEpochMs = 5_000L))
        assertEquals("Boot        : 2,5 detik · GAGAL · 0 detik lalu", barisBoot(teks))
    }

    @Test
    fun barisBoot_tanpaIzinVpn_menjelaskanKenapaTidakMenyambung() {
        val boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_NO_VPN, 0L, 5_000L)
        assertTrue(VelumDiagnostics.render(contoh.copy(boot = boot, nowEpochMs = 5_000L))
            .contains("Boot        : 0,0 detik · dilewati (izin VPN tidak ada) · 0 detik lalu"))
    }

    @Test
    fun barisBoot_tanpaWaktuSekarang_tidakMenebakUmur() {
        // nowEpochMs = 0 berarti pemanggil tidak menyediakannya; lebih baik tidak menulis
        // umur sama sekali daripada menulis "56 tahun lalu" dari epoch 1970.
        val boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_OK, 1_000L, 5_000L)
        val teks = VelumDiagnostics.render(contoh.copy(boot = boot, nowEpochMs = 0L))
        assertEquals("Boot        : 1,0 detik · berhasil", barisBoot(teks))
    }

    @Test
    fun barisBoot_rekamanDariMasaDepan_tidakDihitungUmurnya() {
        // Jam perangkat bisa melompat maju/mundur; rekaman yang "lebih baru" daripada
        // sekarang tidak boleh menghasilkan umur negatif.
        val boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_OK, 1_000L, 9_000L)
        val teks = VelumDiagnostics.render(contoh.copy(boot = boot, nowEpochMs = 5_000L))
        assertEquals("Boot        : 1,0 detik · berhasil", barisBoot(teks))
    }

    @Test
    fun encodeDecodeBoot_pulangPergi() {
        val asli = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_FAIL, 12_345L, 1_700_000_000_000L)
        val teks = VelumDiagnostics.encodeBoot(asli)
        assertEquals("gagal|12345|1700000000000", teks)
        assertEquals(asli, VelumDiagnostics.decodeBoot(teks))
    }

    @Test
    fun decodeBoot_masukanCacat_mengembalikanNullBukanMelempar() {
        // Nilai ini dibaca dari penyimpanan yang bisa berasal dari versi lama atau berkas
        // rusak. Diagnostik yang crash justru menghilangkan alat untuk mendiagnosis.
        assertEquals(null, VelumDiagnostics.decodeBoot(null))
        assertEquals(null, VelumDiagnostics.decodeBoot(""))
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|1000"))           // bidang kurang
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|1000|5|x"))       // bidang lebih
        assertEquals(null, VelumDiagnostics.decodeBoot("mungkin|1000|5"))    // outcome tak dikenal
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|abc|5"))          // bukan angka
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|1000|xyz"))
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|-1|5"))           // durasi negatif
        assertEquals(null, VelumDiagnostics.decodeBoot("ok|1000|0"))         // waktu tak sah
    }

    @Test
    fun keadaanInternalTidakMembawaRahasia() {
        // Baris baru hanya boolean, angka generasi, dan durasi. Uji ini menjaga agar
        // penambahan berikutnya tidak menyelundupkan pengenal pengguna ke ringkasan.
        val teks = VelumDiagnostics.render(
            contoh.copy(
                wasUp = true, intentGen = 3, monitorActive = true, processAgeSec = 10,
                boot = VelumDiagnostics.Boot(VelumDiagnostics.BOOT_OK, 1_000L, 5_000L),
                nowEpochMs = 9_000L
            )
        )
        assertFalse(teks.contains("private_key"))
        assertFalse(teks.contains("token"))
        assertFalse(teks.contains("device_id"))
        // Nama paket aplikasi yang dikecualikan pun tidak boleh ikut ke baris baru.
        assertFalse(teks.contains("com.rollinkxx"))
    }

    @Test
    fun catatanTidakMengklaimHalYangBisaDibantah() {
        // Regresi: dulu berbunyi "tanpa kunci, identitas perangkat, atau alamat IP" padahal
        // baris Endpoint di ringkasan yang sama memuat sebuah alamat IP.
        val teks = VelumDiagnostics.render(contoh)
        assertTrue(teks.contains("Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda"))
    }
}
