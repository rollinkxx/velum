package com.rollinkxx.velum

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan mati/hidup [VelumLog]. Yang dijaga bukan sekadar gaya: seluruh peredaman log
 * sensitif bergantung pada d/i yang benar-benar no-op pada build non-debug, sementara
 * w/e harus tetap menyala karena tanpa adb keduanya satu-satunya jejak kegagalan.
 *
 * `android.util.Log` tidak ada di JVM, jadi [VelumLog.sink] diganti penadah buatan;
 * bawaan platform tidak pernah dieksekusi selama pengujian ini.
 */
class VelumLogTest {

    private val terkirim = mutableListOf<List<Any?>>()
    private var asalEnabled = true
    private lateinit var asalSink: (Int, String, String, Throwable?) -> Unit

    private fun pasang(enabled: Boolean) {
        asalEnabled = VelumLog.verboseEnabled
        asalSink = VelumLog.sink
        VelumLog.verboseEnabled = enabled
        terkirim.clear()
        VelumLog.sink = { level, tag, message, tr -> terkirim.add(listOf(level, tag, message, tr)) }
    }

    @After
    fun lepas() {
        if (::asalSink.isInitialized) {
            VelumLog.verboseEnabled = asalEnabled
            VelumLog.sink = asalSink
        }
    }

    @Test
    fun debug_buildDebug_pesanTerkirim() {
        pasang(enabled = true)
        VelumLog.d("T", "rinci")
        VelumLog.i("T", "peristiwa")
        assertEquals(2, terkirim.size)
        assertEquals(VelumLog.LEVEL_D, terkirim[0][0])
        assertEquals(VelumLog.LEVEL_I, terkirim[1][0])
    }

    @Test
    fun debug_buildRilis_tidakMengirimApaPun() {
        pasang(enabled = false)
        VelumLog.d("T", "endpoint 162.159.192.1:2408")
        VelumLog.i("T", "peristiwa sesi")
        // Inilah inti peredaman: build rilis tidak boleh menulis data sesi ke logcat.
        assertTrue("d/i harus no-op pada build non-debug", terkirim.isEmpty())
    }

    @Test
    fun peringatan_buildRilis_tetapTerkirim() {
        pasang(enabled = false)
        VelumLog.w("T", "gagal jaringan")
        assertEquals(1, terkirim.size)
        assertEquals(VelumLog.LEVEL_W, terkirim[0][0])
        assertEquals("gagal jaringan", terkirim[0][2])
    }

    @Test
    fun galat_denganPenyebab_diteruskanUtuh() {
        pasang(enabled = false)
        val sebab = RuntimeException("asal")
        VelumLog.e("T", "gagal serius", sebab)
        assertEquals(1, terkirim.size)
        assertEquals(VelumLog.LEVEL_E, terkirim[0][0])
        assertSame("throwable harus diteruskan apa adanya", sebab, terkirim[0][3])
    }
}
