package com.nexora.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 * Read-only network diagnostics. All DNS and HTTPS operations are bound to
 * the actual Android cellular Network (never fall back to Wi-Fi or a tunnel).
 * There is no proxying, spoofing, SNI rewriting, or third-party tunnelling.
 */
object NetworkDiagnostic {
    enum class Verdict { PASS, PARTIAL, FAIL }
    data class Finding(val verdict: Verdict, val detail: String)
    data class Target(val url: URL, val hostname: String)

    /** Human-supplied URL; HTTPS certificate/hostname validation remains enabled. */
    fun validate(raw: String): Target {
        val trimmed = raw.trim()
        require(trimmed.length in 12..2048) { "Informe uma URL HTTPS válida." }
        val u = try { URL(trimmed) } catch (_: Exception) {
            throw IllegalArgumentException("Endereço inválido.")
        }
        val uri = try { u.toURI() } catch (_: Exception) {
            throw IllegalArgumentException("URL HTTPS inválida.")
        }
        require(u.protocol.equals("https", true) && !u.host.isNullOrBlank() &&
                uri.userInfo == null && u.port in -1..65535 && u.port != 0) {
            "Use HTTPS com domínio válido (ex.: https://www.vivo.com.br)."
        }
        require(!u.host.contains(' ') && !u.host.contains('\n')) { "Domínio inválido." }
        return Target(u, u.host)
    }

