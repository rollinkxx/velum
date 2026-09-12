package com.rollinkxx.velum

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Padding bilah sistem untuk tampilan edge-to-edge.
 *
 * Sejak `targetSdk` 36 (Android 16), aplikasi **wajib** menggambar sampai ke tepi
 * layar: isi jendela berada di bawah bilah status dan bilah navigasi. Tanpa
 * penyesuaian, judul dapat tertutup bilah status dan tombol "Simpan" tertutup
 * bilah navigasi gestur.
 *
 * Pada perangkat lama (atau jendela yang tidak edge-to-edge) sistem sudah
 * menyisihkan ruang itu dan insets yang diterima bernilai nol, sehingga padding
 * yang dipasang di sini tidak menggandakan jarak. Karena itu cara ini aman untuk
 * rentang minSdk 24 hingga versi terbaru — tanpa perlu memeriksa versi Android.
 */
object VelumInsets {

    /** Terapkan insets bilah sistem sebagai padding pada akar layout layar. */
    fun applySystemBars(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // Minta insets dikirim ulang: saat onCreate, jendela belum tentu terpasang.
        ViewCompat.requestApplyInsets(root)
    }
}
