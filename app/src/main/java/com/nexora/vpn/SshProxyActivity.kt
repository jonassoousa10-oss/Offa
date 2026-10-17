package com.nexora.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Experimental standalone SSH/Proxy test screen. Does not hijack WireGuard
 * and does not capture other apps' traffic. No third-party free SSH profiles.
 */
class SshProxyActivity : Activity() {
    private val dark = Color.rgb(6, 17, 28)
    private val panel = Color.rgb(16, 37, 53)
    private val green = Color.rgb(70, 227, 190)
    private val white = Color.rgb(240, 249, 251)
    private val gray = Color.rgb(155, 178, 196)
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var login: EditText
    private lateinit var password: EditText
    private lateinit var fingerprint: EditText
    private lateinit var result: TextView
    private lateinit var connect: Button
    private lateinit var test: Button
    private lateinit var disconnect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        makeUi()
    }

    override fun onResume() {
        super.onResume()
        if (::result.isInitialized) {
            result.text = SshProxyEngine.state
            updateControls()
        }
    }

    private fun makeUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(dark)
            isFillViewport = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(36), dp(22), dp(30))
        }
        scroll.addView(body)
        setContentView(scroll)
        val header = TextView(this).apply {
            text = "‹  NEXORA • SSH / PROXY"
            textSize = 20f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        }
        body.addView(header)
        space(body, 14)
        val intro = group(body)
        intro.addView(label("Motor SSH2 + SOCKS5 • v0.4", 15f, green, true))
        space(intro, 7)
        intro.addView(label(
            "Conecta de verdade a um SSH autorizado e cria um proxy SOCKS5 só no seu aparelho. " +
            "Nesta versão experimental, isso ainda NÃO fornece VPN para WhatsApp, Telegram ou outros apps.",
            13f, white))
        space(body, 14)

        val inputs = group(body)
        inputs.addView(label("SERVIDOR SSH AUTORIZADO", 12f, green, true))
        space(inputs, 8)
        host = field(inputs, "Servidor (ex.: ssh.exemplo.com)", false, "endereço SSH")
        port = field(inputs, "Porta SSH: 22", false, "porta SSH")
        port.inputType = InputType.TYPE_CLASS_NUMBER
        port.setText("22")
        login = field(inputs, "Usuário SSH", false, "usuário")
        password = field(inputs, "Senha SSH", true, "senha")
        fingerprint = field(inputs, "Fingerprint SHA256:…", false, "fingerprint")
        inputs.addView(label(
            "Segurança: informe a impressão digital SHA256 da chave SSH fornecida pelo administrador. " +
            "Se não tiver, a tentativa mostrará a chave observada, mas a conexão será recusada " +
            "até você conferir e informar o valor correto.",
            12f, gray))
        space(inputs, 10)
        val paste = Button(this).apply {
            text = "Colar fingerprint"
            isAllCaps = false
            textSize = 12f
            setTextColor(green)
            background = outlined()
            setOnClickListener {
                val clip = (getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager).primaryClip
                val str = clip?.getItemAt(0)?.coerceToText(this@SshProxyActivity)?.toString()
                if (!str.isNullOrBlank()) fingerprint.setText(str.trim())
            }
        }
        inputs.addView(paste, LinearLayout.LayoutParams(-1, dp(44)))
        space(body, 14)

        val actions = group(body)
        connect = Button(this).apply {
            text = "CONECTAR SSH / PROXY"
            setTextColor(dark)
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(green)
            setOnClickListener { doConnect() }
        }
        actions.addView(connect, LinearLayout.LayoutParams(-1, dp(52)))
        space(actions, 9)
        test = Button(this).apply {
            text = "TESTAR ACESSO VIA SSH"
            setTextColor(green)
            isAllCaps = false
            background = outlined()
            setOnClickListener {
                result.text = "Conferindo tráfego real pelo SOCKS5…"
                SshProxyEngine.verify { message ->
                    result.text = message
                    updateControls()
                }
            }
        }
        actions.addView(test, LinearLayout.LayoutParams(-1, dp(50)))
        space(actions, 9)
        disconnect = Button(this).apply {
            text = "DESCONECTAR SSH"
            isAllCaps = false
            setTextColor(gray)
            background = outlined()
            setOnClickListener {
                SshProxyEngine.disconnect { message ->
                    result.text = message
                    updateControls()
                }
            }
        }
        actions.addView(disconnect, LinearLayout.LayoutParams(-1, dp(46)))
        space(actions, 12)
        result = label("Desconectado", 13f, white)
        actions.addView(result)
        space(body, 14)
        val footer = group(body)
        footer.addView(label("Importante", 13f, green, true))
        footer.addView(label(
            "Não usa chaves nem servidores do Rev Hunter. Sem anúncios falsos ou alteração do tempo " +
            "de outra empresa. SSH precisa de um servidor alcançável e credenciais próprias. " +
            "O SOCKS5 nesta etapa só é usado no teste dentro do Nexora.",
            12f, gray))
        updateControls()
    }

    private fun doConnect() {
        if (SshProxyEngine.localPort > 0) {
            result.text = "Desconecte o SSH atual para trocar os dados."
            return
        }
        val selected = SshProxyEngine.Profile(
            host.text.toString().trim(),
            port.text.toString().trim().toIntOrNull() ?: 0,
            login.text.toString().trim(),
            password.text.toString(),
            fingerprint.text.toString().trim()
        )
        try { selected.validate() }
        catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("Dados inválidos")
                .setMessage(error.message ?: "Confira os dados SSH.")
                .setPositiveButton("OK", null).show()
            return
        }
        connect.isEnabled = false
        result.text = "Tentando conexão segura…"
        SshProxyEngine.connect(selected) { message ->
            result.text = message
            connect.isEnabled = true
            updateControls()
        }
    }

    private fun updateControls() {
        val connected = SshProxyEngine.localPort > 0
        if (::connect.isInitialized) connect.isEnabled = !connected
        if (::test.isInitialized) test.isEnabled = connected
        if (::disconnect.isInitialized) disconnect.isEnabled = connected
    }

    private fun group(parent: LinearLayout): LinearLayout {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel)
        }
        parent.addView(v, LinearLayout.LayoutParams(-1, -2))
        return v
    }
    private fun field(parent: LinearLayout, placeholder: String,
                      secret: Boolean, desc: String): EditText {
        val input = EditText(this).apply {
            hint = placeholder
            contentDescription = desc
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(gray)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            background = outlined()
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        parent.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        space(parent, 9)
        return input
    }
    private fun label(s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun rounded(c: Int) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(15).toFloat()
    }
    private fun outlined() = rounded(Color.rgb(21, 51, 63)).apply {
        setStroke(dp(1), Color.rgb(53, 94, 104))
    }
}
