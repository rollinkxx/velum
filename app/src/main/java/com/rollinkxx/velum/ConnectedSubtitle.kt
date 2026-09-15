package com.rollinkxx.velum

import android.content.Context

/** Subtitle koneksi yang dipilih sekali per sesi tunnel dan dipakai oleh UI serta notifikasi. */
object ConnectedSubtitle {
    private val picker = RotatingTextPicker<String>()

    fun forSession(context: Context, sessionKey: Long): String = picker.valueFor(
        sessionKey,
        listOf(
            context.getString(R.string.status_connected_variant_1),
            context.getString(R.string.status_connected_variant_2),
            context.getString(R.string.status_connected_variant_3),
            context.getString(R.string.status_connected_variant_4)
        )
    )
}
