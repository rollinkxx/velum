package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pengujian unit murni JVM untuk [VelumRate] — jendela geser laju trafik.
 * Tidak memakai Android framework, berjalan cepat di CI (job `unitTest`).
 */
class VelumRateTest {

    private fun tracker(windowMs: Long = VelumRate.Tracker.DEFAULT_WINDOW_MS) =
        VelumRate.Tracker(windowMs)

    @Test
    fun add_sampelPertama_belumAdaLaju() {
        assertNull(tracker().add(0L, 0L, 1000L))
    }

    @Test
    fun add_duaSampel_lajuDasar() {
        val t = tracker()
        assertNull(t.add(0L, 0L, 1000L))
        val r = t.add(5000L, 2500L, 2000L)!!
        assertEquals(5000.0, r.rxBps, 1e-6)
        assertEquals(2500.0, r.txBps, 1e-6)
    }

    @Test
    fun add_jendelaLimaDetik_meratakanLaju() {
        val t = tracker()
        assertNull(t.add(0L, 0L, 0L))
        for (i in 1..6) {
            val r = t.add(i * 1000L, 0L, i * 1000L)!!
            assertEquals(1000.0, r.rxBps, 1e-6)
        }
    }

    @Test
    fun add_penghitungMundur_jendelaDibersihkan() {
        val t = tracker()
        assertNull(t.add(1000L, 0L, 1000L))
        val r1 = t.add(5000L, 0L, 2000L)!!
        assertEquals(4000.0, r1.rxBps, 1e-6)
        // Tunnel dibangun ulang: penghitung kembali ke angka kecil.
        assertNull(t.add(100L, 0L, 3000L))
        val r2 = t.add(200L, 0L, 4000L)!!
        assertEquals(100.0, r2.rxBps, 1e-6)
    }

    @Test
    fun add_tanpaTrafik_lajuNol() {
        val t = tracker()
        assertNull(t.add(0L, 0L, 1000L))
        val r = t.add(0L, 0L, 2000L)!!
        assertEquals(0.0, r.rxBps, 1e-6)
        assertEquals(0.0, r.txBps, 1e-6)
    }

    @Test
    fun add_waktuTidakMaju_tidakMenghitung() {
        val t = tracker()
        assertNull(t.add(0L, 0L, 1000L))
        // Dua titik pada waktu yang sama: laju belum bermakna.
        assertNull(t.add(100L, 0L, 1000L))
        val r = t.add(200L, 0L, 2000L)!!
        // Baseline tergeser ke titik terakhir (100, t=1000).
        assertEquals(100.0, r.rxBps, 1e-6)
    }

    @Test
    fun reset_memulaiSesiBaru() {
        val t = tracker()
        assertNull(t.add(0L, 0L, 1000L))
        assertNotNull(t.add(5000L, 0L, 2000L))
        t.reset()
        assertNull(t.add(6000L, 0L, 3000L))
    }
}
