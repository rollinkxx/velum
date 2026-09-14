package com.rollinkxx.velum

import java.io.IOException

/**
 * Android Keystore / `EncryptedSharedPreferences` tidak bisa dibuka, sehingga tidak ada
 * tempat yang aman untuk menyimpan kunci privat.
 *
 * Keadaan ini sengaja dibuat FATAL bagi penyimpanan (bukan diturunkan ke berkas polos):
 * kunci privat WireGuard dan token registrasi tidak boleh pernah tertulis tanpa
 * enkripsi. Pemanggil di UI menampilkan dialog "Penyimpanan aman tidak tersedia.
 * Daftar ulang diperlukan."; pemanggil di latar (receiver boot, pemantau jaringan,
 * ubin) berhenti tanpa bertindak.
 *
 * Turunan [IOException] supaya jalur yang sudah menelan kegagalan I/O tidak meledak,
 * tetapi [VelumError.kindOf] mengenalinya lebih dulu sebagai kategori tersendiri
 * ([VelumError.Kind.KEYSTORE]) agar pesannya tidak disalahartikan sebagai gangguan
 * jaringan.
 */
class KeystoreUnavailableException(cause: Throwable?) : IOException(
    "Android Keystore tidak tersedia; kunci privat tidak bisa disimpan terenkripsi",
    cause
)
