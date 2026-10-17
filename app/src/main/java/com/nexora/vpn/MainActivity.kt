package com.nexora.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(6, 17, 28)
    private val surface = Color.rgb(16, 37, 53)
    private val secondary = Color.rgb(146, 168, 184)
    private val white = Color.rgb(240, 249, 251)
    private val mint = Color.rgb(70, 227, 190)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var switchButton: Button
    private lateinit var power: ImageView
    private lateinit var currentProfile: TextView
    private lateinit var currentNetwork: TextView
    private lateinit var down: TextView
    private lateinit var up: TextView
    private lateinit var statePill: TextView
    private lateinit var heroCircle: FrameLayout
    private lateinit var importButton: Button
    private lateinit var deleteButton: TextView
    private var disclosed = false

    private val stateListener: (VpnSession.State, String) -> Unit = { s, message ->
        if (::status.isInitialized) renderState(s, message)
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!isFinishing && ::down.isInitialized) {
                currentNetwork.text = networkName()
                if (VpnSession.state == VpnSession.State.CONNECTED) {
                    VpnSession.traffic { rx, tx ->
                        if (!isFinishing) {
                            down.text = readableBytes(rx)
                            up.text = readableBytes(tx)
                        }
                    }
                }
                mainHandler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildLayout()
        renderState(VpnSession.state, VpnSession.details)
        refreshProfile()
        VpnSession.observe(stateListener)
        mainHandler.post(poll)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(poll)
        VpnSession.unobserve(stateListener)
        super.onDestroy()
    }

    private fun buildLayout() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(34))
        }
        scroll.addView(body, ScrollView.LayoutParams(-1, -2))
        setContentView(scroll)

        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val markBg = FrameLayout(this).apply { background = rounded(Color.rgb(19, 55, 69), 17) }
        val brandMark = ImageView(this).apply { setImageResource(R.drawable.ic_brand) }
        markBg.addView(brandMark, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        brandRow.addView(markBg, LinearLayout.LayoutParams(dp(56), dp(56)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brandText.addView(text("NEXORA", 24f, white, true, 1f))
        brandText.addView(text("PRIVATE NETWORK", 10f, secondary, true, 1.8f))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val info = text("ⓘ", 25f, mint, false).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(21, 45, 62), 50)
            setOnClickListener { about() }
        }
        brandRow.addView(info, LinearLayout.LayoutParams(dp(46), dp(46)))
        body.addView(brandRow)
        spacer(body, 32)

        val greeting = text("SUA CONEXÃO, SUAS REGRAS", 10f, mint, true, 2.0f)
        body.addView(greeting)
        spacer(body, 7)
        body.addView(text("Navegue com mais\nprivacidade.", 31f, white, true, 0f))
        spacer(body, 24)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = gradient(Color.rgb(18, 56, 75), Color.rgb(13, 31, 51), 27)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        body.addView(hero, LinearLayout.LayoutParams(-1, -2))
        statePill = text("OFFLINE", 11f, secondary, true, 1.6f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(17), dp(8), dp(17), dp(8))
            background = rounded(Color.rgb(26, 47, 65), 28)
        }
        hero.addView(statePill)
        spacer(hero, 21)

        heroCircle = FrameLayout(this).apply {
            background = ring(0xFF255267.toInt(), 105)
            isClickable = true
            setOnClickListener { toggleVpn() }
        }
        val midRing = FrameLayout(this).apply { background = ring(0xFF31847F.toInt(), 91) }
        heroCircle.addView(midRing, FrameLayout.LayoutParams(dp(176), dp(176), Gravity.CENTER))
        val core = FrameLayout(this).apply { background = gradient(0xFF69FFD2.toInt(), 0xFF35C9B4.toInt(), 80) }
        midRing.addView(core, FrameLayout.LayoutParams(dp(130), dp(130), Gravity.CENTER))
        power = ImageView(this).apply { setImageResource(R.drawable.ic_power); imageTintList = ColorStateList.valueOf(bg) }
        core.addView(power, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        hero.addView(heroCircle, LinearLayout.LayoutParams(dp(218), dp(218)))
        spacer(hero, 13)
        status = text("Desconectado", 23f, white, true).apply { gravity = Gravity.CENTER }
        caption = text("Importe um perfil de servidor para começar", 12f, secondary).apply { gravity = Gravity.CENTER }
        hero.addView(status)
        spacer(hero, 6)
        hero.addView(caption)
        spacer(hero, 20)

        switchButton = Button(this).apply {
            text = "CONECTAR AGORA"
            setTextColor(bg)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            elevation = dp(0).toFloat()
            background = rounded(mint, 16)
            setOnClickListener { toggleVpn() }
        }
        hero.addView(switchButton, LinearLayout.LayoutParams(-1, dp(53)))
        spacer(body, 17)

        val networkCard = card(body)
        val netTop = row()
        netTop.addView(text("◉", 18f, mint, true))
        val netTitles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        netTitles.addView(text("REDE ATUAL", 10f, secondary, true, 1.2f))
        currentNetwork = text(networkName(), 16f, white, true)
        netTitles.addView(currentNetwork)
        netTop.addView(netTitles, LinearLayout.LayoutParams(0, -2, 1f))
        networkCard.addView(netTop)
        spacer(networkCard, 12)
        networkCard.addView(text("Compatível com Wi-Fi e dados móveis quando a rede permite conectividade ao servidor VPN.", 12f, secondary))
        spacer(body, 15)

        val serverCard = card(body)
        val titleRow = row()
        titleRow.addView(text("PERFIL DE CONEXÃO", 11f, secondary, true, 1.2f), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(text("WireGuard", 12f, mint, true))
        serverCard.addView(titleRow)
        spacer(serverCard, 12)
        currentProfile = text("Nenhum servidor configurado", 16f, white, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        serverCard.addView(currentProfile)
        spacer(serverCard, 5)
        serverCard.addView(text("Arquivo .conf fornecido pelo administrador do servidor.", 12f, secondary))
        spacer(serverCard, 14)
        importButton = Button(this).apply {
            text = "＋  Importar perfil"
            isAllCaps = false
            textSize = 14f
            setTextColor(mint)
            background = outlined(Color.rgb(48, 90, 95), 13)
            setOnClickListener { importConfig() }
        }
        serverCard.addView(importButton, LinearLayout.LayoutParams(-1, dp(48)))
        spacer(serverCard, 8)
        deleteButton = text("Remover perfil", 12f, secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
            setOnClickListener { removeProfile() }
        }
        serverCard.addView(deleteButton)
        spacer(body, 15)

        val statsCard = card(body)
        statsCard.addView(text("TRÁFEGO DA VPN", 11f, secondary, true, 1.1f))
        spacer(statsCard, 13)
        val statsRow = row()
        fun metric(title: String): Pair<LinearLayout, TextView> {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(text(title, 11f, secondary))
            val value = text("0 B", 21f, white, true)
            c.addView(value)
            return c to value
        }
        val (downBox, downVal) = metric("↓  Recebidos")
        val (upBox, upVal) = metric("↑  Enviados")
        down = downVal
        up = upVal
        statsRow.addView(downBox, LinearLayout.LayoutParams(0, -2, 1f))
        statsRow.addView(upBox, LinearLayout.LayoutParams(0, -2, 1f))
        statsCard.addView(statsRow)
        spacer(body, 24)
        val footer = text("NEXORA VPN  ·  v0.1 TEST  ·  PRIVACIDADE EM PRIMEIRO LUGAR", 9f, secondary, true, 0.4f)
        footer.gravity = Gravity.CENTER
        body.addView(footer)
    }

    private fun toggleVpn() {
        when (VpnSession.state) {
            VpnSession.State.CONNECTED -> VpnSession.disconnect()
            VpnSession.State.CONNECTING, VpnSession.State.DISCONNECTING -> Unit
            else -> {
                if (!EncryptedProfileStore.exists(this)) {
                    AlertDialog.Builder(this).setTitle("Configure um servidor")
                        .setMessage("Importe primeiro um arquivo .conf de um servidor WireGuard autorizado. A VPN não cria acesso gratuito à operadora.")
                        .setPositiveButton("Importar") { _, _ -> importConfig() }
                        .setNegativeButton("Cancelar", null).show()
                    return
                }
                val prefs = getSharedPreferences("nexora", MODE_PRIVATE)
                if (!prefs.getBoolean("disclosureAccepted", false)) showDisclosure()
                else requestVpnPermission()
            }
        }
    }

    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Como a Nexora VPN funciona")
            .setMessage("Ao conectar, a Nexora VPN estabelece um túnel criptografado e encaminha o tráfego do dispositivo para o servidor WireGuard que você escolheu. O aplicativo recebe metadados técnicos da conexão para mostrar o estado e os contadores de tráfego. Não vendemos dados nem coletamos seu histórico de navegação. A VPN não fornece franquia de dados móveis e o provedor do servidor pode observar metadados de tráfego. Deseja continuar?")
            .setPositiveButton("Aceitar e continuar") { _, _ ->
                getSharedPreferences("nexora", MODE_PRIVATE).edit().putBoolean("disclosureAccepted", true).apply()
                requestVpnPermission()
            }.setNegativeButton("Agora não", null).show()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent == null) VpnSession.connect()
        else startActivityForResult(intent, REQ_VPN)
    }

    private fun importConfig() {
        if (VpnSession.state == VpnSession.State.CONNECTED) {
            AlertDialog.Builder(this).setTitle("Desconecte primeiro")
                .setMessage("Encerre a VPN antes de trocar o perfil para não interromper a sessão em andamento.")
                .setPositiveButton("OK", null).show()
            return
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(picker, REQ_IMPORT)
    }

    @Deprecated("Legacy activity result used to avoid an additional runtime dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_VPN) { VpnSession.connect(); return }
        if (requestCode == REQ_IMPORT) {
            val uri = data?.data ?: return
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readNBytesCompat(65537)
                } ?: error("Arquivo não pôde ser lido")
                require(bytes.size <= 65536) { "Arquivo muito grande" }
                EncryptedProfileStore.save(this, bytes)
                bytes.fill(0)
                val label = uri.lastPathSegment?.substringAfterLast('/')?.take(60) ?: "Meu servidor"
                getSharedPreferences("nexora", MODE_PRIVATE).edit().putString("profileName", label).apply()
                refreshProfile()
                AlertDialog.Builder(this).setTitle("Perfil importado")
                    .setMessage("Configuração validada e armazenada de forma criptografada. Já é possível tentar conectar ao seu servidor.")
                    .setPositiveButton("OK", null).show()
            } catch (_: Exception) {
                AlertDialog.Builder(this).setTitle("Perfil inválido")
                    .setMessage("Não foi possível importar o arquivo. Confirme que é um .conf WireGuard válido.")
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun refreshProfile() {
        val available = EncryptedProfileStore.exists(this)
        currentProfile.text = if (available) getSharedPreferences("nexora", MODE_PRIVATE)
            .getString("profileName", "Meu servidor WireGuard") else "Nenhum servidor configurado"
        deleteButton.visibility = if (available) View.VISIBLE else View.GONE
    }

    private fun removeProfile() {
        AlertDialog.Builder(this).setTitle("Remover servidor?")
            .setMessage("A configuração e a chave privada armazenadas serão removidas deste aparelho.")
            .setPositiveButton("Remover") { _, _ ->
                EncryptedProfileStore.remove(this)
                refreshProfile()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun about() {
        AlertDialog.Builder(this).setTitle("Nexora VPN · Primeira versão")
            .setMessage("Cliente WireGuard Android independente.\n\n• Funciona em redes Wi-Fi e móveis com conectividade.\n• Requer um servidor VPN configurado e autorizado.\n• Não desbloqueia internet sem crédito e não supera bloqueios de operadoras automaticamente.\n• Os dados de sessão ficam protegidos pelo Android Keystore.\n\nEsta é uma versão inicial para testes e ainda não foi homologada para publicação.")
            .setPositiveButton("Entendi", null).show()
    }

    private fun renderState(state: VpnSession.State, message: String) {
        val active = state == VpnSession.State.CONNECTED
        val busy = state == VpnSession.State.CONNECTING || state == VpnSession.State.DISCONNECTING
        status.text = when (state) {
            VpnSession.State.CONNECTED -> "Túnel ativo"
            VpnSession.State.CONNECTING -> "Conectando…"
            VpnSession.State.DISCONNECTING -> "Desconectando…"
            VpnSession.State.ERROR -> "Falha de conexão"
            else -> "Desconectado"
        }
        caption.text = message
        statePill.text = when (state) {
            VpnSession.State.CONNECTED -> "● PROTEÇÃO ATIVA"
            VpnSession.State.CONNECTING -> "● CONECTANDO"
            VpnSession.State.DISCONNECTING -> "● DESCONECTANDO"
            VpnSession.State.ERROR -> "● VERIFIQUE O PERFIL"
            else -> "● DESCONECTADO"
        }
        statePill.setTextColor(if (active) mint else secondary)
        switchButton.text = when (state) {
            VpnSession.State.CONNECTED -> "DESCONECTAR"
            VpnSession.State.CONNECTING -> "ESTABELECENDO TÚNEL…"
            VpnSession.State.DISCONNECTING -> "ENCERRANDO TÚNEL…"
            else -> "CONECTAR AGORA"
        }
        switchButton.isEnabled = !busy
        switchButton.alpha = if (busy) 0.65f else 1f
        power.alpha = if (active || !busy) 1f else 0.65f
        if (!active) { down.text = "0 B"; up.text = "0 B" }
    }

    private fun networkName(): String {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        return when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi conectado"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Rede móvel conectada"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet conectada"
            else -> "Sem rede identificada"
        }
    }
    private fun readableBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun spacer(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = spacing / 10f
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun card(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2))
        return card
    }
    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    }
    private fun gradient(start: Int, end: Int, radius: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }
    private fun outlined(stroke: Int, radius: Int): GradientDrawable {
        return rounded(Color.rgb(19, 50, 62), radius).apply { setStroke(dp(1), stroke) }
    }
    private fun ring(stroke: Int, radius: Int): GradientDrawable {
        return rounded(Color.rgb(19, 57, 72), radius).apply { setStroke(dp(2), stroke) }
    }
    companion object { private const val REQ_VPN = 90; private const val REQ_IMPORT = 91 }
}
