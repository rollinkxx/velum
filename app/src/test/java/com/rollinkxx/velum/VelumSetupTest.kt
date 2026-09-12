package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji keputusan tawaran kesiapan ([VelumSetup.offer]).
 *
 * Yang dijaga di sini adalah **kapan aplikasi boleh mengganggu pengguna**: tawaran
 * hanya muncul bila ada yang kurang dan pengguna belum menundanya, dan keadaan
 * "tidak diketahui" tidak pernah dianggap kekurangan.
 */
class VelumSetupTest {

    @Test
    fun semuaSudahBeres_tidakAdaTawaran() {
        val t = VelumSetup.offer(
            registered = true, alwaysOn = true, batteryUnrestricted = true, postponed = false
        )
        assertFalse(t.wanted)
        assertNull(t.reason)
        assertTrue(t.reasons.isEmpty())
    }

    @Test
    fun belumTerdaftar_tidakAdaTawaran() {
        val t = VelumSetup.offer(
            registered = false, alwaysOn = false, batteryUnrestricted = false, postponed = false
        )
        assertFalse(t.wanted)
    }

    @Test
    fun sudahDitunda_tidakDitawarkanLagi() {
        val t = VelumSetup.offer(
            registered = true, alwaysOn = false, batteryUnrestricted = false, postponed = true
        )
        assertFalse(t.wanted)
    }

    @Test
    fun tidakDiketahui_bukanAlasanMenawarkan() {
        // Pembacaan pengaturan bisa gagal (izin dibatasi, ROM memodifikasi). Kondisi
        // tak diketahui tidak boleh dianggap kekurangan: lebih baik diam.
        val t = VelumSetup.offer(
            registered = true, alwaysOn = null, batteryUnrestricted = null, postponed = false
        )
        assertFalse(t.wanted)
    }

    @Test
    fun hanyaBaterai_kurang_tawaranAlasanBaterai() {
        val t = VelumSetup.offer(
            registered = true, alwaysOn = true, batteryUnrestricted = false, postponed = false
        )
        assertEquals(VelumSetup.REASON_BATTERY, t.reason)
        assertEquals(listOf(VelumSetup.REASON_BATTERY), t.reasons)
    }

    @Test
    fun hanyaAlwaysOn_kurang_tawaranAlasanAlwaysOn() {
        val t = VelumSetup.offer(
            registered = true, alwaysOn = false, batteryUnrestricted = true, postponed = false
        )
        assertEquals(VelumSetup.REASON_ALWAYS_ON, t.reason)
        assertEquals(listOf(VelumSetup.REASON_ALWAYS_ON), t.reasons)
    }

    @Test
    fun keduanyaKurang_tawaranGabunganDanAlwaysOnDidahulukan() {
        val t = VelumSetup.offer(
            registered = true, alwaysOn = false, batteryUnrestricted = false, postponed = false
        )
        assertTrue(t.wanted)
        assertEquals("ready", t.reason)
        // Alasan pertama menentukan layar yang dibuka tombol utama: always-on lebih dulu
        // karena dampaknya pada ketahanan tunnel paling besar.
        assertEquals(VelumSetup.REASON_ALWAYS_ON, t.reasons.first())
        assertEquals(2, t.reasons.size)
    }
}
