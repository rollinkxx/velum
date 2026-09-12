package com.rollinkxx.velum

/**
 * Hasil satu uji trace yang sudah final.
 *
 * Murni tanpa Android framework supaya bisa diuji unit, disandikan sebagai satu baris teks
 * di [Prefs] (hasil uji bertahan lintas restart/pembuatan ulang layar), dan diterjemahkan
 * ke teks oleh UI — jadi hanya ada satu tempat penerjemahan.
 */
data class VelumTestResult(
    val kind: Kind,
    val atEpochMs: Long,
    val colo: String? = null,
    val detail: String? = null
) {
    enum class Kind {
        /** Terbukti lewat Velum (`warp=on`/`plus`). */
        ACTIVE,

        /** Uji berhasil, tetapi jalurnya bukan WARP. */
        OFF,

        /**
         * Tunnel UP tetapi belum ada handshake, sehingga permintaan uji tidak pernah keluar.
         *
         * Ini **bukan** kegagalan jaringan biasa: yang terlihat di lapangan justru galat DNS
         * ("Unable to resolve host ...") karena DNS di dalam tunnel juga tidak bisa dilewati.
         * Dilaporkan apa adanya supaya pengguna tidak disuruh memeriksa jaringan padahal
         * masalahnya di endpoint WARP yang tidak meneruskan data.
         */
        NO_DATA,

        /** Handshake sudah terjadi, tetapi permintaan uji gagal (jaringan/HTTP). */
        FAILED
    }

    /** Penyandian satu baris; `detail` diletakkan terakhir agar boleh memuat pemisah. */
    fun encode(): String = listOf(
        kind.name, atEpochMs.toString(), colo.orEmpty(), detail.orEmpty()
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        fun decode(raw: String?): VelumTestResult? {
            val parts = raw?.split(SEPARATOR, limit = 4) ?: return null
            if (parts.size < 2) return null
            val kind = Kind.values().firstOrNull { it.name == parts[0] } ?: return null
            val at = parts[1].toLongOrNull() ?: return null
            return VelumTestResult(
                kind = kind,
                atEpochMs = at,
                colo = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
                detail = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Menerjemahkan hasil mentah uji menjadi [VelumTestResult].
         *
         * Urutannya disengaja: keadaan "belum ada handshake" diperiksa **sebelum** `error`,
         * karena galat yang muncul pada keadaan itu hanyalah gejala (biasanya DNS) dan
         * menyebutnya "kesalahan jaringan" menyesatkan pengguna.
         */
        fun of(
            handshakeReady: Boolean,
            trace: VelumFormat.TraceInfo?,
            error: String?,
            atEpochMs: Long
        ): VelumTestResult = when {
            !handshakeReady -> VelumTestResult(Kind.NO_DATA, atEpochMs)
            error != null -> VelumTestResult(Kind.FAILED, atEpochMs, detail = error)
            trace != null && VelumFormat.isWarpActive(trace) ->
                VelumTestResult(Kind.ACTIVE, atEpochMs, colo = trace.colo.ifEmpty { "?" })
            trace != null -> VelumTestResult(Kind.OFF, atEpochMs)
            else -> VelumTestResult(Kind.NO_DATA, atEpochMs)
        }
    }
}
