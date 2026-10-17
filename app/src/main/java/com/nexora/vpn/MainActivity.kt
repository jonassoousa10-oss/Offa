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
    private lateinit var operatorButton: Button
    private lateinit var serverButton: Button
    private lateinit var directoryStatus: TextView
    private var allServers: List<ServerDirectory.Server> = emptyList()
    private var selectedServerId: String? = null
    private var loadingServers = false
    private var provisioning = false
    private val carriers = listOf("Automática", "Vivo", "Claro", "TIM", "Oi", "Outra")
    private val carrierIds = listOf("auto", "vivo", "claro", "tim", "oi", "other")
    private var carrierIndex = 0

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
        carrierIndex = getSharedPreferences("nexora_settings", MODE_PRIVATE)
            .getInt("carrierIndex", 0).coerceIn(0, carriers.lastIndex)
        selectedServerId = getSharedPreferences("nexora_settings", MODE_PRIVATE)
            .getString("serverId", null)
        loadServers()
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
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
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
        caption = text("Selecione uma operadora e um servidor", 12f, secondary).apply { gravity = Gravity.CENTER }
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
        serverCard.addView(text("SELECIONE SUA REDE", 11f, secondary, true, 1.2f))
        spacer(serverCard, 12)
        serverCard.addView(text("Operadora", 12f, secondary))
        spacer(serverCard, 6)
        operatorButton = Button(this).apply {
            isAllCaps = false
            setTextColor(white)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            background = outlined(Color.rgb(48, 90, 95), 13)
            setPadding(dp(15), 0, dp(15), 0)
            setOnClickListener { chooseCarrier() }
        }
        serverCard.addView(operatorButton, LinearLayout.LayoutParams(-1, dp(50)))
        spacer(serverCard, 13)
        serverCard.addView(text("Servidor", 12f, secondary))
        spacer(serverCard, 6)
        serverButton = Button(this).apply {
            isAllCaps = false
            setTextColor(white)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            background = outlined(Color.rgb(48, 90, 95), 13)
            setPadding(dp(15), 0, dp(15), 0)
            setOnClickListener { chooseServer() }
        }
        serverCard.addView(serverButton, LinearLayout.LayoutParams(-1, dp(50)))
        spacer(serverCard, 10)
        currentProfile = text("Sem servidor selecionado", 12f, mint)
        serverCard.addView(currentProfile)
        spacer(serverCard, 10)
        directoryStatus = text("Carregando servidores…", 12f, secondary)
        serverCard.addView(directoryStatus)
        spacer(serverCard, 14)
        val actions = row()
        val refresh = Button(this).apply {
            text = "↻ Atualizar"
            isAllCaps = false
            textSize = 12f
            setTextColor(mint)
            background = outlined(Color.rgb(48, 90, 95), 13)
            setOnClickListener { loadServers() }
        }
        actions.addView(refresh, LinearLayout.LayoutParams(0, dp(45), 1f))
        val admin = Button(this).apply {
            text = "⚙ Serviço"
            isAllCaps = false
            textSize = 12f
            setTextColor(mint)
            background = outlined(Color.rgb(48, 90, 95), 13)
            setOnClickListener { configureDirectory() }
        }
        actions.addView(admin, LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(8) })
        serverCard.addView(actions)
        refreshControls()
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
        val footer = text("NEXORA VPN  ·  v0.2 TEST  ·  PRIVACIDADE EM PRIMEIRO LUGAR", 9f, secondary, true, 0.4f)
        footer.gravity = Gravity.CENTER
        body.addView(footer)
    }

    private fun toggleVpn() {
        if (provisioning) return
        when (VpnSession.state) {
            VpnSession.State.CONNECTED -> VpnSession.disconnect()
            VpnSession.State.CONNECTING, VpnSession.State.DISCONNECTING -> Unit
            else -> {
                if (chosenServer() == null) {
                    AlertDialog.Builder(this).setTitle("Selecione um servidor")
                        .setMessage("Nenhum servidor Nexora está disponível. Atualize a lista ou configure o endereço do serviço com seu administrador. Não é necessário importar arquivos.")
                        .setPositiveButton("OK", null).show()
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
        if (intent == null) beginProvision()
        else startActivityForResult(intent, REQ_VPN)
    }

    @Deprecated("Legacy result callback for Android VPN authorization")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) beginProvision()
    }

    private fun chosenServer(): ServerDirectory.Server? =
        allServers.firstOrNull { it.id == selectedServerId && it.supports(carrierIds[carrierIndex]) }

    private fun availableServers(): List<ServerDirectory.Server> =
        allServers.filter { it.supports(carrierIds[carrierIndex]) }

    private fun refreshControls() {
        if (!::operatorButton.isInitialized || !::serverButton.isInitialized) return
        operatorButton.text = carriers[carrierIndex] + "  ▾"
        val chosen = chosenServer()
        serverButton.text = (chosen?.name ?: "Selecionar servidor") + "  ▾"
        serverButton.isEnabled = !loadingServers && availableServers().isNotEmpty() &&
            VpnSession.state != VpnSession.State.CONNECTED && !provisioning
        currentProfile.text = if (chosen != null) chosen.region + " • " + chosen.name
            else "Escolha um servidor disponível para sua operadora."
    }

    private fun chooseCarrier() {
        if (VpnSession.state == VpnSession.State.CONNECTED || provisioning) {
            AlertDialog.Builder(this).setMessage("Desconecte antes de trocar a operadora.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Escolha sua operadora")
            .setSingleChoiceItems(carriers.toTypedArray(), carrierIndex) { dialog, index ->
                carrierIndex = index
                getSharedPreferences("nexora_settings", MODE_PRIVATE).edit()
                    .putInt("carrierIndex", index).apply()
                if (chosenServer() == null) selectedServerId = availableServers().firstOrNull()?.id
                refreshControls()
                dialog.dismiss()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun chooseServer() {
        if (VpnSession.state == VpnSession.State.CONNECTED || provisioning) {
            AlertDialog.Builder(this).setMessage("Desconecte antes de mudar de servidor.")
                .setPositiveButton("OK", null).show()
            return
        }
        val options = availableServers()
        if (options.isEmpty()) {
            loadServers()
            return
        }
        AlertDialog.Builder(this).setTitle("Escolha seu servidor")
            .setItems(options.map { it.name + " — " + it.region }.toTypedArray()) { _, index ->
                selectedServerId = options[index].id
                getSharedPreferences("nexora_settings", MODE_PRIVATE).edit()
                    .putString("serverId", selectedServerId).apply()
                refreshControls()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun loadServers() {
        if (!::directoryStatus.isInitialized || loadingServers) return
        val endpoint = ServerDirectory.url(this)
        if (endpoint.isBlank()) {
            allServers = emptyList()
            directoryStatus.text = "Serviço ainda não configurado. Toque em ⚙ Serviço (administrador)."
            refreshControls()
            return
        }
        loadingServers = true
        directoryStatus.text = "Consultando servidores seguros…"
        refreshControls()
        Thread {
            val result = runCatching { ServerDirectory.list(applicationContext) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingServers = false
                allServers = result.getOrDefault(emptyList())
                if (chosenServer() == null) {
                    selectedServerId = availableServers().firstOrNull()?.id
                    getSharedPreferences("nexora_settings", MODE_PRIVATE).edit()
                        .putString("serverId", selectedServerId).apply()
                }
                directoryStatus.text = if (result.isFailure)
                    "Não foi possível consultar o serviço. Verifique o endereço ou a conexão."
                else if (allServers.isEmpty())
                    "Nenhum servidor publicado pelo administrador."
                else
                    allServers.size.toString() + " servidor(es) no catálogo • configuração automática"
                refreshControls()
            }
        }.start()
    }

    private fun configureDirectory() {
        if (VpnSession.state == VpnSession.State.CONNECTED || provisioning) {
            AlertDialog.Builder(this).setMessage("Desconecte antes de alterar o serviço.")
                .setPositiveButton("OK", null).show()
            return
        }
        val input = android.widget.EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://api.seu-dominio.com"
            setText(ServerDirectory.url(this@MainActivity))
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        AlertDialog.Builder(this).setTitle("Serviço Nexora • administrador")
            .setMessage("Informe o endereço HTTPS do catálogo e provisionamento Nexora. Seus usuários não precisarão importar configurações.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val result = runCatching { ServerDirectory.setUrl(this, input.text.toString()) }
                if (result.isFailure) {
                    AlertDialog.Builder(this).setMessage("Use um endereço HTTPS válido.")
                        .setPositiveButton("OK", null).show()
                } else {
                    allServers = emptyList()
                    selectedServerId = null
                    loadServers()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun beginProvision() {
        if (provisioning || VpnSession.state == VpnSession.State.CONNECTED) return
        val server = chosenServer() ?: return
        provisioning = true
        status.text = "Preparando conexão…"
        caption.text = "Solicitando configuração segura ao servidor"
        switchButton.text = "AGUARDE…"
        switchButton.isEnabled = false
        refreshControls()
        val operator = carrierIds[carrierIndex]
        Thread {
            val result = runCatching {
                val bytes = ServerDirectory.provision(applicationContext, server, operator)
                try { EncryptedProfileStore.save(applicationContext, bytes) }
                finally { bytes.fill(0) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                provisioning = false
                if (result.isSuccess) VpnSession.connect()
                else renderState(VpnSession.State.ERROR,
                    "O servidor não conseguiu preparar a VPN. Verifique a configuração do serviço.")
                refreshControls()
            }
        }.start()
    }

        private fun about() {
        AlertDialog.Builder(this).setTitle("Nexora VPN · Primeira versão")
            .setMessage("Nexora VPN v0.2 de teste.\n\nEscolha operadora e servidor. O administrador publica servidores autorizados por HTTPS. O telefone cria sua chave privada localmente.\n\nA VPN precisa de uma rede com acesso ao servidor e não fornece dados móveis gratuitos. Não inclui servidores ativos nesta versão de teste.")
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
            VpnSession.State.ERROR -> "● ERRO DE CONEXÃO"
            else -> "● DESCONECTADO"
        }
        statePill.setTextColor(if (active) mint else secondary)
        switchButton.text = when (state) {
            VpnSession.State.CONNECTED -> "DESCONECTAR"
            VpnSession.State.CONNECTING -> "ESTABELECENDO TÚNEL…"
            VpnSession.State.DISCONNECTING -> "ENCERRANDO TÚNEL…"
            else -> "CONECTAR AGORA"
        }
        switchButton.isEnabled = !busy && !provisioning
        switchButton.alpha = if (busy) 0.65f else 1f
        power.alpha = if (active || !busy) 1f else 0.65f
        if (!active) { down.text = "0 B"; up.text = "0 B" }
        refreshControls()
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
