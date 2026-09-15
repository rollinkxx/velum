package com.rollinkxx.velum

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RotatingTextPickerTest {
    private val values = listOf("a", "b", "c", "d")

    @Test
    fun kunciSama_mempertahankanPilihan() {
        val picker = RotatingTextPicker<String>(Random(7))
        val first = picker.valueFor(10L, values)

        assertEquals(first, picker.valueFor(10L, values))
    }

    @Test
    fun kunciBaru_tidakMengulangPilihanSebelumnya() {
        val picker = RotatingTextPicker<String>(Random(7))
        val first = picker.valueFor(10L, values)

        assertNotEquals(first, picker.valueFor(11L, values))
    }

    @Test
    fun clear_memungkinkanSesiBaru() {
        val picker = RotatingTextPicker<String>(Random(7))
        val first = picker.valueFor(10L, values)
        picker.clear()

        assertNotEquals(first, picker.valueFor(11L, values))
    }
}