    fun run(context: Context, target: Target, report: (String, Finding) -> Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val manager = CellularSelection(cm)
        val network = try { manager.obtain() } catch (_: SecurityException) { null }
        if (network == null) {
            report("cellular", Finding(Verdict.FAIL,
                "Não foi possível selecionar a rede móvel para teste. " + manager.diagnostics + " Wi-Fi não será usado."))
            report("dns", Finding(Verdict.FAIL, "Teste não executado: sem rede celular selecionada."))
            report("https", Finding(Verdict.FAIL, "Teste não executado: sem rede celular selecionada."))
            report("internet", Finding(Verdict.FAIL, "Teste não executado: sem rede celular selecionada."))
            manager.close()
            return
        }
        try {
            val caps = cm.getNetworkCapabilities(network)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) {
                report("cellular", Finding(Verdict.FAIL, "A rede selecionada deixou de ser móvel."))
                report("dns", Finding(Verdict.FAIL, "Não executado."))
                report("https", Finding(Verdict.FAIL, "Não executado."))
                report("internet", Finding(Verdict.FAIL, "Não executado."))
                return
            }
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            report("cellular", Finding(Verdict.PASS,
                "Rede móvel selecionada (#" + network.toString() + ") sem exigir acesso validado. Android confirma internet geral: " +
                    (if (validated) "sim" else "não ou desconhecido") +
                    ". O chip de dados é escolhido nas configurações do Android."))
            val start = SystemClock.elapsedRealtime()
            val dns = try {
                val addrs = network.getAllByName(target.hostname)
                if (addrs.isEmpty()) Finding(Verdict.FAIL, "DNS não retornou endereços.")
                else Finding(Verdict.PASS,
                    "DNS resolveu " + target.hostname + " para " + addrs.size +
                    " endereço(s) em " + (SystemClock.elapsedRealtime() - start) + " ms. " +
                    "Exemplo: " + addrs.first().hostAddress.orEmpty().take(90))
            } catch (error: Exception) {
                Finding(Verdict.FAIL, "DNS falhou na rede móvel: " + reason(error))
            }
            report("dns", dns)

            val https = checkTls(network, target.url)
            report("https", https)

            // An operator portal being reachable does NOT prove general Internet access.
            // Probe two unrelated well-known HTTPS destinations, without redirects.
            val google = httpStatus(network, URL("https://connectivitycheck.gstatic.com/generate_204"))
            val cloudflare = httpStatus(network, URL("https://www.cloudflare.com/cdn-cgi/trace"))
            val gOk = google.first == 204
            val cOk = cloudflare.first == 200
            val count = listOf(gOk, cOk).count { it }
            val summary = "Google: " + google.second + " | Cloudflare: " + cloudflare.second + ". "
            report("internet", when(count) {
                2 -> Finding(Verdict.PASS,
                    summary + "Ambos responderam pela rede móvel. Acesso geral provavelmente disponível.")
                1 -> Finding(Verdict.PARTIAL,
                    summary + "Somente um destino respondeu. Internet geral não confirmada.")
                else -> Finding(Verdict.FAIL,
                    summary + "Os testes não confirmaram acesso geral. Isso não prova bloqueio de toda a internet.")
            })
        } finally {
            manager.close()
        }
    }

    private fun checkTls(network: Network, url: URL): Finding {
        val start = SystemClock.elapsedRealtime()
        val connection = try { network.openConnection(url) as HttpsURLConnection }
        catch (error: Exception) { return Finding(Verdict.FAIL, reason(error)) }
        return try {
            prepare(connection)
            val status = connection.responseCode
            // getCipherSuite is available only after a TLS handshake.
            val cipher = connection.cipherSuite
            val ms = SystemClock.elapsedRealtime() - start
            val msg = "Certificado e TLS verificados em " + ms + " ms; HTTP " + status +
                "; cifra " + cipher.take(48) + ". Redirecionamentos não são seguidos."
            when {
                status in 200..299 -> Finding(Verdict.PASS, msg)
                status in 300..399 -> Finding(Verdict.PARTIAL,
                    msg + " A resposta é um redirecionamento, não prova acesso ao destino final.")
                else -> Finding(Verdict.PARTIAL,
                    msg + " Servidor alcançado, mas a página não foi liberada.")
            }
        } catch (error: Exception) {
            Finding(Verdict.FAIL, "HTTPS/TLS falhou: " + reason(error))
        } finally {
            connection.disconnect()
        }
    }

    private fun httpStatus(network: Network, url: URL): Pair<Int, String> {
        val conn = try { network.openConnection(url) as HttpsURLConnection }
        catch (error: Exception) { return -1 to reason(error) }
        return try {
            prepare(conn)
            val code = conn.responseCode
            val cipher = conn.cipherSuite
            code to ("HTTP " + code + " (TLS " + (if (cipher.isNotEmpty()) "válido)" else "desconhecido)"))
        } catch (error: Exception) {
            -1 to reason(error)
        } finally { conn.disconnect() }
    }

    private fun prepare(conn: HttpsURLConnection) {
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.requestMethod = "GET"
        conn.setRequestProperty("Cache-Control", "no-store")
        conn.setRequestProperty("Pragma", "no-cache")
        // Leave the platform's TLS trust store and hostname verifier untouched.
    }

    private fun reason(error: Exception): String = when (error) {
        is UnknownHostException -> "nome de domínio não resolvido"
        is SocketTimeoutException -> "tempo de conexão esgotado"
        is SSLException -> "falha no certificado ou na negociação TLS"
        is IOException -> "falha de rede (" + error.javaClass.simpleName.take(50) + ")"
        is SecurityException -> "permissão de rede negada"
        else -> "erro " + error.javaClass.simpleName.take(60)
    }

    private class CellularSelection(private val cm: ConnectivityManager) {
        var diagnostics = "O Android não forneceu uma rede de dados móveis a este aplicativo."
            private set
        private var callback: ConnectivityManager.NetworkCallback? = null

        /**
         * v0.3 tried requestNetwork(), which may require CHANGE_NETWORK_STATE,
         * and filtered out captive/no-credit networks with NET_CAPABILITY_INTERNET.
         * Either condition could produce a false "no cellular network" result.
         *
         * Read already-connected networks first. This needs ACCESS_NETWORK_STATE.
         * Do not require NET_CAPABILITY_VALIDATED or INTERNET on the mobile network:
         * a zero-rated portal may remain reachable without either capability.
         */
        private fun existing(): Network? {
            val all = cm.allNetworks.toList()
            val mobile = all.filter { network ->
                try {
                    cm.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                } catch (_: SecurityException) { false }
            }
            val active = cm.activeNetwork
            val preferred = mobile.firstOrNull { it == active }
                ?: mobile.firstOrNull {
                    cm.getNetworkCapabilities(it)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
                ?: mobile.firstOrNull()
            diagnostics = if (preferred != null)
                "Rede móvel Android identificada (" + mobile.size +
                    " disponível(is), rede #" + preferred.toString() + ")."
            else "O Android expôs " + all.size +
                " rede(s), mas nenhuma com transporte CELULAR. " +
                "Confirme os dados móveis e o SIM padrão no Android."
            return preferred
        }

        fun obtain(): Network? {
            try {
                existing()?.let { return it }
            } catch (error: SecurityException) {
                diagnostics = "Android negou acesso às informações de rede: " +
                    error.javaClass.simpleName
                return null
            }

            // Passive observation only; unlike requestNetwork(), this does
            // not trigger a new network or require CHANGE_NETWORK_STATE.
            val selected = AtomicReference<Network?>()
            val ready = CountDownLatch(1)
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    selected.set(network)
                    ready.countDown()
                }
            }
            try {
                callback = cb
                cm.registerNetworkCallback(request, cb)
                ready.await(4, TimeUnit.SECONDS)
                val mobile = selected.get()
                if (mobile != null) {
                    diagnostics = "Rede celular encontrada pelo observador Android (#" +
                        mobile.toString() + ")."
                    return mobile
                }
                return existing()
            } catch (error: Exception) {
                diagnostics = "Sem rede celular acessível ao diagnóstico. " +
                    "Observador: " + error.javaClass.simpleName + ". " + diagnostics
                return try { existing() } catch (_: Exception) { null }
            }
        }

        fun close() {
            callback?.let {
                try { cm.unregisterNetworkCallback(it) } catch (_: Exception) { }
            }
            callback = null
        }
    }
}
