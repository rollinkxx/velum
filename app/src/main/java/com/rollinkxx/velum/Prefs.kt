package com.rollinkxx.velum

import android.content.Context
import android.content.SharedPreferences

/** Penyimpanan ringan untuk data registrasi WARP (SharedPreferences, tanpa library tambahan). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("velum", Context.MODE_PRIVATE)

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

    /** Memo: akun terkonfirmasi memakai flag WARP penuh (set oleh registrasi/ensure). */
    var warpEnabled: Boolean
        get() = sp.getBoolean(K_WARP, false)
        set(v) = sp.edit().putBoolean(K_WARP, v).apply()

    /** Memo: terakhir kali tunnel memang UP (untuk sambung ulang saat boot). */
    var wasUp: Boolean
        get() = sp.getBoolean(K_WAS_UP, false)
        set(v) = sp.edit().putBoolean(K_WAS_UP, v).apply()

    /** Registrasi dianggap lengkap bila semua bidang inti tersedia. */
    val isRegistered: Boolean
        get() = !privateKey.isNullOrEmpty() && !addressV4.isNullOrEmpty() &&
            !peerPublicKey.isNullOrEmpty() && !endpoint.isNullOrEmpty()

    fun clear() = sp.edit().clear().apply()

    private companion object {
        const val K_PRIV = "private_key"
        const val K_ID = "device_id"
        const val K_TOKEN = "token"
        const val K_V4 = "addr_v4"
        const val K_V6 = "addr_v6"
        const val K_PEER = "peer_pub"
        const val K_ENDPOINT = "endpoint"
        const val K_WARP = "warp_enabled"
        const val K_WAS_UP = "was_up"
    }
}
