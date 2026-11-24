package me.zssu.ime.ime

import android.content.Context
import me.zssu.ime.mozc.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Where proofreading runs. [LOCAL] is the on-device model; the others send the text off the device. */
internal enum class ProofreadEngine(val label: String) {
    LOCAL("端末内"),
    OPENROUTER("OpenRouter"),
    OLLAMA("Ollama"),
}

/** The user-visible failure of a remote call. The message is safe to show: it never carries the key. */
internal class ProofreadRemoteException(message: String) : Exception(message)

/**
 * Optional remote proofreading, off by default.
 *
 * The on-device model stays the default because it never sends text anywhere. These engines exist
 * for people who accept that trade for speed: OpenRouter (an OpenAI-compatible hosted service) and
 * an Ollama server the user runs themselves.
 */
internal class ProofreadRemoteSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("proofread_remote", Context.MODE_PRIVATE)
    // The key is a credential, so it is encrypted with the Keystore like the user dictionary.
    private val keyFile = File(app.filesDir, "openrouter_key.enc")

    var engine: ProofreadEngine
        get() = runCatching { ProofreadEngine.valueOf(prefs.getString("engine", null) ?: "") }
            .getOrDefault(ProofreadEngine.LOCAL)
        set(value) {
            val editor = prefs.edit().putString("engine", value.name)
            // Remember the last external choice so that picking "cloud / Ollama" again restores it.
            if (value != ProofreadEngine.LOCAL) editor.putString("last_remote_engine", value.name)
            editor.apply()
        }

    /** The external engine to use when the user switches from the on-device model back to an external one. */
    val lastRemoteEngine: ProofreadEngine
        get() = runCatching { ProofreadEngine.valueOf(prefs.getString("last_remote_engine", null) ?: "") }
            .getOrNull()?.takeIf { it != ProofreadEngine.LOCAL } ?: ProofreadEngine.OPENROUTER

    var openRouterModel: String
        get() = prefs.getString("openrouter_model", "").orEmpty()
        set(value) { prefs.edit().putString("openrouter_model", value.trim()).apply() }

    var ollamaUrl: String
        get() = prefs.getString("ollama_url", "").orEmpty()
        set(value) { prefs.edit().putString("ollama_url", value.trim()).apply() }

    var ollamaModel: String
        get() = prefs.getString("ollama_model", "").orEmpty()
        set(value) { prefs.edit().putString("ollama_model", value.trim()).apply() }

    var openRouterKey: String
        get() = runCatching { SecureStore.read(keyFile).orEmpty() }.getOrDefault("")
        set(value) {
            val key = value.trim()
            if (key.isEmpty()) keyFile.delete() else SecureStore.writeBytes(keyFile, key.toByteArray())
        }

    fun snapshot() = ProofreadRemote.Config(
        engine = engine,
        openRouterKey = openRouterKey,
        openRouterModel = openRouterModel,
        ollamaUrl = ollamaUrl,
        ollamaModel = ollamaModel,
    )
}

internal object ProofreadRemote {
    const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
    const val OLLAMA_DEFAULT_PORT = 11434
    private const val MAX_TOKENS = 1024
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 120_000

    /** Appended to connection failures of the user's own server, which is usually reached over a VPN. */
    const val OLLAMA_NETWORK_HINT = "Tailscale を使っている場合は、スマホの Tailscale が ON(Connected)になっているか確認してください。"

    data class Config(
        val engine: ProofreadEngine,
        val openRouterKey: String,
        val openRouterModel: String,
        val ollamaUrl: String,
        val ollamaModel: String,
    ) {
        /** Whether this engine has everything it needs to be called. */
        fun ready(): Boolean = when (engine) {
            ProofreadEngine.LOCAL -> true
            ProofreadEngine.OPENROUTER -> openRouterKey.isNotBlank() && openRouterModel.isNotBlank()
            ProofreadEngine.OLLAMA -> checkOllamaUrl(ollamaUrl).base != null && ollamaModel.isNotBlank()
        }

        /** A short name for status lines, including the model. */
        fun describe(): String = when (engine) {
            ProofreadEngine.LOCAL -> "端末内"
            ProofreadEngine.OPENROUTER -> "OpenRouter(${openRouterModel})"
            ProofreadEngine.OLLAMA -> "Ollama(${ollamaModel})"
        }
    }

    /** A request in flight, so a long wait can be abandoned from another thread. */
    class Call {
        @Volatile private var connection: HttpURLConnection? = null
        @Volatile var cancelled = false
            private set

