package com.rollinkxx.velum

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VelumErrorTest {

    @Test
    fun kodePenolakanKlien_dikenali() {
        for (code in listOf(401, 403, 404, 410, 426)) {
            assertEquals(
                "HTTP $code",
                VelumError.Kind.SERVER_REJECT,
                VelumError.kindOf(VelumApi.HttpError(code, "HTTP $code"))
            )
        }
    }

    @Test
    fun kodeServerLain_bukanPenolakanKlien() {
        for (code in listOf(500, 502, 503, 429)) {
            assertEquals(
                "HTTP $code",
                VelumError.Kind.UNKNOWN,
                VelumError.kindOf(VelumApi.HttpError(code, "HTTP $code"))
            )
        }
    }

    @Test
    fun kegagalanJaringan_dikenali() {
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(UnknownHostException("api")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(SocketTimeoutException("timeout")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(ConnectException("refused")))
        assertEquals(VelumError.Kind.NETWORK, VelumError.kindOf(IOException("reset")))
    }

    @Test
    fun kegagalanTakDikenal_dan_null() {
        assertEquals(VelumError.Kind.UNKNOWN, VelumError.kindOf(IllegalStateException("aneh")))
        assertEquals(VelumError.Kind.UNKNOWN, VelumError.kindOf(null))
    }
}
