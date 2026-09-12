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
        assertEquals(9, teks.trim().lines().size)
    }

    @Test
    fun penyimpananPolos_dimunculkanSebagaiPeringatan() {
        val teks = VelumDiagnostics.render(contoh.copy(plaintextFallback = true))
        assertTrue(teks.contains("Peringatan  : penyimpanan TIDAK terenkripsi"))
        assertEquals(10, teks.trim().lines().size)
        // Sisi lainnya dijaga: baris itu hanya muncul bila memang perlu, supaya ringkasan
        // pada keadaan normal tidak bertambah panjang (asersi 9 baris di atas).
        assertEquals(9, VelumDiagnostics.render(contoh).trim().lines().size)
    }

    @Test
    fun catatanTidakMengklaimHalYangBisaDibantah() {
        // Regresi: dulu berbunyi "tanpa kunci, identitas perangkat, atau alamat IP" padahal
        // baris Endpoint di ringkasan yang sama memuat sebuah alamat IP.
        val teks = VelumDiagnostics.render(contoh)
        assertTrue(teks.contains("Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda"))
    }
}
