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

    /**
     * Memformat jumlah byte ke satuan paling masuk akal (B/KB/MB/GB), memakai **koma**
     * sebagai pemisah desimal — sama dengan [formatSeconds] dan konvensi Indonesia.
     *
     * Sebelumnya fungsi ini memakai titik, sehingga baris `Data` menulis "5.1 MB"
     * sementara baris `Boot` pada ringkasan diagnostik yang sama menulis "14,2 detik".
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb).replace('.', ',')
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb).replace('.', ',')
        return String.format(Locale.US, "%.2f GB", mb / 1024.0).replace('.', ',')
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

    /**
     * Durasi dalam detik dengan satu desimal, memakai koma (konvensi Indonesia):
     * `14200` -> `"14,2 detik"`.
     *
     * Dipakai untuk angka yang presisinya penting bagi keputusan, misalnya berapa lama
     * `BootReceiver` menghabiskan anggaran `goAsync()` (batasnya 10 detik, jadi "14 detik"
     * dan "14,2 detik" punya arti berbeda). `formatDuration` tidak dipakai di sini karena
     * membulatkan ke detik penuh.
     */
    fun formatSeconds(ms: Long): String {
        val clamped = ms.coerceAtLeast(0)
        val detik = clamped / 1000.0
        val teks = String.format(Locale.US, "%.1f", detik).replace('.', ',')
        return "$teks detik"
    }

    /**
     * Umur relatif dalam bahasa manusia: `"42 detik lalu"`, `"5 menit lalu"`,
     * `"3 jam lalu"`, `"3 hari lalu"`. Nilai negatif dianggap nol.
     *
     * Baris `Handshake` pada diagnostik SENGAJA tidak dipindah ke fungsi ini: formatnya
     * (`"N detik lalu"`) sudah dikunci oleh `VelumDiagnosticsTest` dan dibaca pengguna
     * sejak lama. Fungsi ini hanya untuk baris baru.
     */
    fun formatAge(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        return when {
            s < 60 -> "$s detik lalu"
            s < 3_600 -> "${s / 60} menit lalu"
            s < 86_400 -> "${s / 3_600} jam lalu"
            else -> "${s / 86_400} hari lalu"
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
