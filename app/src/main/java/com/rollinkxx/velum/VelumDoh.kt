package com.rollinkxx.velum

import org.json.JSONObject

/**
 * Parse respons DNS-over-HTTPS (skema `application/dns-json` sebagaimana dilayani
 * `cloudflare-dns.com/dns-query`) — murni supaya teruji unit di JVM.
 *
 * Jawaban DoH yang aneh (rekaman selain A, HTML portal tawanan, JSON rusak) harus
 * menjadi daftar KOSONG, bukan pengecualian: pemanggil jatuh ke daftar statis, dan
 * itulah satu-satunya perilaku aman.
 */
object VelumDoh {

    /**
     * Batas kandidat yang disimpan dari satu respons: pool proba punya anggaran total
     * detik, dan jawaban abnormal tidak boleh meledakkannya atau membanjiri penyimpanan.
     */
    const val MAX_STORED = 12

    /**
     * Alamat IPv4 unik dari bagian `Answer` (hanya rekaman A / `type=1`), urut mengikuti
     * respons, dibatasi [MAX_STORED]. Masukan cacat menghasilkan daftar kosong.
     */
    fun parseARecords(json: String): List<String> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val answers = root.optJSONArray("Answer") ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until answers.length()) {
            val a = answers.optJSONObject(i) ?: continue
            if (a.optInt("type") != 1) continue
            val data = a.optString("data", "").trim()
            if (VelumFormat.isIpv4(data)) out.add(data)
        }
        return out.take(MAX_STORED)
    }
}
