package me.zssu.ime.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zssu.ime.keyboard.FlickGuideStyle
import me.zssu.ime.keyboard.KeyboardStyle
import me.zssu.ime.theme.ZinnaTheme

class InputSettingsActivity : ComponentActivity() {
    /** Bumped when a key is pressed on this screen, so the keyboard card re-reads what was learned. */
    private val keyTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZinnaTheme { InputSettingsScreen(keyTick.intValue) } }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            KeyboardLayoutInfo.noteKey(this, event)
            keyTick.intValue++
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InputSettingsScreen(keyTick: Int) {
    val context = LocalContext.current
    var deviceTick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val manager = context.getSystemService(android.content.Context.INPUT_SERVICE) as? android.hardware.input.InputManager
        val listener = object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { deviceTick++ }
            override fun onInputDeviceRemoved(deviceId: Int) { deviceTick++ }
            override fun onInputDeviceChanged(deviceId: Int) { deviceTick++ }
        }
        manager?.registerInputDeviceListener(listener, null)
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }
    val settings = remember { ImeSettings(context) }
    var style by remember { mutableStateOf(settings.keyboardStyle) }
    var flickInputMode by remember { mutableStateOf(settings.flickInputMode) }
    var guide by remember { mutableStateOf(settings.flickGuideStyle) }
    var hapticFeedback by remember { mutableStateOf(settings.hapticFeedbackEnabled) }
    var hardwareKana by remember { mutableStateOf(settings.hardwareKanaEnabled) }
    var jisSymbols by remember { mutableStateOf(settings.hardwareJisSymbols) }
    var kanaColor by remember { mutableStateOf(settings.modeIndicatorKanaColor) }
    var directColor by remember { mutableStateOf(settings.modeIndicatorDirectColor) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("入力方式") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecognizedKeyboardsCard(keyTick + deviceTick, jisSymbols)

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("キーボード配列", style = MaterialTheme.typography.titleMedium)
                    Text("かなと英字それぞれの配列。キーボード上のキーで面を切り替えたときも、この選択に従います", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(style == KeyboardStyle.FLICK, "フリック") { style = KeyboardStyle.FLICK; settings.keyboardStyle = KeyboardStyle.FLICK }
                        Chip(style == KeyboardStyle.QWERTY, "QWERTY") { style = KeyboardStyle.QWERTY; settings.keyboardStyle = KeyboardStyle.QWERTY }
                        Chip(style == KeyboardStyle.MIXED, "英字だけQWERTY") { style = KeyboardStyle.MIXED; settings.keyboardStyle = KeyboardStyle.MIXED }
                        Chip(style == KeyboardStyle.KANA_QWERTY, "かなだけQWERTY") { style = KeyboardStyle.KANA_QWERTY; settings.keyboardStyle = KeyboardStyle.KANA_QWERTY }
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ハードウェアキーボード(JISかな入力)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bluetooth などの JIS キーボードで、キーに印字されたかなどおりに入力します。" +
                            "接続中は画面のキーを隠して候補バーだけを表示します(バー右端の ⌨ で画面キーボードを出せます)。" +
                            "切替: 半角/全角 ほか、かなキーでオン、無変換・英数でオフ。" +
                            "パスワード、URL、メールアドレス欄では英数入力のままです",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(hardwareKana, "オン") { hardwareKana = true; settings.hardwareKanaEnabled = true }
                        Chip(!hardwareKana, "オフ") { hardwareKana = false; settings.hardwareKanaEnabled = false }
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JIS配列の記号(英数入力のとき)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "端末がキーボードを英語(US)配列として扱うと、¥ や ろ のキーが入らず、Shift+2 が \" でなく @ になります。" +
                            "オンにすると、キーに印字された JIS 配列の記号を入力します。" +
                            "英語(US)配列のキーボードをつないでいるときは、下の「オフ」を選んでください。" +
                            "上の「ハードウェアキーボード(JISかな入力)」とは別の設定です",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(jisSymbols, "オン(おすすめ)") { jisSymbols = true; settings.hardwareJisSymbols = true }
                        Chip(!jisSymbols, "オフ") { jisSymbols = false; settings.hardwareJisSymbols = false }
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("入力モードの表示(あ / A)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ハードウェアキーボードの接続中、メニューボタンの右に、いまの入力モードを表示します。" +
                            "かな入力は「あ」、英字入力は「A」(半角)です。タップすると切り替わります。背景の色を、8色から選べます",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("かな入力(あ)の背景", style = MaterialTheme.typography.titleSmall)
                    ColorSwatches(kanaColor) { kanaColor = it; settings.modeIndicatorKanaColor = it }
                    Text("英字入力(A)の背景", style = MaterialTheme.typography.titleSmall)
                    ColorSwatches(directColor) { directColor = it; settings.modeIndicatorDirectColor = it }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("フリック連打", style = MaterialTheme.typography.titleMedium)
                    Text("ケータイ打ち併用では、同じキーを続けて押すと文字が循環します。約0.65秒待つか、右キーを押すと同じ行の文字を続けて入力できます", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(flickInputMode == ImeSettings.FlickInputMode.FLICK_ONLY, "フリックのみ") {
                            flickInputMode = ImeSettings.FlickInputMode.FLICK_ONLY; settings.flickInputMode = ImeSettings.FlickInputMode.FLICK_ONLY
                        }
                        Chip(flickInputMode == ImeSettings.FlickInputMode.FLICK_AND_TOGGLE, "ケータイ併用") {
                            flickInputMode = ImeSettings.FlickInputMode.FLICK_AND_TOGGLE; settings.flickInputMode = ImeSettings.FlickInputMode.FLICK_AND_TOGGLE
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("フリックガイド表示", style = MaterialTheme.typography.titleMedium)
                    Text("キーを押している間に何を出すか。フリック配列のときだけ効きます", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(guide == FlickGuideStyle.PREVIEW, "選択文字のみ") {
                            guide = FlickGuideStyle.PREVIEW; settings.flickGuideStyle = FlickGuideStyle.PREVIEW
                        }
                        Chip(guide == FlickGuideStyle.DIRECTIONS, "4方向表示") {
                            guide = FlickGuideStyle.DIRECTIONS; settings.flickGuideStyle = FlickGuideStyle.DIRECTIONS
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("キー操作のバイブ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "キー、候補、ツールボタンを押したときの触覚フィードバック",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(hapticFeedback, "オン") {
                            hapticFeedback = true
                            settings.hapticFeedbackEnabled = true
                        }
                        Chip(!hapticFeedback, "オフ") {
                            hapticFeedback = false
                            settings.hapticFeedbackEnabled = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun RecognizedKeyboardsCard(tick: Int, jisSymbols: Boolean) {
    val context = LocalContext.current
    // tick changes when a key is pressed or a keyboard is attached or removed.
    val keyboards = remember(tick) { KeyboardLayoutInfo.attachedKeyboards() }
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("認識されているハードウェアキーボード", style = MaterialTheme.typography.titleMedium)
            if (keyboards.isEmpty()) {
                Text("ハードウェアキーボードは接続されていません", style = MaterialTheme.typography.bodyMedium)
            }
            keyboards.forEach { device ->
                val jis = KeyboardLayoutInfo.seenJisKey(context, device)
                val android = KeyboardLayoutInfo.androidMap(device)
                Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (jis) "配列: JIS配列(JIS配列にしかないキーを検出しました)"
                    else "配列: 未確認(英語配列か、JIS配列のキーをまだ押していません)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "端末の扱い: " + when (android) {
                        KeyboardLayoutInfo.AndroidMap.US -> "英語(US)配列(Shift+2 で @ になります)"
                        KeyboardLayoutInfo.AndroidMap.JIS -> "日本語(JIS)配列(Shift+2 で \" になります)"
                        KeyboardLayoutInfo.AndroidMap.OTHER -> "その他の配列"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (jis && android == KeyboardLayoutInfo.AndroidMap.US) {
                    Text(
                        if (jisSymbols) "このキーボードは JIS 配列ですが、端末は英語配列として扱っています。下の「JIS配列の記号」がオンなので、英数入力でも印字どおりの記号が入ります"
                        else "このキーボードは JIS 配列ですが、端末は英語配列として扱っています。下の「JIS配列の記号」をオンにすると、英数入力でも印字どおりの記号が入ります",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (keyboards.isNotEmpty() && keyboards.none { KeyboardLayoutInfo.seenJisKey(context, it) }) {
                Text(
                    "JIS配列かどうかを調べるには、この画面を開いたまま、¥ キー(バックスペースの隣)か、無変換・変換キーを押してください。" +
                        "英語配列のキーボードには、これらのキーがありません",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeIndicatorColors.palette.forEachIndexed { index, swatch ->
            val chosen = index == selected
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(androidx.compose.ui.graphics.Color(swatch.argb), CircleShape)
                        .border(
                            if (chosen) 3.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    if (chosen) Text("✓", color = androidx.compose.ui.graphics.Color(ModeIndicatorColors.textColorFor(swatch.argb)))
                }
                Text(swatch.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
