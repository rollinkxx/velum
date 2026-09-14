package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uji [VelumVerifiedChoice] — pemilih endpoint yang BENAR-BENAR lolos handshake.
 *
 * Lambda `verify` berperan sebagai GoBackend tiruan: mengembalikan true = handshake
 * simulasi berhasil, false = gagal. Dengannya seluruh kontrak diverifikasi di JVM:
 * urutan prioritas RTT, host gagal yang tidak diulang, batas kandidat, dan arti pemenang.
 */
class VelumVerifiedChoiceTest {

    private val ipA = "162.159.192.1"
    private val ipB = "162.159.193.1"
    private val ipC = "162.159.195.1"
    private val ipD = "188.114.96.1"

    /** handshakeOk memuat host yang lolos tiruan; panggilan dicatat untuk asersi urutan. */
    private fun pilih(
        ranked: List<String>,
        skip: String? = null,
        max: Int = 3,
        handshakeOk: Set<String>
    ): Pair<VelumVerifiedChoice.Result, List<String>> {
        val panggilan = mutableListOf<String>()
        val r = VelumVerifiedChoice.pickVerified(ranked, skip, max) { host ->
            panggilan.add(host)
            host in handshakeOk
        }
        return r to panggilan
    }

    @Test
    fun kandidatPertamaLolos_langsungDipilih() {
        val (r, panggilan) = pilih(listOf(ipA, ipB, ipC), handshakeOk = setOf(ipA, ipB))
        assertEquals(ipA, r.winner)
        assertEquals(listOf(ipA), panggilan)
        // Setelah ada yang lolos, kandidat lain TIDAK boleh ikut diuji.
        assertEquals(listOf(ipA), r.attempted)
    }

    @Test
    fun hanyaKandidatKetigaLolos_duaPertamaDicobaLaluMenyerahPadanya() {
        val (r, panggilan) = pilih(listOf(ipA, ipB, ipC), handshakeOk = setOf(ipC))
        assertEquals(ipC, r.winner)
        // Urutan percobaan mengikuti peringkat RTT: tercepat dicoba lebih dulu.
        assertEquals(listOf(ipA, ipB, ipC), panggilan)
    }

    @Test
    fun semuaGagal_tidakAdaPemenang_danSemuaYangDicobaTercatat() {
        val (r, _) = pilih(listOf(ipA, ipB, ipC), handshakeOk = emptySet())
        assertNull(r.winner)
        assertEquals(listOf(ipA, ipB, ipC), r.attempted)
    }

    @Test
    fun hostYangGagal_tidakPernahDicobaUlang() {
        val (r, panggilan) = pilih(listOf(ipA, ipB), skip = ipA, handshakeOk = setOf(ipB))
        assertEquals(ipB, r.winner)
        assertEquals(listOf(ipB), panggilan)
    }

    @Test
    fun semuaKandidatAdalahYangGagal_tidakAdaPercobaanSamaSekali() {
        val (r, panggilan) = pilih(listOf(ipA), skip = ipA, handshakeOk = setOf(ipA))
        assertNull(r.winner)
        assertEquals(emptyList<String>(), panggilan)
    }

    @Test
    fun batasKandidat_ditegakkanMeskiKandidatLebihBanyak() {
        // 4 kandidat terukur, tetapi hanya 3 yang boleh menyita waktu penyambungan;
        // ipD tidak pernah diuji walau ia satu-satunya yang "lolos".
        val (r, panggilan) = pilih(listOf(ipA, ipB, ipC, ipD), max = 3, handshakeOk = setOf(ipD))
        assertNull(r.winner)
        assertEquals(listOf(ipA, ipB, ipC), panggilan)
    }

    @Test
    fun duplikatDalamPeringkat_hanyaDiverifikasiSekali() {
        // Host yang sama bisa muncul dua kali (mis. endpoint registrasi kebetulan sama
        // dengan kandidat anycast); menguji dua kali membuang anggaran handshake.
        val (r, panggilan) = pilih(listOf(ipA, ipA, ipB), handshakeOk = setOf(ipB))
        assertEquals(ipB, r.winner)
        assertEquals(listOf(ipA, ipB), panggilan)
    }
}
