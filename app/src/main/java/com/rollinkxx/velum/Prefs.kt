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

    /** Endpoint efektif: hasil proba bila ada, sonst endpoint registrasi. */
    val effectiveEndpoint: String?
        get() = speedEndpoint ?: endpoint

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
     * Memo: tawaran kesiapan (Always-on VPN & bebas optimasi baterai) sudah ditampilkan
     * dan ditunda pengguna. Sekali ditolak, tidak ditawarkan lagi.
     */
    var setupPostponed: Boolean
        get() = sp.getBoolean(K_SETUP_POSTPONED, false)
        set(v) = sp.edit().putBoolean(K_SETUP_POSTPONED, v).apply()

    /** Registrasi dianggap lengkap bila semua bidang inti tersedia. */
    val isRegistered: Boolean
        get() = !privateKey.isNullOrEmpty() && !addressV4.isNullOrEmpty() &&
            !peerPublicKey.isNullOrEmpty() && !endpoint.isNullOrEmpty()

    /**
     * Membersihkan data registrasi. Dua memo **sengaja dipertahankan**: [wasUp]
     * (niat sambung ulang saat boot) dan [setupPostponed] — menekan Daftar ulang
     * bukan alasan untuk menanyakan kembali tawaran pengaturan yang sudah ditolak.
     */
    fun clear() {
        val keepUp = wasUp
        val keepPostponed = setupPostponed
        sp.edit().clear().apply()
        if (keepUp) wasUp = true
        if (keepPostponed) setupPostponed = true
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

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
        const val K_WARP = "warp_enabled"
        const val K_WAS_UP = "was_up"
        const val K_SETUP_POSTPONED = "setup_postponed"
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
            if (encrypted == null) return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
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
