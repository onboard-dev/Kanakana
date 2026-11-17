package me.zssu.ime.ime

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Chooses where AI proofreading runs: on the device (default), OpenRouter, or the user's own Ollama.
 *
 * Plain views on purpose, like the snippet screen: this is a short form and needs no Compose.
 */
class ProofreadRemoteActivity : Activity() {
    private lateinit var settings: ProofreadRemoteSettings
    private lateinit var group: RadioGroup
    private lateinit var keyField: EditText
    private lateinit var routerModel: EditText
    private lateinit var ollamaUrl: EditText
    private lateinit var ollamaModel: EditText
    private lateinit var status: TextView
    private var call: ProofreadRemote.Call? = null

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun field(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        this.hint = hint
        setText(value)
        setSingleLine()
        inputType = type
        // Keep model names, addresses and keys out of the keyboard's learning.
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI 校正の接続先"
        settings = ProofreadRemoteSettings(this)

        val engines = ProofreadEngine.entries
        group = RadioGroup(this).apply {
            engines.forEach { engine ->
                addView(RadioButton(context).apply {
                    id = 1000 + engine.ordinal
                    text = when (engine) {
                        ProofreadEngine.LOCAL -> "端末内のモデル(既定・文章は外に出ません)"
                        ProofreadEngine.OPENROUTER -> "OpenRouter(文章を OpenRouter に送信)"
                        ProofreadEngine.OLLAMA -> "Ollama(文章を指定したサーバーに送信)"
                    }
                })
            }
            check(1000 + settings.engine.ordinal)
        }

        val hasKey = settings.openRouterKey.isNotEmpty()
        keyField = field(
            if (hasKey) "保存済み(変える場合だけ入力)" else "API キー(sk-or-...)",
            "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        routerModel = field("モデル名(例: openai/gpt-4o-mini)", settings.openRouterModel, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        ollamaUrl = field("アドレス(例: http://192.168.0.10:11434)", settings.ollamaUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        ollamaModel = field("モデル名(例: qwen3:8b)", settings.ollamaModel, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(10), 0, dp(10)) }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            addView(TextView(context).apply { text = "AI 校正の接続先"; textSize = 22f })
            addView(note("端末内のモデルは遅いことがあります。速度を優先する場合だけ、下の接続先を選んでください。外部の接続先を選ぶと、校正する文章がその接続先に送信されます。"))
            addView(heading("使う接続先"))
            addView(group)

            addView(heading("OpenRouter"))
            addView(note("API キーは端末のキーストアで暗号化して保存し、バックアップには含めません。"))
            addView(keyField)
            addView(routerModel)
            addView(Button(context).apply {
                text = "OpenRouter のキーを削除"
                isEnabled = hasKey
                setOnClickListener {
                    settings.openRouterKey = ""
                    Toast.makeText(context, "キーを削除しました", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            })

            addView(heading("Ollama"))
            addView(note("http:// は自宅・社内ネットワーク(192.168.x.x、10.x.x.x、Tailscale の 100.x.x.x など)のみ使えます。インターネット上のサーバーには https:// が必要です。ポートを省略すると 11434 になります。"))
            addView(ollamaUrl)
            addView(ollamaModel)
            addView(Button(context).apply { text = "サーバーからモデルを選ぶ"; setOnClickListener { pickOllamaModel() } })

            addView(status)
            addView(Button(context).apply { text = "接続テスト(文章は送りません)"; setOnClickListener { test() } })
            addView(Button(context).apply { text = "保存"; setOnClickListener { save(); finish() } })
            addView(Button(context).apply { text = "戻る"; setOnClickListener { finish() } })
        }
        val scroll = ScrollView(this).apply { addView(form) }
        // The window draws edge to edge, so the screen is not resized for the status bar or the
        // keyboard on its own: pad by both, then bring the field being typed in back into view.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            view.post {
                (currentFocus as? EditText)?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height + dp(56))) }
            }
            insets
        }
        setContentView(scroll)
    }

    private fun chosenEngine(): ProofreadEngine =
        ProofreadEngine.entries.firstOrNull { 1000 + it.ordinal == group.checkedRadioButtonId } ?: ProofreadEngine.LOCAL

    private fun save() {
        settings.engine = chosenEngine()
        keyField.text.toString().trim().takeIf { it.isNotEmpty() }?.let { settings.openRouterKey = it }
        settings.openRouterModel = routerModel.text.toString()
        settings.ollamaUrl = ollamaUrl.text.toString()
        settings.ollamaModel = ollamaModel.text.toString()
        val config = settings.snapshot()
        val message = when {
            config.engine == ProofreadEngine.LOCAL -> "端末内のモデルで校正します"
            config.ready() -> "保存しました: ${config.describe()}"
            else -> "保存しました(設定が足りないため、校正する前に入力してください)"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Asks the server which models it has and lets the user tap one, instead of typing its name. */
    private fun pickOllamaModel() {
        val url = ollamaUrl.text.toString()
        status.text = "モデルの一覧を取得しています…"
        call?.cancel()
        val current = ProofreadRemote.Call().also { call = it }
        Thread {
            val result = runCatching { ProofreadRemote.listOllamaModels(url, current) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { models ->
                    if (models.isEmpty()) { status.text = "サーバーにモデルがありません(ollama pull で追加してください)"; return@onSuccess }
                    status.text = "モデルが ${models.size} 件見つかりました"
                    val checked = models.indexOf(ollamaModel.text.toString().trim())
                    android.app.AlertDialog.Builder(this).setTitle("モデルを選ぶ")
                        .setSingleChoiceItems(models.toTypedArray(), checked) { dialog, which ->
                            ollamaModel.setText(models[which])
                            dialog.dismiss()
                        }
                        .setNegativeButton("キャンセル", null).show()
                }.onFailure {
                    status.text = (it as? ProofreadRemoteException)?.message ?: "モデルの一覧を取得できませんでした"
                }
            }
        }.start()
    }

    private fun test() {
        val engine = chosenEngine()
        if (engine == ProofreadEngine.LOCAL) { status.text = "端末内のモデルに接続テストはありません"; return }
        val typedKey = keyField.text.toString().trim()
        val key = typedKey.ifEmpty { settings.openRouterKey }
        val url = ollamaUrl.text.toString()
        val model = ollamaModel.text.toString().trim()
        status.text = "接続を確認しています…"
        call?.cancel()
        val current = ProofreadRemote.Call().also { call = it }
        Thread {
            val message = try {
                if (engine == ProofreadEngine.OPENROUTER) ProofreadRemote.testOpenRouter(key, current)
                else ProofreadRemote.testOllama(url, model, current)
            } catch (e: ProofreadRemoteException) {
                e.message.orEmpty()
            } catch (e: Exception) {
                "接続を確認できませんでした"
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) status.text = message }
        }.start()
    }

    override fun onDestroy() {
        call?.cancel()
        super.onDestroy()
    }
}
