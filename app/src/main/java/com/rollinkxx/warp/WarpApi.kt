package com.rollinkxx.warp

import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Klien minimal API registrasi Cloudflare WARP.
 * Hanya memakai HttpURLConnection + org.json bawaan Android agar tidak menambah dependensi.
 * Semua fungsi bersifat blocking: panggil dari thread latar.
 */
object WarpApi {
    private const val BASE = "https://api.cloudflareclient.com/v0a2158"
    private const val CLIENT_VERSION = "a-6.10-2158"
    private const val USER_AGENT = "okhttp/3.12.1"
    const val DEFAULT_ENDPOINT = "engage.cloudflareclient.com:2408"

    /** Mendaftarkan perangkat baru dan menyimpan hasilnya ke [prefs]. */
    @Throws(IOException::class)
    fun register(prefs: Prefs) {
        val keyPair = KeyPair()
        val body = JSONObject()
            .put("key", keyPair.publicKey.toBase64())
            .put("install_id", "")
            .put("fcm_token", "")
            .put("tos", isoNow())
            .put("model", "Android")
            .put("type", "Android")
            .put("locale", "id_ID")
            .put("serial_number", UUID.randomUUID().toString())
            .put("warp_enabled", true)

        val json = request("POST", "$BASE/reg", body.toString(), null)

        val id = json.getString("id")
        val token = json.getString("token")
        val config = json.getJSONObject("config")
        val addresses = config.getJSONObject("interface").getJSONObject("addresses")
        val peer = config.getJSONArray("peers").getJSONObject(0)
        val endpointObj = peer.getJSONObject("endpoint")
        val host = endpointObj.optString("host", DEFAULT_ENDPOINT)

        prefs.privateKey = keyPair.privateKey.toBase64()
        prefs.deviceId = id
        prefs.token = token
        prefs.addressV4 = addresses.getString("v4")
        prefs.addressV6 = addresses.optString("v6", null)
        prefs.peerPublicKey = peer.getString("public_key")
        prefs.endpoint = if (host.isNotEmpty()) host else DEFAULT_ENDPOINT
    }

    /** Menghapus registrasi di server (best-effort) lalu membersihkan penyimpanan lokal. */
    fun unregister(prefs: Prefs) {
        val id = prefs.deviceId
        val token = prefs.token
        if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) {
            try {
                request("DELETE", "$BASE/reg/$id", null, token)
            } catch (_: IOException) {
                // Abaikan; data lokal tetap dibersihkan.
            }
        }
        prefs.clear()
    }

    /** Mengembalikan true bila lalu lintas sudah lewat WARP (cdn-cgi/trace: warp=on/plus). */
    @Throws(IOException::class)
    fun isWarpActive(): Boolean {
        val conn = (URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return text.lineSequence().any { it == "warp=on" || it == "warp=plus" }
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun request(method: String, url: String, body: String?, bearer: String?): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("CF-Client-Version", CLIENT_VERSION)
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(200)}")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
