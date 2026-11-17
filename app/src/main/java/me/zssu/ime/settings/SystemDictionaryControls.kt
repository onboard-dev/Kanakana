package me.zssu.ime.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.zssu.ime.mozc.SystemDictionaryManager
import java.util.Locale

@Composable
internal fun SystemDictionaryControls() {
    val context = LocalContext.current
    val manager = remember { SystemDictionaryManager.get(context) }
    var state by remember { mutableStateOf(manager.currentState()) }
    var notice by remember { mutableStateOf<SystemDictionaryManager.Pack?>(null) }
    DisposableEffect(manager) {
        val observer = manager.observe { state = it }
        onDispose { observer.close() }
    }
    fun size(bytes: Long) = String.format(Locale.JAPAN, "%.1f MiB", bytes / 1048576.0)
    val sourcePacks = manager.packs.filter { it.sourceIds.ifEmpty { listOf(it.id) }.size == 1 }
    val resolved = manager.resolve(state.sources)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("追加システム辞書", style = MaterialTheme.typography.titleMedium)
            Text("標準の辞書にない言葉(新語、地名、英単語、郵便番号など)を増やす、任意のダウンロードです。必要ない人が多く、初期値の「標準辞書」で足ります。増やすほど候補が増え、使わない候補が混ざることがあります。変換の速さへの影響は、まだ測っていません。", style = MaterialTheme.typography.bodyMedium)
            Text("追加語も文中の変換に使う、Mozcデータ全体の切り替えです。複数選べます。実際に使うのは、選んだ辞書をすべて含む「統合データセット」です（単体より大きい場合があります）。", style = MaterialTheme.typography.bodyMedium)
            Text("入力中は切り替えを待ちます。個人辞書・学習履歴は引き継ぎます。", style = MaterialTheme.typography.bodySmall)
            Text(state.message.ifEmpty { "標準辞書を使用します" }, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { manager.select(emptySet()) },
                enabled = state.sources.isNotEmpty() || state.requested.isNotEmpty(),
            ) {
                Text(if (state.applied == "" && state.requested.isEmpty()) "標準辞書を使用中" else "標準辞書に戻す")
            }

            Text("使う辞書を選ぶ", style = MaterialTheme.typography.titleSmall)
            sourcePacks.forEach { pack ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = pack.id in state.sources,
                        onCheckedChange = { on ->
                            manager.select(if (on) state.sources + pack.id else state.sources - pack.id)
                        },
                        enabled = manager.compatible(pack),
                    )
                    Column {
                        Text(pack.name, style = MaterialTheme.typography.bodyMedium)
                        packSummary(pack.id)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("${pack.version} · ${pack.license}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("使用するデータセット", style = MaterialTheme.typography.titleSmall)
                    Text(resolved?.name ?: "標準辞書", style = MaterialTheme.typography.bodyMedium)
                    if (resolved != null) {
                        Text(
                            "${resolved.sourceIds.ifEmpty { listOf(resolved.id) }.size} 種類を収録 · 取得 ${size(resolved.compressedBytes)} / 保存 ${size(resolved.dataBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("導入時の追加空き容量 ${size(resolved.compressedBytes + resolved.dataBytes + 32L * 1024 * 1024)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(status(resolved, state, manager), style = MaterialTheme.typography.bodySmall)
                    if (resolved != null) {
                        if (state.downloading == resolved.id) {
                            LinearProgressIndicator(progress = { (state.received.toFloat() / resolved.compressedBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${size(state.received)} / ${size(resolved.compressedBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (manager.installed(resolved)) {
                                Button(
                                    onClick = { manager.select(resolved.sourceIds.ifEmpty { listOf(resolved.id) }.toSet()) },
                                    enabled = manager.compatible(resolved) && state.requested != resolved.id,
                                ) { Text("使用する") }
                                OutlinedButton(onClick = { manager.delete(resolved) }, enabled = state.downloading == null) { Text("削除") }
                            } else {
                                Button(
                                    onClick = { manager.download(resolved) },
                                    enabled = manager.compatible(resolved) && state.downloading == null,
                                ) { Text("取得する") }
                            }
                            TextButton(onClick = { notice = resolved }) { Text("出典・ライセンス・収録方針") }
                        }
                    }
                }
            }
            Text("入力内容・個人辞書・学習履歴を取得先へ送信しません。ダウンロード先はGitHubです。", style = MaterialTheme.typography.bodySmall)
        }
    }
    notice?.let { pack ->
        val text = remember(pack.id) { manager.notice(pack) }
        AlertDialog(onDismissRequest = { notice = null }, title = { Text(pack.name) },
            text = { Text(text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("閉じる") } })
    }
}

private fun status(
    pack: SystemDictionaryManager.Pack?,
    state: SystemDictionaryManager.State,
    manager: SystemDictionaryManager,
): String = when {
    pack == null -> if (state.sources.isEmpty()) "標準辞書を使用します" else "選んだ組み合わせは未提供です"
    !manager.compatible(pack) -> "このアプリのMozcとは互換性がありません"
    state.applied == pack.id && state.requested == pack.id -> "使用中"
    state.requested == pack.id -> "切り替え待ち"
    state.applied == pack.id -> "標準辞書への切り替え待ち"
    manager.installed(pack) -> "導入済み"
    else -> "未導入"
}

/** What a dictionary adds, in words a user can act on. Shown under each checkbox. */
private fun packSummary(id: String): String? = when (id) {
    "neologd-main" -> "新しい言葉と固有名詞(ネットの新語、人名、地名、企業名など)"
    "neologd-variants" -> "同じ言葉の別の書き方(表記ゆれ)と活用した形"
    "skk-gpl" -> "総合辞書。一般の言葉、人名、地名、専門用語"
    "skk-cc" -> "英和辞書。ひらがなの読みから英単語を出す"
    "skk-unicode" -> "絵文字と異体字(旧字・俗字などの別の字形)"
    "skk-permissive" -> "沖縄の言葉(地名・人名・食べ物など)、全国の郵便番号と事業所の郵便番号、異体字、公共の用語。沖縄の郵便番号だけではありません"
    else -> null
}
