package com.rollinkxx.velum

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Klasifikasi kegagalan jaringan/API — murni tanpa Android framework supaya teruji unit.
 *
 * Tujuannya agar pesan ke pengguna tidak generik: "Layanan menolak klien ini (HTTP 403)"
 * jelas berbeda perlakuannya dari "Kesalahan jaringan: timeout".
 */
object VelumError {

    enum class Kind {
        /** Jaringan putus/lambat — layak dicoba ulang. */
        NETWORK,

        /** Upstream menolak klien ini — mengulang percuma, butuh pembaruan aplikasi. */
        SERVER_REJECT,

        /** Tidak terklasifikasi — tampilkan pesan bawaan pemanggil. */
        UNKNOWN
    }

    fun kindOf(e: Throwable?): Kind = when (e) {
        null -> Kind.UNKNOWN
        // Harus sebelum IOException: HttpError adalah IOException juga.
        is VelumApi.HttpError ->
            if (VelumUpstream.isClientRejected(e.code)) Kind.SERVER_REJECT else Kind.UNKNOWN
        is UnknownHostException, is SocketTimeoutException, is ConnectException -> Kind.NETWORK
        is IOException -> Kind.NETWORK
        else -> Kind.UNKNOWN
    }
}
