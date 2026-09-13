package com.rollinkxx.velum

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Penyimpanan data registrasi. Terenkripsi (AndroidX Security + Tink) dengan
 * fallback polos bila keystore perangkat gagal; data era polos dimigrasi sekali.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = open(context.applicationContext)

    var privateKey: String?
        get() = sp.getString(K_PRIV, null)
        set(v) = sp.edit().putString(K_PRIV, v).apply()

    var deviceId: String?
        get() = sp.getString(K_ID, null)
        set(v) = sp.edit().putString(K_ID, v).apply()

    var token: String?
        get() = sp.getString(K_TOKEN, null)
        set(v) = sp.edit().putString(K_TOKEN, v).apply()

    var addressV4: String?
        get() = sp.getString(K_V4, null)
        set(v) = sp.edit().putString(K_V4, v).apply()

    var addressV6: String?
        get() = sp.getString(K_V6, null)
        set(v) = sp.edit().putString(K_V6, v).apply()

    var peerPublicKey: String?
        get() = sp.getString(K_PEER, null)
        set(v) = sp.edit().putString(K_PEER, v).apply()

    var endpoint: String?
        get() = sp.getString(K_ENDPOINT, null)
        set(v) = sp.edit().putString(K_ENDPOINT, v).apply()

    /** Endpoint tercepat hasil proba (null = pakai endpoint registrasi). */
    var speedEndpoint: String?
        get() = sp.getString(K_SPEED_EP, null)
        set(v) = sp.edit().putString(K_SPEED_EP, v).apply()

    /** Waktu proba terakhir (epoch ms); basi setelah 1 jam. */
    var speedEndpointAt: Long
        get() = sp.getLong(K_SPEED_AT, 0L)
        set(v) = sp.edit().putLong(K_SPEED_AT, v).apply()

    /**
     * Endpoint yang **terbukti** menghasilkan handshake di perangkat ini.
     *
     * Berbeda dari [speedEndpoint] yang hanya perkiraan urutan RTT, nilai ini bukti nyata;
     * karena itu diutamakan (bukti > perkiraan). Dikosongkan oleh [EndpointProbe.rotate]
     * ketika endpoint tersebut justru gagal handshake.
     */
    var workingEndpoint: String?
        get() = sp.getString(K_WORKING_EP, null)
        set(v) = sp.edit().putString(K_WORKING_EP, v).apply()

    /** Hasil uji trace terakhir (`null` = belum pernah diuji). Bertahan lintas restart. */
    var lastTest: VelumTestResult?
        get() = VelumTestResult.decode(sp.getString(K_LAST_TEST, null))
        set(v) = sp.edit().putString(K_LAST_TEST, v?.encode()).apply()

    /** Endpoint efektif: yang terbukti bekerja, lalu hasil proba, lalu endpoint registrasi. */
    val effectiveEndpoint: String?
        get() = workingEndpoint ?: speedEndpoint ?: endpoint

    /** Paket aplikasi yang dikecualikan dari tunnel (split tunneling). */
    var excludedApps: Set<String>
        get() = sp.getStringSet(K_EXCLUDED, null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(K_EXCLUDED, v).apply()

    /** Memo: akun terkonfirmasi memakai flag WARP penuh (set oleh registrasi/ensure). */
    var warpEnabled: Boolean
        get() = sp.getBoolean(K_WARP, false)
        set(v) = sp.edit().putBoolean(K_WARP, v).apply()

    /** Memo: terakhir kali tunnel memang UP (untuk sambung ulang saat boot). */
    var wasUp: Boolean
        get() = sp.getBoolean(K_WAS_UP, false)
        set(v) = sp.edit().putBoolean(K_WAS_UP, v).apply()

    /**
     * Rekaman percobaan sambung ulang otomatis terakhir oleh [BootReceiver], dalam format
     * [VelumDiagnostics.encodeBoot] (`outcome|durationMs|atEpochMs`); null bila belum pernah.
     *
     * Angka inilah satu-satunya bukti berapa lama boot menghabiskan anggaran `goAsync()`,
     * dan maintainer membacanya dari layar diagnostik karena tidak punya adb.
     */
    val bootRecord: String?
        get() = sp.getString(K_BOOT, null)

    /**
     * Tulis rekaman boot secara **durabel** (`commit()`).
     *
     * HANYA boleh dipanggil dari thread latar: `commit()` menulis ke disk secara sinkron.
     * Dipakai [BootReceiver] di dalam thread pekerjaannya, tepat sebelum
     * `PendingResult.finish()` — sesudah itu proses boleh dibunuh sistem kapan saja, dan
     * `apply()` yang masih mengantre di memori bisa hilang bersama angkanya.
     */
    @SuppressLint("ApplySharedPref")
    fun writeBootRecordDurable(value: String) {
        sp.edit().putString(K_BOOT, value).commit()
    }

    /**
     * Tulis rekaman boot tanpa menahan thread (`apply()`).
     *
     * Dipakai pada jalur [BootReceiver] yang berjalan di **main thread** (persetujuan VPN
     * hilang, tidak ada pekerjaan latar). Rekaman ini murni diagnostik, dan menulis sinkron
     * di dalam receiver yang berjalan di main thread adalah biaya yang tidak sepadan untuk
     * satu baris teks. Konsekuensi yang diterima sadar: bila proses dibunuh sebelum tulisan
     * mendarat, rekaman hilang dan baris `Boot` menunjukkan percobaan sebelumnya atau
     * "belum ada percobaan" — membingungkan, tetapi tidak merusak apa pun.
     */
    fun writeBootRecord(value: String) {
        sp.edit().putString(K_BOOT, value).apply()
    }

    /** Registrasi dianggap lengkap bila semua bidang inti tersedia. */
    val isRegistered: Boolean
        get() = !privateKey.isNullOrEmpty() && !addressV4.isNullOrEmpty() &&
            !peerPublicKey.isNullOrEmpty() && !endpoint.isNullOrEmpty()

    /**
     * Apakah penyimpanan jatuh ke berkas POLOS karena keystore perangkat gagal.
     *
     * Fallback itu sengaja ada dan menyelamatkan aplikasi dari tidak bisa dipakai sama
     * sekali, tetapi konsekuensinya nyata: kunci privat dan token tersimpan TANPA enkripsi.
     * Pengguna berhak tahu, jadi keadaannya ikut dilaporkan di diagnostik — sebelumnya
     * hanya tercatat di logcat yang tidak dibaca siapa pun.
     */
    val isPlainFallback: Boolean
        get() = plainFallback

    /**
     * Menulis seluruh hasil registrasi dalam SATU transaksi.
     *
     * Sebelumnya ketujuh bidang ditulis satu per satu lewat `apply()`. Proses yang mati di
     * tengah penulisan meninggalkan campuran kunci privat BARU dengan endpoint/peer LAMA,
     * sementara [isRegistered] tetap `true` karena semua bidang terisi — akibatnya
     * handshake gagal terus-menerus tanpa pesan yang menunjuk penyebabnya, dan satu-satunya
     * jalan keluar adalah pengguna menemukan tombol "Daftar ulang" sendiri.
     *
     * `commit()` dipakai sengaja (bukan `apply()`): SharedPreferences menulis ke berkas
     * sementara lalu mengganti namanya, sehingga satu `commit` bersifat atomik terhadap
     * proses yang mati — seluruh bidang masuk, atau tidak sama sekali.
     */
    @SuppressLint("ApplySharedPref")
    fun saveRegistration(r: VelumRegistration.Result, privateKeyBase64: String) {
        sp.edit()
            .putString(K_PRIV, privateKeyBase64)
            .putString(K_ID, r.id)
            .putString(K_TOKEN, r.token)
            .putString(K_V4, r.addressV4)
            // null di sini menghapus kunci lama: alamat IPv6 sesi sebelumnya tidak boleh
            // tertinggal menempel pada kunci yang baru.
            .putString(K_V6, r.addressV6)
            .putString(K_PEER, r.peerPublicKey)
            .putString(K_ENDPOINT, r.endpoint)
            .putBoolean(K_WARP, true) // body registrasi memang meminta warp_enabled
            .commit()
    }

    /**
     * Membersihkan data registrasi.
     *
     * Dua hal sengaja DIPERTAHANKAN karena keduanya bukan bagian dari registrasi:
     * - [wasUp]: niat pengguna untuk tersambung saat boot;
     * - [excludedApps]: pilihan split tunneling milik pengguna. Sebelumnya ikut terhapus,
     *   sehingga menekan "Daftar ulang" diam-diam menghapus daftar pengecualian yang sudah
     *   disusun pengguna — dan dialog konfirmasinya tidak mengatakan itu.
     *
     * Semuanya ditulis dalam SATU transaksi (`clear()` + kedua `put` + `commit()`), bukan
     * tiga tulisan terpisah seperti sebelumnya: proses yang mati di antara `clear()` dan
     * penulisan ulang akan menghapus niat dan pengecualian pengguna — persis kelas
     * kegagalan yang [saveRegistration] tutup dengan `commit()`.
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        val keepUp = wasUp
        val keepExcluded = excludedApps
        val ed = sp.edit().clear()
        if (keepUp) ed.putBoolean(K_WAS_UP, true)
        if (keepExcluded.isNotEmpty()) ed.putStringSet(K_EXCLUDED, keepExcluded)
        ed.commit()
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        /** Terisi bila `open()` jatuh ke penyimpanan polos; dibaca lewat [isPlainFallback]. */
        @Volatile
        private var plainFallback = false

        /**
         * Satu instance per proses. Membuka prefs terenkripsi itu mahal (baca + dekripsi
         * seluruh nilai untuk pengecekan migrasi), sehingga dipakai bersama oleh UI,
         * [ReconnectMonitor], dan [BootReceiver].
         */
        fun of(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }

        const val TAG = "Velum"
        const val FILE = "velum"
        /**
         * Berkas cadangan bila keystore perangkat gagal. SENGAJA berbeda nama dari [FILE]
         * supaya store terenkripsi dan store polos tidak pernah berbagi satu berkas —
         * lihat penjelasan di `open()`.
         */
        const val FILE_PLAIN = "velum_plain"
        const val LEGACY_FILE = "warp"
        const val K_PRIV = "private_key"
        const val K_ID = "device_id"
        const val K_TOKEN = "token"
        const val K_V4 = "addr_v4"
        const val K_V6 = "addr_v6"
        const val K_PEER = "peer_pub"
        const val K_ENDPOINT = "endpoint"
        const val K_SPEED_EP = "speed_ep"
        const val K_SPEED_AT = "speed_at"
        const val K_WORKING_EP = "working_ep"
        const val K_LAST_TEST = "last_test"
        const val K_WARP = "warp_enabled"
        const val K_WAS_UP = "was_up"
        const val K_BOOT = "boot_last"
        const val K_EXCLUDED = "excluded_apps"

        private fun open(ctx: Context): SharedPreferences {
            val encrypted = try {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    ctx,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "prefs terenkripsi gagal, fallback polos", e)
                null
            }
            if (encrypted == null) {
                // Dicatat agar bisa dilaporkan ke pengguna lewat diagnostik, bukan hanya
                // ke logcat: kunci privat kini tersimpan tanpa enkripsi.
                plainFallback = true
                // NAMA BERKAS SENGAJA BERBEDA dari store terenkripsi. Sebelumnya fallback
                // ini memakai `FILE` yang sama, dan itu rusak dua arah:
                // - `EncryptedSharedPreferences` mengenkripsi NAMA kunci juga
                //   (`PrefKeyEncryptionScheme.AES256_SIV`), jadi pembacaan polos atas
                //   berkas terenkripsi melihat ciphertext di bawah nama ciphertext —
                //   `getString("private_key")` null, `isRegistered` false, dan pengguna
                //   dipaksa daftar ulang padahal datanya masih ada di berkas itu;
                // - tulisan polos berikutnya lalu bercampur ke berkas yang sama, sehingga
                //   bila keystore pulih, `EncryptedSharedPreferences.create` harus
                //   mendekripsi berkas berisi entri polos → gagal lagi → fallback lagi.
                // Konsekuensi yang diterima (dan dikorbankan secara sadar): perangkat yang
                // SUDAH terlanjur jatuh ke fallback sebelum perubahan ini kehilangan data
                // polosnya di berkas lama dan perlu daftar ulang SEKALI. Tidak ada migrasi
                // heuristik dari berkas lama, karena membedakan "berkas polos era lama"
                // dari "berkas terenkripsi" berarti menebak — dan tebakan yang salah di
                // sini merusak data yang sebenarnya masih bisa dibaca.
                Log.w(TAG, "penyimpanan polos dipakai: $FILE_PLAIN (kunci privat TIDAK terenkripsi)")
                return ctx.getSharedPreferences(FILE_PLAIN, Context.MODE_PRIVATE)
            }
            migrateLegacy(ctx, encrypted)
            return encrypted
        }

        /** Keberadaan berkas era lama, tanpa membuka/dekripsi isinya. */
        private fun legacyFileExists(ctx: Context): Boolean = try {
            File(File(ctx.applicationInfo.dataDir, "shared_prefs"), "$LEGACY_FILE.xml").exists()
        } catch (_: Exception) {
            false
        }

        /**
         * Menyalin sekali data era polos (file "warp") ke penyimpanan terenkripsi.
         * `commit()` dipakai sengaja: kita harus tahu pasti data sudah menetap sebelum
         * berkas lama dikosongkan.
         */
        @SuppressLint("ApplySharedPref")
        private fun migrateLegacy(ctx: Context, dst: SharedPreferences) {
            // Cek murah dulu: bila berkas era lama tak pernah ada, tak ada yang dimigrasi
            // dan kita terhindar dari pembacaan + dekripsi seluruh nilai (`dst.all`).
            if (!legacyFileExists(ctx)) return
            if (dst.all.isNotEmpty()) return
            val legacy = ctx.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val rencana = VelumMigration.plan(legacy.all)
            if (rencana.isEmpty()) return
            try {
                val ed = dst.edit()
                for ((k, v) in rencana) {
                    when (v) {
                        is String -> ed.putString(k, v)
                        is Boolean -> ed.putBoolean(k, v)
                        is Int -> ed.putInt(k, v)
                        is Long -> ed.putLong(k, v)
                        is Float -> ed.putFloat(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            ed.putStringSet(k, v as Set<String>)
                        }
                    }
                }
                if (!ed.commit()) return
                legacy.edit().clear().commit()
                Log.i(TAG, "migrasi prefs lama selesai")
            } catch (e: Exception) {
                Log.w(TAG, "migrasi prefs lama gagal", e)
            }
        }
    }
}
