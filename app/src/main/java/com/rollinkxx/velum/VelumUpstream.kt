package com.rollinkxx.velum

/**
 * Konstanta yang mengikuti layanan upstream (Cloudflare WARP) — sengaja dikumpulkan
 * di satu berkas. Bila upstream mengubah versi API, endpoint, atau rentang anycast,
 * cukup satu tempat yang disentuh dan risikonya terlihat jelas saat review.
 *
 * Catatan: nilai-nilai ini berada di luar kendali aplikasi. Bila upstream menolak
 * klien ini (lihat [isClientRejected]), aplikasi menampilkan pesan yang menyuruh
 * pengguna memeriksa pembaruan, bukan sekadar "gagal".
 */
object VelumUpstream {

    /** Basis API registrasi perangkat (gaya wgcf). */
    const val BASE = "https://api.cloudflareclient.com/v0a2158"

    /**
     * Versi klien yang dikirim lewat header `CF-Client-Version`.
     *
     * Nilai sebelumnya (`a-6.10-2158`, aplikasi Android era 2022) berisiko ditolak
     * upstream dengan HTTP 426/403: Cloudflare menghentikan penerimaan versi yang
     * terlalu tua. Nilai ini mengikuti yang dikirim klien resmi saat ini (referensi:
     * proyek wgcf yang memakai model header yang sama).
     */
    // TODO: refresh header version periodically — selaraskan dengan klien resmi/wgcf
    //  begitu isClientRejected mulai terlihat di lapangan (HTTP 403/426).
    const val CLIENT_VERSION = "a-6.35-4471"

    /**
     * User-Agent yang diharapkan API registrasi. Sebelumnya `okhttp/3.12.1` —
     * nilai bawaan library, bukan identitas klien, dan mudah dipetakan sebagai
     * klien usang oleh upstream.
     */
    const val USER_AGENT = "WARP for Android"

    /**
     * Header yang wajib menyertai setiap pemanggilan API registrasi.
     *
     * Dikumpulkan sebagai satu peta (bukan `setRequestProperty` yang tersebar) supaya
     * apa yang benar-benar dikirim bisa diasertakan unit test tanpa mock HTTP: mengubah
     * satu nilai di sini mengubah seluruh pemanggilan, dan `VelumUpstreamTest` menjaga
     * ketiganya tetap seperti yang diharapkan upstream.
     */
    val API_HEADERS: Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "CF-Client-Version" to CLIENT_VERSION,
        "Accept" to "application/json"
    )

    /** Endpoint cadangan bila registrasi tidak mengembalikan endpoint. */
    const val DEFAULT_ENDPOINT = "engage.cloudflareclient.com:2408"

    /** Kandidat anycast WARP yang dikenal (IPv4), dipakai `EndpointProbe`. */
    val CANDIDATES: List<String> = listOf(
        "162.159.192.1",
        "162.159.193.1",
        "162.159.195.1",
        "188.114.96.1",
        "188.114.97.1",
        "188.114.98.1",
        "188.114.99.1"
    )

    /**
     * Kode HTTP yang berarti klien ini tidak lagi diterima layanan: mengulang
     * permintaan tidak ada gunanya, yang dibutuhkan pembaruan aplikasi.
     */
    fun isClientRejected(code: Int): Boolean =
        code == 401 || code == 403 || code == 404 || code == 410 || code == 426
}
