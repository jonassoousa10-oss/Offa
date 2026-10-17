package com.nexora.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** A single backend for the process; never bind its lifetime to the Activity. */
object VpnSession {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val observers = CopyOnWriteArraySet<(State, String) -> Unit>()
    @Volatile var state: State = State.DISCONNECTED
        private set
    @Volatile var details: String = "Nenhum túnel ativo"
        private set
    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = "nexora"
        override fun onStateChange(newState: Tunnel.State) {
            when (newState) {
                Tunnel.State.UP -> emit(State.CONNECTED, "Túnel WireGuard ativo")
                Tunnel.State.DOWN -> emit(State.DISCONNECTED, "Desconectado")
                else -> Unit
            }
        }
    }

    fun init(context: Context) { app = context.applicationContext }

    fun observe(callback: (State, String) -> Unit) {
        observers.add(callback)
        main.post { callback(state, details) }
    }

    fun unobserve(callback: (State, String) -> Unit) { observers.remove(callback) }

    private fun emit(newState: State, message: String) {
        state = newState
        details = message
        main.post { observers.forEach { it(newState, message) } }
    }

    fun connect() {
        if (state == State.CONNECTED || state == State.CONNECTING) return
        emit(State.CONNECTING, "Estabelecendo conexão segura…")
        executor.execute {
            try {
                val config: Config = EncryptedProfileStore.load(app)
                val engine = backend ?: GoBackend(app).also { backend = it }
                engine.setState(tunnel, Tunnel.State.UP, config)
                emit(State.CONNECTED, "Túnel ativo — verificando tráfego")
                val intent = Intent(app, ConnectionService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent)
                    else app.startService(intent)
                } catch (_: Exception) {
                    // The VPN remains usable in foreground; background survival might be limited.
                }
            } catch (error: Exception) {
                // Never log exception.message: backend errors can include server/profile details.
                emit(State.ERROR, "Não foi possível estabelecer o túnel. Verifique a rede e o perfil.")
            }
        }
    }

    fun disconnect() {
        if (state == State.DISCONNECTED || state == State.DISCONNECTING) return
        emit(State.DISCONNECTING, "Encerrando conexão…")
        executor.execute {
            try { backend?.setState(tunnel, Tunnel.State.DOWN, null) }
            catch (_: Exception) { /* Best effort: notify user below */ }
            emit(State.DISCONNECTED, "Desconectado")
            app.stopService(Intent(app, ConnectionService::class.java))
        }
    }

    fun traffic(onResult: (Long, Long) -> Unit) {
        executor.execute {
            try {
                val stats = backend?.getStatistics(tunnel)
                val rx = stats?.totalRx() ?: 0L
                val tx = stats?.totalTx() ?: 0L
                main.post { onResult(rx, tx) }
            } catch (_: Exception) { main.post { onResult(0, 0) } }
        }
    }
}
