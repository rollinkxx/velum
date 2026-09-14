package com.rollinkxx.velum

import android.util.Log

/**
 * Pembungkus tipis `android.util.Log` dengan dua tujuan nyata:
 *
 * 1. **Level sensitif mati total di build yang dibagikan.** [d] dan [i] hanya menyala
 *    bila `BuildConfig.DEBUG` true; pada release/preview keduanya no-op sebelum pesan
 *    sempat dirangkai ke platform. Data yang kini diletakkan di level ini — alamat IP
 *    endpoint hasil proba, host yang terbukti handshake, peristiwa sesi — tidak boleh
 *    bocor ke logcat perangkat pengguna (logcat bisa dibaca aplikasi lain dengan izin
 *    khusus dan oleh siapa pun yang memegang perangkat dengan adb).
 * 2. **Bisa diuji di JVM.** `android.util.Log` tidak ada di unit test JVM, jadi tujuan
 *    akhir ([sink]) dapat diganti dengan penadah buatan; aturan mati/hidupnya diuji di
 *    [VelumLogTest] tanpa perangkat.
 *
 * [w] dan [e] menyala di SEMUA build: keduanya hanya dipakai untuk kegagalan yang tidak
 * memuat data sensitif, dan maintainer menguji di perangkat tanpa adb — mematikan satu-
 * satunya jejak kegagalan di build rilis akan membuat diagnosis mustahil. Ini penyimpangan
 * sadar dari kaidah populer "hapus semua log di rilis"; pelengkapnya, `proguard-rules.pro`
 * tetap membuang `Log.d`/`Log.v` pada varian yang diperkecil sebagai lapis kedua.
 */
object VelumLog {

    internal const val LEVEL_D = 3
    internal const val LEVEL_I = 4
    internal const val LEVEL_W = 5
    internal const val LEVEL_E = 6

    /**
     * Apakah level sensitif ([d]/[i]) menyala — bawaan: hanya build debug.
     * `internal` supaya unit test dapat membalikkannya tanpa refleksi.
     */
    @Volatile
    internal var verboseEnabled: Boolean = BuildConfig.DEBUG

    /**
     * Tujuan akhir setiap pesan. Bawaannya platform `android.util.Log`; unit test
     * menggantinya dengan penadah (JVM tidak punya `android.util.Log`, dan tidak akan
     * pernah mengeksekusi bawaan ini selama diganti lebih dulu).
     */
    internal var sink: (level: Int, tag: String, message: String, tr: Throwable?) -> Unit =
        { level, tag, message, tr -> platformLog(level, tag, message, tr) }

    private fun platformLog(level: Int, tag: String, message: String, tr: Throwable?) {
        when (level) {
            LEVEL_D -> if (tr == null) Log.d(tag, message) else Log.d(tag, message, tr)
            LEVEL_I -> if (tr == null) Log.i(tag, message) else Log.i(tag, message, tr)
            LEVEL_W -> if (tr == null) Log.w(tag, message) else Log.w(tag, message, tr)
            else -> if (tr == null) Log.e(tag, message) else Log.e(tag, message, tr)
        }
    }

    /** Debug — detail diagnostik (boleh memuat endpoint/IP); mati di build non-debug. */
    fun d(tag: String, message: String, tr: Throwable? = null) {
        if (verboseEnabled) sink(LEVEL_D, tag, message, tr)
    }

    /** Info — peristiwa umum aplikasi; mati di build non-debug. */
    fun i(tag: String, message: String, tr: Throwable? = null) {
        if (verboseEnabled) sink(LEVEL_I, tag, message, tr)
    }

    /** Peringatan — kegagalan nyata tanpa data sensitif; menyala di semua build. */
    fun w(tag: String, message: String, tr: Throwable? = null) {
        sink(LEVEL_W, tag, message, tr)
    }

    /** Galat — kegagalan serius tanpa data sensitif; menyala di semua build. */
    fun e(tag: String, message: String, tr: Throwable? = null) {
        sink(LEVEL_E, tag, message, tr)
    }
}
