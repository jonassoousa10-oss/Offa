package com.nexora.vpn

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/**
 * Experimental SSH2 + local SOCKS5 CONNECT gateway for authorized SSH servers.
 * This does not override the WireGuard VpnService and does not route other apps.
 * Uses a known-host SHA256 fingerprint. A new/changed key is never trusted blindly.
 * No Rev Hunter private servers, app logic, ad limits, or secrets are used.
 */
object SshProxyEngine {
    data class Profile(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val pinnedSha256: String
    ) {
        fun validate() {
            require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9.:_-]+"))) {
                "Informe o endereço de seu servidor SSH autorizado."
            }
            require(port in 1..65535) { "Porta inválida." }
            require(username.length in 1..128 && password.isNotEmpty()) {
                "Informe usuário e senha válidos."
            }
            require(pinnedSha256.isBlank() || pinnedSha256.matches(
                Regex("SHA256:[A-Za-z0-9+/]{42,44}={0,2}"))) {
                "Fingerprint incorreto. Use SHA256:... fornecido pelo dono do SSH."
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val handlers = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val activeClients = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    private val openedChannels = java.util.Collections.synchronizedSet(
        mutableSetOf<com.jcraft.jsch.Channel>())
    private val connections = AtomicInteger()
    private var session: Session? = null
    private var socks: ServerSocket? = null
    @Volatile private var running = false
    @Volatile var localPort: Int = -1
        private set
    @Volatile var state: String = "Desconectado"
        private set
    @Volatile var lastFingerprint: String = ""
        private set

    private fun post(status: String, callback: (String) -> Unit) {
        state = status
        main.post { callback(status) }
    }

    fun connect(profile: Profile, callback: (String) -> Unit) {
        worker.execute {
            try {
                profile.validate()
                closeInternal()
                post("Conectando ao SSH autorizado…", callback)
                lastFingerprint = ""
                val repo = object : HostKeyRepository {
                    override fun check(host: String?, key: ByteArray?): Int {
                        if (key == null) return HostKeyRepository.CHANGED
                        val digest = MessageDigest.getInstance("SHA-256").digest(key)
                        val fingerprint = "SHA256:" + Base64.encodeToString(
                            digest, Base64.NO_WRAP or Base64.NO_PADDING)
                        lastFingerprint = fingerprint
                        val expected = profile.pinnedSha256.trim().trimEnd('=')
                        val actual = fingerprint.trimEnd('=')
                        return if (expected.isNotBlank() && MessageDigest.isEqual(
                                expected.toByteArray(Charsets.US_ASCII),
                                actual.toByteArray(Charsets.US_ASCII)
                            )) HostKeyRepository.OK else HostKeyRepository.CHANGED
                    }
                    override fun add(hostkey: HostKey?, ui: UserInfo?) { /* pinned key only */ }
                    override fun remove(host: String?, type: String?) { }
                    override fun remove(host: String?, type: String?, key: ByteArray?) { }
                    override fun getKnownHostsRepositoryID(): String = "Nexora host key pin"
                    override fun getHostKey(): Array<HostKey> = emptyArray()
                    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
                }
                val jsch = JSch()
                jsch.setHostKeyRepository(repo)
                val client = jsch.getSession(profile.username, profile.host, profile.port)
                client.setConfig("StrictHostKeyChecking", "yes")
                client.setConfig("PreferredAuthentications", "password")
                client.setPassword(profile.password)
                client.setTimeout(10000)
                client.setServerAliveInterval(12000)
                client.setServerAliveCountMax(2)
                try {
                    client.connect(11000)
                    val sock = ServerSocket(
                        0, 32, InetAddress.getByName("127.0.0.1"))
                    session = client
                    socks = sock
                    localPort = sock.localPort
                    running = true
                    handlers.execute { acceptLoop(sock, client) }
                    post("SSH autenticado • SOCKS5 127.0.0.1:" + localPort +
                        " • sem VPN de sistema", callback)
                } catch (error: Exception) {
                    try { client.disconnect() } catch (_: Exception) { }
                    throw error
                }
            } catch (error: Exception) {
                closeInternal()
                val detail = if (lastFingerprint.isNotBlank()) {
                    " Identidade SSH observada: " + lastFingerprint +
                    ". Confirme essa chave com o administrador antes de confiar."
                } else ""
                post("SSH não conectado (" + error.javaClass.simpleName +
                    "). Verifique a rede, o servidor, a conta e o fingerprint." + detail, callback)
            }
        }
    }

    fun disconnect(callback: (String) -> Unit) {
        worker.execute {
            closeInternal()
            post("SSH desconectado", callback)
        }
    }

    private fun closeInternal() {
        running = false
        try { socks?.close() } catch (_: Exception) { }
        socks = null
        localPort = -1
        synchronized(activeClients) {
            activeClients.toList().forEach { try { it.close() } catch (_: Exception) { } }
            activeClients.clear()
        }
        synchronized(openedChannels) {
            openedChannels.toList().forEach { try { it.disconnect() } catch (_: Exception) { } }
            openedChannels.clear()
        }
        try { session?.disconnect() } catch (_: Exception) { }
        session = null
    }

    private fun acceptLoop(server: ServerSocket, ssh: Session) {
        while (running && ssh.isConnected && !server.isClosed) {
            try {
                val socket = server.accept()
                if (connections.get() >= 32) {
                    socket.close()
                    continue
                }
                connections.incrementAndGet()
                activeClients.add(socket)
                handlers.execute {
                    try { handle(socket, ssh) }
                    catch (_: Exception) { }
                    finally {
                        activeClients.remove(socket)
                        try { socket.close() } catch (_: Exception) { }
                        connections.decrementAndGet()
                    }
                }
            } catch (_: Exception) {
                if (server.isClosed) break
            }
        }
    }

    private fun handle(client: Socket, ssh: Session) {
        client.soTimeout = 12000
        client.tcpNoDelay = true
        val fromClient = DataInputStream(client.getInputStream())
        val toClient = client.getOutputStream()
        if (fromClient.readUnsignedByte() != 5) return
        val nMethods = fromClient.readUnsignedByte()
        if (nMethods !in 1..32) return
        val methods = ByteArray(nMethods)
        fromClient.readFully(methods)
        if (methods.none { (it.toInt() and 0xff) == 0 }) {
            toClient.write(byteArrayOf(5, -1))
            return
        }
        toClient.write(byteArrayOf(5, 0))
        toClient.flush()
        val version = fromClient.readUnsignedByte()
        val command = fromClient.readUnsignedByte()
        fromClient.readUnsignedByte() // reserved
        val atyp = fromClient.readUnsignedByte()
        if (version != 5 || command != 1) {
            reject(toClient, 7)
            return
        }
        val destination = when (atyp) {
            1 -> {
                val bytes = ByteArray(4)
                fromClient.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress.orEmpty()
            }
            3 -> {
                val length = fromClient.readUnsignedByte()
                if (length !in 1..253) return
                val bytes = ByteArray(length)
                fromClient.readFully(bytes)
                String(bytes, Charsets.US_ASCII)
            }
            4 -> {
                val bytes = ByteArray(16)
                fromClient.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress.orEmpty()
            }
            else -> {
                reject(toClient, 8)
                return
            }
        }
        val port = fromClient.readUnsignedShort()
        if (destination.isBlank() || port !in 1..65535 || !ssh.isConnected) {
            reject(toClient, 4)
            return
        }
        val channel = try {
            ssh.getStreamForwarder(destination, port)
        } catch (_: Exception) {
            reject(toClient, 4)
            return
        }
        openedChannels.add(channel)
        try {
            val remoteInput = channel.inputStream
            val remoteOutput = channel.outputStream
            try { channel.connect(10000) }
            catch (_: Exception) {
                reject(toClient, 5)
                return
            }
            toClient.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            toClient.flush()
            client.soTimeout = 0
            val upstream = handlers.submit {
                try { pump(fromClient, remoteOutput) }
                finally { try { remoteOutput.close() } catch (_: Exception) { } }
            }
            try { pump(remoteInput, toClient) }
            finally {
                upstream.cancel(true)
            }
        } finally {
            openedChannels.remove(channel)
            try { channel.disconnect() } catch (_: Exception) { }
        }
    }

    private fun reject(output: OutputStream, code: Int) {
        output.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (running) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) {
                output.write(buffer, 0, n)
                output.flush()
            }
        }
    }

    /**
     * End-to-end check: explicitly sends SOCKS5 CONNECT into the local SSH gateway
     * and negotiates HTTPS over that very socket. Does not risk a platform API
     * silently bypassing a SOCKS proxy and giving a false-positive IP result.
     */
    fun verify(callback: (String) -> Unit) {
        handlers.execute {
            val port = localPort
            val current = session
            if (port <= 0 || current?.isConnected != true) {
                post("SSH não está conectado. Faça login antes do teste.", callback)
                return@execute
            }
            val result = try {
                Socket().use { raw ->
                    raw.connect(InetSocketAddress("127.0.0.1", port), 5000)
                    raw.soTimeout = 8500
                    val input = DataInputStream(raw.getInputStream())
                    val output = raw.getOutputStream()
                    output.write(byteArrayOf(5, 1, 0))
                    output.flush()
                    require(input.readUnsignedByte() == 5 &&
                        input.readUnsignedByte() == 0) { "SOCKS5 recusou autenticação." }
                    val hostname = "api64.ipify.org".toByteArray(Charsets.US_ASCII)
                    output.write(byteArrayOf(5, 1, 0, 3, hostname.size.toByte()))
                    output.write(hostname)
                    output.write(byteArrayOf(1, -69)) // TCP 443 (0x01BB)
                    output.flush()
                    require(input.readUnsignedByte() == 5 &&
                        input.readUnsignedByte() == 0) { "Servidor SSH recusou encaminhamento TCP." }
                    val remainder = ByteArray(8)
                    input.readFully(remainder)
                    val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(raw, "api64.ipify.org", 443, false) as SSLSocket
                    tls.use { secure ->
                        secure.soTimeout = 8500
                        val params = secure.sslParameters
                        params.endpointIdentificationAlgorithm = "HTTPS"
                        secure.sslParameters = params
                        secure.startHandshake()
                        val request = (
                            "GET / HTTP/1.1\r\nHost: api64.ipify.org\r\n" +
                            "Accept: text/plain\r\nConnection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                        secure.outputStream.write(request)
                        secure.outputStream.flush()
                        val reader = secure.inputStream.bufferedReader(Charsets.US_ASCII)
                        val response = reader.readLine()?.take(100).orEmpty()
                        require(response.contains(" 200 ")) {
                            "Resposta HTTPS inesperada: " + response.take(60)
                        }
                        for (i in 0..48) {
                            if (reader.readLine().isNullOrEmpty()) break
                        }
                        val ip = reader.readLine()?.trim()?.take(80).orEmpty()
                        if (ip.matches(Regex("[a-fA-F0-9.:]{3,45}")))
                            "SSH SOCKS5 e HTTPS confirmados; IP de saída: " + ip +
                                ". Outros aplicativos Android não passam por este proxy nesta versão."
                        else "SSH SOCKS5 e HTTPS responderam, mas o IP não pôde ser validado."
                    }
                }
            } catch (error: Exception) {
                "Teste SSH/Proxy falhou (" + error.javaClass.simpleName +
                    "). Verifique se a conta permite direct-tcpip e se o servidor é alcançável."
            }
            post(result, callback)
        }
    }
}
