package com.nexora.vpn

import android.content.Context
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Independent HTTPS server catalog. A real Nexora backend is required. */
object ServerDirectory {
    data class Server(val id: String, val name: String, val region: String, val operators: Set<String>) {
        fun supports(operator: String) = operator == "auto" || operators.isEmpty() ||
            operators.contains("auto") || operators.contains(operator)
    }
    private const val STORE = "nexora_settings"

    fun url(context: Context): String {
        val override = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString("serviceUrl", "").orEmpty().trim()
        return if (override.isNotEmpty()) override else BuildConfig.NEXORA_API_URL.trim()
    }

    fun setUrl(context: Context, address: String) {
        val cleaned = address.trim().trimEnd('/')
        if (cleaned.isNotEmpty()) secureUrl(cleaned)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString("serviceUrl", cleaned).apply()
    }

    private fun secureUrl(raw: String): URL {
        val uri = URL(raw)
        require(uri.protocol.equals("https", true) && uri.host.isNotBlank() &&
            uri.userInfo == null && uri.query == null && uri.ref == null) {
            "Use um endereço HTTPS válido para o serviço Nexora."
        }
        return uri
    }

    private fun request(context: Context, suffix: String, data: JSONObject? = null): JSONObject {
        val base = url(context).trimEnd('/')
        require(base.isNotEmpty()) { "Nenhum serviço de servidores foi configurado." }
        secureUrl(base)
        val conn = secureUrl(base + suffix).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-store")
            if (data != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            }
            require(conn.responseCode in 200..299) {
                "Serviço de servidores indisponível (HTTP " + conn.responseCode + ")."
            }
            val bytes = conn.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val tmp = ByteArray(4096)
                while (out.size() <= 65536) {
                    val count = stream.read(tmp, 0, minOf(tmp.size, 65537 - out.size()))
                    if (count <= 0) break
                    out.write(tmp, 0, count)
                }
                out.toByteArray()
            }
            require(bytes.size <= 65536) { "Resposta do serviço muito grande." }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }

    fun list(context: Context): List<Server> {
        val array = request(context, "/v1/servers").getJSONArray("servers")
        require(array.length() <= 100) { "Catálogo inválido." }
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            require(id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "ID de servidor inválido." }
            val carriers = mutableSetOf<String>()
            val operatorArray = item.optJSONArray("operators")
            if (operatorArray != null) {
                for (j in 0 until operatorArray.length())
                    carriers.add(operatorArray.getString(j).lowercase())
            }
            Server(id, item.getString("name").take(70),
                item.optString("region", "Brasil").take(40), carriers)
        }
    }

    /** The private key is created on-device; only the public key leaves the device. */
    fun provision(context: Context, server: Server, operator: String): ByteArray {
        val keys = KeyPair()
        val body = JSONObject().put("server_id", server.id)
            .put("operator", operator).put("public_key", keys.publicKey.toBase64())
        val reply = request(context, "/v1/sessions", body)
        val address = line(reply.getString("address"))
        val peer = line(reply.getString("peer_public_key"))
        Key.fromBase64(peer)
        val endpoint = line(reply.getString("endpoint"))
        val dnsArray = reply.optJSONArray("dns")
        val dns = if (dnsArray != null && dnsArray.length() > 0)
            (0 until dnsArray.length()).joinToString(", ") { line(dnsArray.getString(it)) }
            else "1.1.1.1"
        val allowed = line(reply.optString("allowed_ips", "0.0.0.0/0, ::/0"))
        val keepalive = reply.optInt("persistent_keepalive", 25).coerceIn(0, 120)
        val config = listOf(
            "[Interface]",
            "PrivateKey = " + keys.privateKey.toBase64(),
            "Address = " + address,
            "DNS = " + dns,
            "",
            "[Peer]",
            "PublicKey = " + peer,
            "AllowedIPs = " + allowed,
            "Endpoint = " + endpoint,
            "PersistentKeepalive = " + keepalive,
            ""
        ).joinToString("\n")
        val bytes = config.toByteArray(Charsets.UTF_8)
        try {
            Config.parse(ByteArrayInputStream(bytes))
            return bytes
        } catch (_: Exception) {
            bytes.fill(0)
            throw IllegalArgumentException("Configuração inválida recebida do servidor.")
        }
    }

    private fun line(value: String): String {
        val s = value.trim()
        require(s.length in 1..300 && '\n' !in s && '\r' !in s && '[' !in s && ']' !in s) {
            "Resposta de servidor inválida."
        }
        return s
    }
}
