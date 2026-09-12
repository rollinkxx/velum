package com.rollinkxx.velum

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pembantu murni: tanpa Android framework, tanpa I/O, tanpa keadaan global.
 * Sengaja dipisah dari Activity/API supaya logika yang rawan salah (parse
 * `cdn-cgi/trace`, pemformatan, pemilihan endpoint) bisa diuji oleh unit test JVM.
 */
object VelumFormat {

    /** Hasil parse `cdn-cgi/trace`. Nilai yang tidak ada = string kosong. */
    class TraceInfo(val warp: String, val colo: String, val ip: String)

    /** Mem-parse teks `cdn-cgi/trace` baris demi baris (`kunci=nilai`). */
    fun parseTrace(text: String): TraceInfo {
        var warp = ""
        var colo = ""
        var ip = ""
        for (line in text.lineSequence()) {
            when {
                line.startsWith("warp=") -> warp = line.removePrefix("warp=")
                line.startsWith("colo=") -> colo = line.removePrefix("colo=")
                line.startsWith("ip=") -> ip = line.removePrefix("ip=")
            }
        }
        return TraceInfo(warp, colo, ip)
    }

    /**
     * Apakah trace membuktikan lalu lintas lewat jalur ingress WARP.
     * Hanya `on` dan `plus` yang dianggap aktif; `off` dan nilai kosong tidak.
     */
    fun isWarpActive(trace: TraceInfo): Boolean = trace.warp == "on" || trace.warp == "plus"

    /** Memformat jumlah byte ke satuan paling masuk akal (B/KB/MB/GB). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** Memformat durasi (ms) ke `mm:ss` atau `h:mm:ss` bila lebih dari satu jam. */
    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** Jam menit lokal (`HH:mm`) untuk cap waktu hasil uji. */
    fun formatClock(epochMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

    /**
     * Apakah hasil parse ini benar-benar berasal dari keluaran `cdn-cgi/trace`.
     *
     * Portal tawanan (captive portal) dan halaman galat proxy menjawab HTTP 200 berisi
     * HTML, yang diparse menjadi tiga bidang kosong. Tanpa pemeriksaan ini keadaan itu
     * dilaporkan sebagai "Belum aktif" — seolah tunnelnya tidak bekerja, padahal yang
     * sebenarnya terjadi jaringan ini meminta login lebih dulu. Diagnosis salah arah,
     * persis kelas masalah yang dulu membuat "Kesalahan jaringan: Unable to resolve host"
     * menyesatkan pengguna.
     */
    fun isUsable(trace: TraceInfo): Boolean =
        trace.warp.isNotEmpty() || trace.colo.isNotEmpty() || trace.ip.isNotEmpty()

    /** Memisahkan host dari "host:port" (aman untuk literal IPv6 dalam kurung siku). */
    fun hostPart(endpoint: String): String {
        if (endpoint.startsWith("[")) return endpoint.substringBefore("]").removePrefix("[")
        return if (endpoint.count { it == ':' } == 1) endpoint.substringBeforeLast(":") else endpoint
    }

    /** Apakah [host] literal IPv4 (bukan nama domain). */
    fun isIpLiteral(host: String): Boolean =
        host.all { it.isDigit() || it == '.' } && host.count { it == '.' } == 3
}
