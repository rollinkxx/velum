package com.rollinkxx.velum

/**
 * Perangkai keputusan "kandidat endpoint mana yang BENAR-BENAR lolos handshake" —
 * murni: tanpa Android, tanpa GoBackend, tanpa soket, tanpa [Prefs].
 *
 * Logika ini dipisah dari [VelumController] dengan alasan yang sama seperti
 * [VelumEndpointChoice] dipisah dari [EndpointProbe]: urutan "coba kandidat berikutnya
 * sampai ada yang handshake" adalah logika yang bisa salah dengan cara yang senyap
 * (mengulangi host yang gagal, mencoba lebih dari batas kandidat sampai pengguna
 * menunggu setengah menit, atau menganggap "sudah dicoba" padahal belum). Di sini ia
 * diuji di JVM dengan handshake TIRUAN (lambda [verify]) — sukses/gagal — sehingga
 * tidak butuh perangkat, dan [VelumVerifiedChoiceTest] mengunci kontraknya.
 */
object VelumVerifiedChoice {

    /**
     * Batas kandidat yang diverifikasi per rotasi. Setiap verifikasi berarti membangun
     * ulang tunnel + menunggu handshake (anggaran detik), jadi tanpa batas ini waktu
     * penyambungan bisa menggantung jauh melebihi kesabaran pengguna.
     */
    const val MAX_CANDIDATES = 3

    /**
     * Hasil perangkaian.
     *
     * @property winner host pertama yang lolos [verify]; `null` bila semua gagal atau
     *   tidak ada kandidat yang memenuhi syarat. HANYA inilah yang boleh dicatat sebagai
     *   endpoint yang terbukti bekerja.
     * @property attempted host yang benar-benar DIUJI, dalam urutan percobaan — bukti
     *   untuk log tentang apa yang sudah diperiksa (pemanggil tidak boleh mengarang
     *   daftarnya sendiri).
     */
    data class Result(val winner: String?, val attempted: List<String>)

    /**
     * Memilih host pertama dari [ranked] (terurut tercepat dulu) yang lolos [verify].
     *
     * @param ranked kandidat terurut hasil pengukuran RTT; urutan dipakai apa adanya
     *   (RTT tetap berguna sebagai prioritas percobaan, walaupun bukan keputusan akhir).
     * @param skip host yang sedang gagal handshake — tidak pernah dicoba ulang.
     * @param maxCandidates batas percobaan; kandidat selebihnya dianggap tidak teruji.
     * @param verify uji handshake untuk satu host (di perangkat: bangun ulang tunnel
     *   dengan host itu lalu tunggu handshake GoBackend; di unit test: lambda tiruan).
     *   Duplikat dalam [ranked] hanya diverifikasi sekali.
     */
    fun pickVerified(
        ranked: List<String>,
        skip: String? = null,
        maxCandidates: Int = MAX_CANDIDATES,
        verify: (String) -> Boolean
    ): Result {
        val seen = LinkedHashSet<String>()
        val attempted = ArrayList<String>(maxCandidates.coerceAtLeast(0))
        for (host in ranked) {
            if (host == skip || !seen.add(host)) continue
            if (attempted.size >= maxCandidates) break
            attempted.add(host)
            if (verify(host)) return Result(winner = host, attempted = attempted)
        }
        return Result(winner = null, attempted = attempted)
    }
}
