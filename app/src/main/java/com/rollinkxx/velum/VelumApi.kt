package com.rollinkxx.velum

import android.os.SystemClock
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
object VelumApi {
    /** Kode & pesan HTTP yang gagal, supaya UI bisa membedakan penolakan klien. */
    class HttpError(val code: Int, message: String) : IOException(message)

    private const val BASE = VelumUpstream.BASE
    private const val CLIENT_VERSION = VelumUpstream.CLIENT_VERSION
    private const val USER_AGENT = VelumUpstream.USER_AGENT
    const val DEFAULT_ENDPOINT = VelumUpstream.DEFAULT_ENDPOINT

    /**
     * Matikan keep-alive HTTP. Android tidak memindahkan soket yang SUDAH terbuka ke VPN,
     * jadi koneksi yang tersisa dari sebelum tunnel aktif akan dipakai ulang dan keluar
     * langsung ke internet — hasil `cdn-cgi/trace` lalu salah (`warp=off`) meski tunnel UP.
     */
    init {
        runCatching { System.setProperty("http.keepAlive", "false") }
    }

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

        val hasil = VelumRegistration.parse(request("POST", "$BASE/reg", body.toString(), null))

        prefs.privateKey = keyPair.privateKey.toBase64()
        prefs.deviceId = hasil.id
        prefs.token = hasil.token
        prefs.addressV4 = hasil.addressV4
        prefs.addressV6 = hasil.addressV6
        prefs.peerPublicKey = hasil.peerPublicKey
        prefs.endpoint = hasil.endpoint
        prefs.warpEnabled = true // body registrasi memang meminta warp_enabled
    }

    /**
     * Menyembuhkan akun lama yang terdaftar tanpa flag WARP (era sebelum registrasi
     * memakai warp_enabled): GET /reg/{id} → bila account.warp_enabled == false → hapus
     * registrasi lama di server lalu daftar ulang (otomatis memakai flag baru).
     * Postur fail-safe: hasil tak meyakinkan atau error jaringan → tidak melakukan apa pun
     * (pengguna tetap bisa Daftar ulang manual). Dipanggil dari thread latar.
     */
    fun ensureWarpEnabled(prefs: Prefs) {
        val id = prefs.deviceId ?: return
        val token = prefs.token ?: return
        val enabled: Boolean? = try {
            val json = JSONObject(request("GET", "$BASE/reg/$id", null, token))
            val account = json.optJSONObject("account")
            if (account != null && account.has("warp_enabled")) {
                account.optBoolean("warp_enabled")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        when (enabled) {
            true -> prefs.warpEnabled = true
            false -> {
                try {
                    request("DELETE", "$BASE/reg/$id", null, token)
                } catch (_: IOException) {
                    // Server mungkin sudah lupa; tetap daftar ulang.
                }
                prefs.clear()
                register(prefs) // IOException dibiarkan ke pemanggil; prefs set ulang di sana
            }
            null -> Unit // tak meyakinkan: jangan sentuh apa pun
        }
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

    /**
     * Host uji trace; yang kedua cadangan, karena sebagian jaringan memblokir salah satu
     * host Cloudflare sehingga uji gagal padahal tunnelnya sehat.
     */
    private val TRACE_URLS = listOf(
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://one.one.one.one/cdn-cgi/trace"
    )

    /** Anggaran total: cadangan tidak boleh menambah waktu tunggu tanpa batas. */
    private const val TRACE_BUDGET_MS = 14_000L

    /** Mengambil dan mem-parse cdn-cgi/trace (mengikuti jalur koneksi saat ini). Blocking. */
    @Throws(IOException::class)
    fun fetchTrace(): VelumFormat.TraceInfo {
        val started = SystemClock.elapsedRealtime()
        var lastError: IOException? = null
        for (url in TRACE_URLS) {
            try {
                return fetchTraceFrom(url)
            } catch (e: IOException) {
                lastError = e
                // Gagal cepat (mis. host diblokir DNS) → masih ada waktu untuk cadangan.
                // Gagal karena menggantung sampai batas waktu → cadangan hanya menambah
                // waktu tunggu, jadi dihentikan saja.
                if (SystemClock.elapsedRealtime() - started > TRACE_BUDGET_MS) break
            }
        }
        throw lastError ?: IOException("uji trace gagal")
    }

    @Throws(IOException::class)
    private fun fetchTraceFrom(url: String): VelumFormat.TraceInfo {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            // Soket baru untuk setiap uji: jangan pakai koneksi dari sebelum tunnel aktif.
            conn.setRequestProperty("Connection", "close")
            conn.useCaches = false
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return VelumFormat.parseTrace(text)
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun request(method: String, url: String, body: String?, bearer: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("CF-Client-Version", CLIENT_VERSION)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Connection", "close")
            conn.useCaches = false
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw if (VelumUpstream.isClientRejected(code)) {
                    HttpError(code, "HTTP $code: ${text.take(120)}")
                } else {
                    IOException("HTTP $code: ${text.take(120)}")
                }
            }
            return text
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
