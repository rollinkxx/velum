package com.rollinkxx.velum

import kotlin.random.Random

/**
 * Memilih satu item untuk setiap kunci sesi dan menghindari item yang sama
 * dengan sesi sebelumnya. State ini sengaja berada di level proses agar rotasi
 * layar tidak mengganti teks selama koneksi atau hasil uji masih sama.
 */
class RotatingTextPicker<T>(private val random: Random = Random.Default) {
    private var currentKey: Long? = null
    private var currentValue: T? = null

    @Synchronized
    fun valueFor(key: Long, values: List<T>): T {
        require(values.isNotEmpty()) { "values tidak boleh kosong" }
        if (currentKey == key) return requireNotNull(currentValue)

        val candidates = values.filter { it != currentValue }.ifEmpty { values }
        val selected = candidates[random.nextInt(candidates.size)]
        currentKey = key
        currentValue = selected
        return selected
    }

    @Synchronized
    fun clear() {
        currentKey = null
    }
}
