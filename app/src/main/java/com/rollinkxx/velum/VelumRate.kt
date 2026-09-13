package com.rollinkxx.velum

import java.util.ArrayDeque

/**
 * Penghitung laju trafik dengan jendela geser — murni, tanpa Android framework,
 * supaya logikanya bisa diuji JVM di CI (repo ini tidak punya emulator).
 *
 * Laju dihitung sebagai selisih penghitung kumulatif dalam satu jendela waktu,
 * bukan selisih dua titik: itu yang membuat angka halus dan tidak loncat-loncat
 * ketika trafik bursty. Penghitung yang mundur (tunnel dibangun ulang) dianggap
 * reset dan jendela dibersihkan — laju sesi lama tidak pernah dicampur dengan
 * laju sesi baru.
 */
object VelumRate {

    /** Satu titik pengukuran: penghitung kumulatif + waktu ([SystemClock.elapsedRealtime]). */
    class Sample(val rxBytes: Long, val txBytes: Long, val atMs: Long)

    /** Laju rata-rata dalam byte/detik. */
    class Rates(val rxBps: Double, val txBps: Double)

    /**
     * Pelacak untuk SATU sesi tunnel. Panggil [add] tiap sampel; kembalian null berarti
     * laju belum bisa dihitung (sampel pertama, atau penghitung baru saja di-reset).
     * Panggil [reset] saat sesi baru dimulai.
     */
    class Tracker(private val windowMs: Long = DEFAULT_WINDOW_MS) {
        private val samples = ArrayDeque<Sample>()

        fun add(rxBytes: Long, txBytes: Long, atMs: Long): Rates? {
            val last = samples.lastOrNull()
            if (last != null) {
                if (rxBytes < last.rxBytes || txBytes < last.txBytes) {
                    // Penghitung mundur = tunnel dibangun ulang; jangan campur laju.
                    samples.clear()
                } else if (atMs <= last.atMs) {
                    // Waktu tidak maju (panggilan beruntun tanpa jeda): ganti titik
                    // terakhir saja, laju belum bermakna.
                    samples.removeLast()
                    samples.addLast(Sample(rxBytes, txBytes, atMs))
                    return null
                }
            }
            samples.addLast(Sample(rxBytes, txBytes, atMs))
            val oldestAllowed = atMs - windowMs
            while (samples.size > 1 && samples.first().atMs < oldestAllowed) {
                samples.removeFirst()
            }
            val first = samples.first()
            val dtMs = atMs - first.atMs
            if (dtMs <= 0) return null
            val dtSec = dtMs / 1000.0
            return Rates(
                rxBps = (rxBytes - first.rxBytes).coerceAtLeast(0) / dtSec,
                txBps = (txBytes - first.txBytes).coerceAtLeast(0) / dtSec
            )
        }

        fun reset() = samples.clear()

        companion object {
            /** Jendela pengukuran default: 5 detik (cukup halus, tetap responsif). */
            const val DEFAULT_WINDOW_MS = 5000L
        }
    }
}
