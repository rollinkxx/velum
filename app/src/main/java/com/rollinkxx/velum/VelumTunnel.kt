package com.rollinkxx.velum

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

/**
 * Pengelola tunnel WARP berbasis WireGuard (GoBackend).
 * Singleton ringan: satu backend, satu tunnel, tanpa service tambahan —
 * VpnService milik library yang menjaga proses tetap hidup selama tersambung.
 */
object VelumTunnel : Tunnel {
    private const val NAME = "velum"
    private const val MTU = 1280
    private const val DNS = "1.1.1.1, 1.0.0.1"
    private const val ALLOWED_IPS = "0.0.0.0/0, ::/0"

    @Volatile
    private var backend: GoBackend? = null

    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    /** Callback UI; dipanggil dari thread backend, penerima harus pindah ke main thread sendiri. */
    @Volatile
    var listener: ((Tunnel.State) -> Unit)? = null

    override fun getName(): String = NAME

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
        listener?.invoke(newState)
    }

    private fun backend(context: Context): GoBackend =
        backend ?: synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }

    /** Sinkronkan status dengan backend (mis. setelah proses dibuat ulang). Blocking. */
    fun refreshState(context: Context): Tunnel.State {
        val s = backend(context).getState(this)
        state = s
        return s
    }

    /** Menyalakan tunnel. Blocking; panggil dari thread latar. */
    @Throws(Exception::class)
    fun up(context: Context, prefs: Prefs) {
        val config = buildConfig(prefs)
        backend(context).setState(this, Tunnel.State.UP, config)
    }

    /** Mematikan tunnel. Blocking; panggil dari thread latar. */
    @Throws(Exception::class)
    fun down(context: Context) {
        backend(context).setState(this, Tunnel.State.DOWN, null)
    }

    private fun buildConfig(prefs: Prefs): Config {
        val addresses = buildString {
            append(prefs.addressV4).append("/32")
            prefs.addressV6?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it).append("/128") }
        }
        val iface = Interface.Builder()
            .parsePrivateKey(requireNotNull(prefs.privateKey))
            .parseAddresses(addresses)
            .parseDnsServers(DNS)
            .parseMtu(MTU.toString())
            .build()
        val peer = Peer.Builder()
            .parsePublicKey(requireNotNull(prefs.peerPublicKey))
            .parseAllowedIPs(ALLOWED_IPS)
            .parseEndpoint(prefs.endpoint ?: VelumApi.DEFAULT_ENDPOINT)
            .parsePersistentKeepalive("25")
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }
}
