package me.zssu.ime.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.zssu.ime.ime.ZenzPlusModel
import me.zssu.ime.ime.ZenzaiNative
import me.zssu.ime.ime.ZenzaiEngine

@Composable
internal fun ZenzaiControls() {
    val context = LocalContext.current
    val settings = remember { ImeSettings(context) }
    var enabled by remember { mutableStateOf(settings.zenzaiPlusEnabled) }
    var plusState by remember { mutableStateOf(ZenzPlusModel.state(context)) }
    val prefs = remember { context.getSharedPreferences(ImeSettings.NAME, 0) }
    var updates by remember { mutableLongStateOf(prefs.getLong("zenzai_ttt_updates", 0)) }
    var trained by remember { mutableIntStateOf(prefs.getInt("zenzai_dictionary_trained", 0)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updates = prefs.getLong("zenzai_ttt_updates", 0)
            trained = prefs.getInt("zenzai_dictionary_trained", 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(context) {
        val subscription = ZenzPlusModel.observe(context) { plusState = it }
        onDispose { subscription.close() }
    }
    val available = remember(plusState) { ZenzaiEngine.isBundled(context) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("zenzai-plus(約183MB・大きいAI)", style = MaterialTheme.typography.titleMedium)
            Text(
                "読みから文全体の変換を考えて、先頭の候補にします。確定した言葉を覚える「自動学習(TTT)」があり、使うほど自分の言葉づかいに合っていきます。Karukan より優先して使われます。端末の中だけで動き、入力は外に送りません。",
                style = MaterialTheme.typography.bodySmall,
            )
            when (val current = plusState) {
                ZenzPlusModel.State.Missing -> {
                    Text("モデルを Hugging Face から取得します（約 183 MiB、CC BY-SA 4.0）。")
                    Button(onClick = { ZenzPlusModel.install(context) }, enabled = ZenzaiNative.available) {
                        Text(if (ZenzPlusModel.storedBytes(context) > 0) "zenzai-plus を再開" else "zenzai-plus をダウンロード")
                    }
                    if (!ZenzaiNative.available) Text("この端末では学習ランタイムを利用できません。")
                }
                is ZenzPlusModel.State.Downloading -> {
                    val progress = (current.bytes.toFloat() / 192132704f).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("zenzai-plus: ${(progress * 100).toInt()}%")
                    OutlinedButton(onClick = { ZenzPlusModel.cancel() }) { Text("中断") }
                }
                is ZenzPlusModel.State.Error -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { ZenzPlusModel.install(context) }, enabled = ZenzaiNative.available) { Text("再試行") }
                }
                ZenzPlusModel.State.Ready -> {
                    Text("zenzai-plus のモデルを準備しました。")
                    OutlinedButton(onClick = { ZenzPlusModel.delete(context) }, enabled = !enabled) { Text("zenzai-plus のモデルを削除") }
                }
            }

            val modelReady = plusState is ZenzPlusModel.State.Ready
            val canEnable = available && modelReady
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("AI変換にzenzai-plusを優先的に使用する", modifier = Modifier.weight(1f))
                Switch(checked = enabled && canEnable, enabled = canEnable, onCheckedChange = {
                    enabled = it; settings.zenzaiPlusEnabled = it
                })
            }
            if (!canEnable) {
                // Say why the switch is dead instead of leaving it silently unresponsive.
                Text(
                    if (!available) {
                        "この端末では zenzai-plus の学習ランタイムを利用できません。"
                    } else {
                        "上のモデルをダウンロードすると有効にできます。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled && canEnable) {
                Text("文節の区切りと候補選択には Mozc を使用します。")
                var autoLearning by remember { mutableStateOf(prefs.getBoolean("zenzai_auto_learning_enabled", true)) }
                Text("入力から自動学習(TTT)")
                Switch(checked = autoLearning, onCheckedChange = {
                    autoLearning = it
                    prefs.edit().putBoolean("zenzai_auto_learning_enabled", it).apply()
                })
                Text("TTT は、確定した言葉と直前の文脈を、AIが少しずつ覚える仕組みです。たとえば「にしがわ」を「西川」と確定し続けると、次から「西川」が出やすくなります。Mozc の学習履歴とは別に、AI自身が覚えます。学習は端末内で行い、オフにすると未処理の入力は学習しません(進行中の学習は完了する場合があります)。", style = MaterialTheme.typography.bodySmall)
                run {
                    OutlinedButton(onClick = {
                        prefs.edit().putLong("zenzai_train_dictionary", prefs.getLong("zenzai_train_dictionary", 0) + 1).apply()
                    }) { Text("ユーザー辞書をAIに覚えさせる") }
                    Text("登録した単語を、AIにまとめて覚えさせます。", style = MaterialTheme.typography.bodySmall)
                    Text("自動学習(TTT)の回数: $updates / AIに覚えさせた辞書の単語: $trained")
                }
            }
        }
    }
}
