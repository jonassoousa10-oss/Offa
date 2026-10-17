package com.nexora.vpn

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Offline UI and read-only local diagnostics. Nothing is uploaded to Nexora.
 * All requests are performed by the selected default mobile-data SIM, not Wi-Fi.
 */
class DiagnosticsActivity : Activity() {
    private val bg = Color.rgb(6, 17, 28)
    private val surface = Color.rgb(16, 37, 53)
    private val mint = Color.rgb(70, 227, 190)
    private val secondary = Color.rgb(152, 173, 191)
    private val white = Color.rgb(241, 250, 252)
    private val yellow = Color.rgb(255, 202, 101)
    private val red = Color.rgb(255, 123, 137)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var address: EditText
    private lateinit var carrier: Spinner
    private lateinit var runButton: Button
    private lateinit var accessButton: Button
    private lateinit var copyButton: Button
    private lateinit var summary: TextView
    private val resultViews = linkedMapOf<String, Pair<TextView, TextView>>()
    private val findings = linkedMapOf<String, NetworkDiagnostic.Finding>()
    private val keys = listOf("cellular", "dns", "https", "internet")
    private val accessKeys = listOf("portal", "whatsapp", "telegram", "site")
    private val allKeys = keys + accessKeys
    private val titles = mapOf(
        "cellular" to "1. Rede móvel selecionada",
        "dns" to "2. Resolução DNS",
        "https" to "3. HTTPS e certificado TLS",
        "internet" to "4. Internet geral (2 destinos)",
        "portal" to "5. URL escolhida / Vivo",
        "whatsapp" to "6. Site WhatsApp",
        "telegram" to "7. Site Telegram",
        "site" to "8. Site independente / Wikipedia"
    )
    private val carriers = listOf("Vivo", "Claro", "TIM", "Oi", "Outra / Automática")
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val prefs = getSharedPreferences("nexora_diagnostics", MODE_PRIVATE)
        val screen = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    insets
                }
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
        }
        screen.addView(body)
        setContentView(screen)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = label("‹", 32f, mint).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
            contentDescription = "Voltar"
        }
        heading.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val headingText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingText.addView(label("DIAGNÓSTICO DE REDE", 19f, white, true))
        headingText.addView(label("NEXORA • Teste local pelo chip móvel", 11f, secondary))
        heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(heading)
        space(body, 19)

        val intro = panel(body)
        intro.addView(label("Sem servidor, sem Node.js, sem VPS", 15f, mint, true))
        space(intro, 6)
        intro.addView(label(
            "Este diagnóstico NÃO liga a VPN, não usa Wi-Fi e não encaminha seu tráfego pelo site informado. Ele apenas testa conectividade real pela rede móvel do Android.",
            12f, white))
        space(body, 16)

        val settings = panel(body)
        settings.addView(label("DOMÍNIO PARA TESTAR", 11f, secondary, true))
        space(settings, 8)
        address = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString("url", "https://www.vivo.com.br"))
            textSize = 14f
            setTextColor(white)
            setHintTextColor(secondary)
            hint = "https://www.vivo.com.br"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = outlined()
            setPadding(dp(13), dp(11), dp(13), dp(11))
            selectAll()
        }
        settings.addView(address, LinearLayout.LayoutParams(-1, dp(54)))
        space(settings, 13)
        settings.addView(label("OPERADORA (IDENTIFICAÇÃO)", 11f, secondary, true))
        space(settings, 7)
        carrier = Spinner(this).apply {
            background = outlined()
            val adapter = object : ArrayAdapter<String>(
                this@DiagnosticsActivity, android.R.layout.simple_spinner_dropdown_item, carriers
            ) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    return (super.getView(position, convertView, parent) as TextView).apply {
                        setTextColor(white)
                        textSize = 14f
                        setPadding(dp(14), dp(11), dp(12), dp(11))
                    }
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                        setTextColor(Color.BLACK)
                        textSize = 15f
                    }
                }
            }
            setAdapter(adapter)
            setSelection(prefs.getInt("carrier", 0).coerceIn(0, carriers.lastIndex))
        }
        settings.addView(carrier, LinearLayout.LayoutParams(-1, dp(52)))
        space(settings, 11)
        settings.addView(label(
            "A operadora acima é apenas uma etiqueta. Para escolher QUAL chip será testado, defina-o como chip de dados móveis nas configurações do Android. O app não altera seu SIM.",
            12f, secondary))
        space(settings, 12)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val systemSettings = Button(this).apply {
            text = "Escolher chip"
            isAllCaps = false
            setTextColor(mint)
            textSize = 12f
            background = outlined()
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
        buttons.addView(systemSettings, LinearLayout.LayoutParams(0, dp(46), 1f))
        val defaults = Button(this).apply {
            text = "URL Vivo"
            isAllCaps = false
            setTextColor(mint)
            textSize = 12f
            background = outlined()
            setOnClickListener {
                address.setText("https://www.vivo.com.br")
                carrier.setSelection(0)
            }
        }
        buttons.addView(defaults, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        settings.addView(buttons)
        space(body, 16)

        runButton = Button(this).apply {
            text = "INICIAR DIAGNÓSTICO"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            background = rounded(mint, 16)
            setOnClickListener { begin() }
        }
        body.addView(runButton, LinearLayout.LayoutParams(-1, dp(55)))
        space(body, 10)
        accessButton = Button(this).apply {
            text = "TESTAR ACESSO A APPS E SITES"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mint)
            background = outlined()
            setOnClickListener { beginDirectAccess() }
        }
        body.addView(accessButton, LinearLayout.LayoutParams(-1, dp(55)))
        space(body, 10)
        body.addView(label(
            "Teste direto pela rede móvel: URL escolhida, site WhatsApp, site Telegram e Wikipedia. Não envia mensagens, não usa o portal Vivo como túnel e não simula conexão.",
            12f, secondary))
        space(body, 14)

        val resultCard = panel(body)
        resultCard.addView(label("RESULTADOS", 12f, mint, true))
        space(resultCard, 8)
        summary = label("Nenhum teste executado neste aparelho.", 12f, secondary)
        resultCard.addView(summary)
        space(resultCard, 10)
        for (key in keys) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = label(titles[key].orEmpty() + "  •  Aguardando", 14f, white, true)
            val detail = label("—", 12f, secondary)
            line.addView(title)
            space(line, 5)
            line.addView(detail)
            resultCard.addView(line)
            resultViews[key] = title to detail
            space(resultCard, 18)
        }
        resultCard.addView(label("TESTE DIRETO DE ACESSO", 12f, mint, true))
        space(resultCard, 7)
        resultCard.addView(label(
            "Responder por HTTPS significa apenas que a página web é alcançável; não prova que mensagens de WhatsApp ou Telegram funcionem.",
            12f, secondary))
        space(resultCard, 14)
        for (key in accessKeys) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = label(titles[key].orEmpty() + "  •  Não testado", 14f, white, true)
            val detail = label("Toque em TESTAR ACESSO A APPS E SITES.", 12f, secondary)
            line.addView(title)
            space(line, 5)
            line.addView(detail)
            resultCard.addView(line)
            resultViews[key] = title to detail
            space(resultCard, 18)
        }
        copyButton = Button(this).apply {
            text = "Copiar relatório"
            isAllCaps = false
            textSize = 13f
            setTextColor(mint)
            background = outlined()
            isEnabled = false
            setOnClickListener { copyReport() }
        }
        resultCard.addView(copyButton, LinearLayout.LayoutParams(-1, dp(48)))
        space(body, 14)
        body.addView(label(
            "Os testes usam exclusivamente a rede celular solicitada ao Android, com DNS próprio da rede e HTTPS com certificado verificado. O sucesso do portal da Vivo não significa que internet geral ou VPN estejam disponíveis.",
            11f, secondary))
    }

    private fun begin() {
        if (working) return
        if (VpnSession.state == VpnSession.State.CONNECTED) {
            AlertDialog.Builder(this).setTitle("Desconecte a VPN antes")
                .setMessage("Desconecte o túnel Nexora para medir a conectividade real do seu chip sem interferência da VPN.")
                .setPositiveButton("Entendi", null).show()
            return
        }
        val target = try { NetworkDiagnostic.validate(address.text.toString()) }
        catch (e: IllegalArgumentException) {
            address.error = e.message
            address.requestFocus()
            return
        }
        getSharedPreferences("nexora_diagnostics", MODE_PRIVATE).edit()
            .putString("url", target.url.toString())
            .putInt("carrier", carrier.selectedItemPosition)
            .apply()
        working = true
        runButton.isEnabled = false
        accessButton.isEnabled = false
        runButton.text = "TESTANDO REDE MÓVEL…"
        copyButton.isEnabled = false
        findings.clear()
        summary.text = "Teste real em andamento. Wi-Fi e VPN não serão usados."
        for (key in allKeys) {
            resultViews[key]?.first?.apply {
                text = titles[key].orEmpty() +
                    (if (key in keys) "  •  Aguardando" else "  •  Não testado")
                setTextColor(white)
            }
            resultViews[key]?.second?.text =
                if (key in keys) "—" else "Execute o teste de acesso separadamente."
        }
        worker.execute {
            try {
                NetworkDiagnostic.run(applicationContext, target) { key, finding ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) applyFinding(key, finding)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        summary.text = "Teste interrompido: " + error.javaClass.simpleName
                    }
                }
            } finally {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        working = false
                        runButton.isEnabled = true
                        accessButton.isEnabled = true
                        runButton.text = "REPETIR DIAGNÓSTICO"
                        copyButton.isEnabled = findings.isNotEmpty()
                        if (findings.size == keys.size) {
                            summary.text = "Concluído • " +
                                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
                                    .format(Date()) +
                                " • Operadora informada: " + carrier.selectedItem.toString()
                        }
                    }
                }
            }
        }
    }


    private fun beginDirectAccess() {
        if (working) return
        if (VpnSession.state == VpnSession.State.CONNECTED) {
            AlertDialog.Builder(this).setTitle("Desconecte a VPN antes")
                .setMessage("O teste precisa medir a rede móvel sem interferência do túnel.")
                .setPositiveButton("OK", null).show()
            return
        }
        val target = try { NetworkDiagnostic.validate(address.text.toString()) }
        catch (error: IllegalArgumentException) {
            address.error = error.message
            address.requestFocus()
            return
        }
        getSharedPreferences("nexora_diagnostics", MODE_PRIVATE).edit()
            .putString("url", target.url.toExternalForm())
            .putInt("carrier", carrier.selectedItemPosition)
            .apply()
        findings.clear()
        working = true
        runButton.isEnabled = false
        accessButton.isEnabled = false
        accessButton.text = "VERIFICANDO SITES…"
        copyButton.isEnabled = false
        summary.text = "Verificando sites reais pela rede celular (não Wi-Fi)."
        for (key in allKeys) {
            resultViews[key]?.first?.apply {
                setTextColor(white)
                text = titles[key].orEmpty() +
                    if (key in accessKeys) "  •  Aguardando" else "  •  Não executado"
            }
            resultViews[key]?.second?.text =
                if (key in accessKeys) "Aguardando resposta HTTPS…" else "Este teste não foi solicitado."
        }
        worker.execute {
            try {
                NetworkDiagnostic.runDirectAccess(applicationContext, target) { key, finding ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) applyFinding(key, finding)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed)
                        summary.text = "Falha no teste: " + error.javaClass.simpleName
                }
            } finally {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        working = false
                        runButton.isEnabled = true
                        accessButton.isEnabled = true
                        accessButton.text = "REPETIR TESTE DE ACESSO"
                        copyButton.isEnabled = findings.isNotEmpty()
                        if (accessKeys.all { findings.containsKey(it) }) {
                            summary.text = "Teste direto concluído • " +
                                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
                                    .format(Date()) + " • " + carrier.selectedItem.toString()
                        }
                    }
                }
            }
        }
    }

    private fun applyFinding(key: String, finding: NetworkDiagnostic.Finding) {
        findings[key] = finding
        val pair = resultViews[key] ?: return
        val word = when(finding.verdict) {
            NetworkDiagnostic.Verdict.PASS -> "OK"
            NetworkDiagnostic.Verdict.PARTIAL -> "PARCIAL"
            NetworkDiagnostic.Verdict.FAIL -> "FALHOU"
            NetworkDiagnostic.Verdict.SKIPPED -> "NÃO EXECUTADO"
        }
        pair.first.text = titles[key].orEmpty() + "  •  " + word
        pair.first.setTextColor(when(finding.verdict) {
            NetworkDiagnostic.Verdict.PASS -> mint
            NetworkDiagnostic.Verdict.PARTIAL -> yellow
            NetworkDiagnostic.Verdict.FAIL -> red
            NetworkDiagnostic.Verdict.SKIPPED -> secondary
        })
        pair.second.text = finding.detail
        copyButton.isEnabled = true
    }

    private fun copyReport() {
        val lines = mutableListOf(
            "NEXORA • Diagnóstico local",
            "Data: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date()),
            "URL: " + address.text.toString(),
            "Operadora informada: " + carrier.selectedItem.toString(),
            "Rota: rede móvel do Android (não Wi-Fi)",
            ""
        )
        for (key in allKeys) {
            val finding = findings[key]
            lines.add(titles[key].orEmpty() + ": " + (finding?.verdict?.name ?: "NÃO TESTADO"))
            lines.add(finding?.detail ?: "Aguardando")
        }
        lines.add("Relatório sem senhas ou chaves VPN.")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Nexora diagnóstico", lines.joinToString("\n")))
        android.widget.Toast.makeText(this, "Relatório copiado", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun panel(body: LinearLayout): LinearLayout {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
            background = rounded(surface, 18)
        }
        body.addView(v, LinearLayout.LayoutParams(-1, -2))
        return v
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun space(v: LinearLayout, size: Int) = v.addView(
        View(this), LinearLayout.LayoutParams(1, dp(size)))
    private fun label(content: String, size: Float, color: Int, strong: Boolean = false): TextView =
        TextView(this).apply {
            text = content
            textSize = size
            setTextColor(color)
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun outlined() = rounded(Color.rgb(19, 50, 62), 12).apply {
        setStroke(dp(1), Color.rgb(49, 94, 99))
    }
}