        internal fun attach(value: HttpURLConnection) {
            connection = value
            if (cancelled) value.disconnect()
        }

        fun cancel() {
            cancelled = true
            connection?.disconnect()
        }
    }

    // ---- addresses ------------------------------------------------------------------------------

    class UrlCheck(val base: String?, val problem: String?)

    /**
     * Validates a user-typed Ollama address and reduces it to `scheme://host:port`.
     *
     * Plain `http` is accepted only for addresses that stay on a private network (home LAN,
     * Tailscale, localhost), because the proofread text would otherwise cross the internet
     * unencrypted. Anything public must be `https`.
     */
    fun checkOllamaUrl(input: String): UrlCheck {
        val text = input.trim()
        if (text.isEmpty()) return UrlCheck(null, "Ollama のアドレスを入力してください")
        val withScheme = if ("://" in text) text else "http://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: return UrlCheck(null, "アドレスの形式が正しくありません")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "http" && scheme != "https") || host.isNullOrEmpty()) {
            return UrlCheck(null, "http:// または https:// で始まるアドレスを入力してください")
        }
        if (scheme == "http" && !isPrivateHost(host)) {
            return UrlCheck(null, "インターネット上のアドレスには https:// を使ってください(http は自宅・社内ネットワークのみ)")
        }
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "http" -> OLLAMA_DEFAULT_PORT
            else -> -1
        }
        val hostPart = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return UrlCheck("$scheme://$hostPart" + (if (port != -1) ":$port" else ""), null)
    }

    /** Loopback, link-local, RFC 1918, Tailscale's 100.64/10, and names that never leave a LAN. */
    fun isPrivateHost(host: String): Boolean {
        val name = host.trim('[', ']').lowercase()
        if (name == "localhost" || name.endsWith(".local") || name.endsWith(".lan") ||
            name.endsWith(".ts.net") || name.endsWith(".home.arpa")) return true
        if (':' in name) return name == "::1" || name.startsWith("fc") || name.startsWith("fd") || name.startsWith("fe80")
        val parts = name.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val (a, b) = parts[0].toInt() to parts[1].toInt()
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        // A bare single-label name such as "nas" is resolved by the local network only.
        return '.' !in name
    }

    // ---- request bodies -------------------------------------------------------------------------

    private fun messages(system: String, user: String): JSONArray = JSONArray()
        .put(JSONObject().put("role", "system").put("content", system))
        .put(JSONObject().put("role", "user").put("content", user))

    fun openRouterBody(model: String, system: String, user: String): String = JSONObject()
        .put("model", model)
        .put("messages", messages(system, user))
        .put("temperature", 0)
        .put("max_tokens", MAX_TOKENS)
        .put("stream", false)
        .toString()

    fun ollamaBody(model: String, system: String, user: String): String = JSONObject()
        .put("model", model)
        .put("messages", messages(system, user))
        .put("stream", false)
        // Reasoning models spend the whole token budget thinking and return an empty answer.
        .put("think", false)
        .put("options", JSONObject().put("temperature", 0).put("num_predict", MAX_TOKENS))
        .toString()

    // ---- responses ------------------------------------------------------------------------------

    /** The reply text of an OpenAI-style chat completion. */
    fun parseOpenRouter(body: String): String {
        val json = parse(body)
        json.optJSONObject("error")?.let {
            throw ProofreadRemoteException("OpenRouter: " + it.optString("message", "エラーが返されました"))
        }
        val content = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        if (content.isNullOrBlank()) throw ProofreadRemoteException("OpenRouter から校正結果を受け取れませんでした")
        return content
    }

    /** The reply text of Ollama's `/api/chat`. */
    fun parseOllama(body: String): String {
        val json = parse(body)
        json.optString("error").takeIf { it.isNotBlank() }?.let {
            throw ProofreadRemoteException("Ollama: $it")
        }
        val message = json.optJSONObject("message")
        val content = message?.optString("content")
        if (content.isNullOrBlank()) {
            val thoughtOnly = !message?.optString("thinking").isNullOrBlank()
            throw ProofreadRemoteException(
                if (thoughtOnly) "Ollama: モデルが推論だけで終わり、答えを返しませんでした" else "Ollama から校正結果を受け取れませんでした",
            )
        }
        return content
    }

    /** Model names listed by Ollama's `/api/tags`. */
    fun parseOllamaModels(body: String): List<String> {
        val models = parse(body).optJSONArray("models") ?: return emptyList()
        return (0 until models.length()).mapNotNull { models.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
    }

    private fun parse(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrElse { throw ProofreadRemoteException("サーバーの応答を読み取れませんでした") }

    // ---- calls ----------------------------------------------------------------------------------

    /** Sends [user] to the configured engine and returns the raw reply. Blocking: call off the UI thread. */
    fun proofread(config: Config, system: String, user: String, call: Call): String = when (config.engine) {
        ProofreadEngine.LOCAL -> throw IllegalStateException("local engine is not remote")
        ProofreadEngine.OPENROUTER -> {
            val (status, body) = send(
                "POST", "$OPENROUTER_BASE/chat/completions", openRouterHeaders(config.openRouterKey),
                openRouterBody(config.openRouterModel, system, user), call,
            )
            if (status !in 200..299) throw ProofreadRemoteException(openRouterFailure(status, body))
            parseOpenRouter(body)
        }
        ProofreadEngine.OLLAMA -> {
            val base = checkOllamaUrl(config.ollamaUrl).let { it.base ?: throw ProofreadRemoteException(it.problem.orEmpty()) }
            val (status, body) = send("POST", "$base/api/chat", emptyMap(), ollamaBody(config.ollamaModel, system, user), call, OLLAMA_NETWORK_HINT)
            if (status !in 200..299) throw ProofreadRemoteException(ollamaFailure(status, body))
            parseOllama(body)
        }
    }

    /** The models installed on the Ollama server, sorted by name. Sends none of the user's text. */
    fun listOllamaModels(url: String, call: Call): List<String> {
        val base = checkOllamaUrl(url).let { it.base ?: throw ProofreadRemoteException(it.problem.orEmpty()) }
        val (status, body) = send("GET", "$base/api/tags", emptyMap(), null, call, OLLAMA_NETWORK_HINT)
        if (status !in 200..299) throw ProofreadRemoteException(ollamaFailure(status, body))
        return parseOllamaModels(body).distinct().sorted()
    }

    /** Checks the connection without sending any of the user's text. */
    fun testOllama(url: String, model: String, call: Call): String {
        val models = listOllamaModels(url, call)
        return when {
            model.isBlank() -> "接続できました(モデル ${models.size} 件)。モデル名を入力してください"
            model in models || models.any { it.substringBefore(':') == model && ':' !in model } ->
                "接続できました。モデル「$model」を使えます"
            else -> "接続できましたが、モデル「$model」が見つかりません。利用可能: " +
                models.take(8).joinToString(", ").ifEmpty { "(なし)" }
        }
    }

    fun testOpenRouter(key: String, call: Call): String {
        if (key.isBlank()) throw ProofreadRemoteException("API キーを入力してください")
        val (status, body) = send("GET", "$OPENROUTER_BASE/key", openRouterHeaders(key), null, call)
        if (status !in 200..299) throw ProofreadRemoteException(openRouterFailure(status, body))
        return "接続できました。API キーは有効です"
    }

    private fun openRouterHeaders(key: String) = mapOf(
        "Authorization" to "Bearer $key",
        "HTTP-Referer" to "https://github.com/onboard-dev/Kanakana",
        "X-Title" to "KanaKana",
    )

    private fun openRouterFailure(status: Int, body: String): String = when (status) {
        401 -> "OpenRouter: API キーが無効です"
        402 -> "OpenRouter: クレジットが不足しています"
        404 -> "OpenRouter: モデルが見つかりません(モデル名を確認してください)"
        429 -> "OpenRouter: 利用が集中しています。しばらく待ってから再実行してください"
        else -> runCatching { parseOpenRouter(body) }.exceptionOrNull()?.message
            ?: "OpenRouter: エラー($status)"
    }

    private fun ollamaFailure(status: Int, body: String): String =
        runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "Ollama: $it" } ?: "Ollama: エラー($status)"

    /**
     * One HTTP exchange. Neither the body nor the headers are ever logged: they hold the user's text
     * and the API key.
     */
    private fun send(
        method: String, url: String, headers: Map<String, String>, body: String?, call: Call, failureHint: String = "",
    ): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        call.attach(connection)
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.use { readBounded(it) } ?: "")
        } catch (e: IOException) {
            throw ProofreadRemoteException(
                if (call.cancelled) "中止しました" else {
                    val timedOut = e is java.net.SocketTimeoutException
                    (if (timedOut) "応答がなく、時間切れになりました。" else "接続できませんでした(アドレスとネットワークを確認してください)。") +
                        (if (failureHint.isNotEmpty()) " $failureHint" else "")
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_RESPONSE_BYTES) throw ProofreadRemoteException("サーバーの応答が大きすぎます")
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
