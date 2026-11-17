package me.zssu.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import me.zssu.ime.ime.SnippetCrypto
import me.zssu.ime.ime.SnippetStore
import me.zssu.ime.ime.SnippetTransfer

class SnippetActivity : Activity() {
    private companion object {
        const val REQUEST_IMPORT = 4101
        const val REQUEST_EXPORT = 4102
    }
    private val store by lazy { SnippetStore(this) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); refresh() }
    private fun refresh() {
        val items = store.entries()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(context).apply { text = "定型文\nタップで編集、長押しで並べ替え・削除。端末内に暗号化して保存します。"; textSize = 18f })
            addView(Button(context).apply { text = "追加"; isEnabled = items.size < 100; setOnClickListener { edit(null) } })
            addView(Button(context).apply { text = "インポート"; setOnClickListener { pickImport() } })
            addView(Button(context).apply { text = "エクスポート"; setOnClickListener { pickExport() } })
            addView(Button(context).apply {
                text = "差し込み用の名前を設定"
                setOnClickListener {
                    val field = EditText(context).apply {
                        setText(store.name()); setSingleLine()
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        filters = arrayOf(android.text.InputFilter.LengthFilter(100))
                    }
                    AlertDialog.Builder(this@SnippetActivity).setTitle("差し込み用の名前").setView(field)
                        .setNegativeButton("キャンセル", null).setPositiveButton("保存") { _, _ ->
                            if (!store.saveName(field.text.toString())) Toast.makeText(context, "保存できませんでした", Toast.LENGTH_LONG).show()
                        }.show()
                }
            })
            addView(Button(context).apply {
                text = "日付の形式を設定"
                setOnClickListener {
                    val field = EditText(context).apply {
                        setSingleLine(); setText(store.dateFormat())
                        hint = "yyyy年MM月dd日"
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                    val dialog = AlertDialog.Builder(this@SnippetActivity).setTitle("日付の形式")
                        .setMessage("例：yyyy-MM-dd、yyyy年M月d日、M/d")
                        .setView(field).setPositiveButton("保存", null).setNegativeButton("戻る", null).create()
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val pattern = field.text.toString().trim()
                            if (!me.zssu.ime.ime.SnippetTemplate.validDateFormat(pattern)) field.error = "この日付形式は使えません"
                            else { store.saveDateFormat(pattern); dialog.dismiss() }
                        }
                    }
                    dialog.show()
                }
            })
            items.forEachIndexed { index, value ->
                addView(Button(context).apply {
                    text = value
                    // Buttons upper-case their label by default; a snippet must show as stored.
                    isAllCaps = false
                    setOnClickListener { edit(index) }
                    setOnLongClickListener {
                        val actions = buildList {
                            if (index > 0) { add("先頭に移動" to 0); add("一つ上へ" to index - 1) }
                            if (index < items.lastIndex) add("一つ下へ" to index + 1)
                            add("削除" to -1)
                        }
                        AlertDialog.Builder(this@SnippetActivity).setItems(actions.map { it.first }.toTypedArray()) { _, choice ->
                            val target = actions[choice].second
                            if (target < 0) AlertDialog.Builder(this@SnippetActivity).setMessage("この定型文を削除しますか？")
                                .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ ->
                                    persist(items.filterIndexed { i, _ -> i != index })
                                }.show()
                            else persist(items.toMutableList().apply { add(target, removeAt(index)) })
                        }.show()
                        true
                    }
                })
            }
            addView(Button(context).apply { text = "戻る"; setOnClickListener { finish() } })
        }
        val scroll = ScrollView(this).apply { addView(panel) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }
    private fun edit(index: Int?) {
        val input = EditText(this).apply {
            minLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setText(index?.let { store.entries().getOrNull(it) }.orEmpty())
        }
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = "差し込み項目をタップして追加できます。" })
            addView(LinearLayout(context).apply {
                for (label in listOf("名前", "日付", "選択", "カーソル")) addView(Button(context).apply {
                    text = label
                    setOnClickListener {
                        input.text.insert(input.selectionStart.coerceAtLeast(0), "{{$label}}")
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(this).setTitle("定型文（2000文字まで）").setView(editor)
            .setNegativeButton("キャンセル", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.isBlank()) { input.error = "本文を入力してください"; return@setOnClickListener }
                val next = store.entries().toMutableList()
                if (index == null) next.add(input.text.toString()) else if (index in next.indices) next[index] = input.text.toString()
                if (persist(next)) dialog.dismiss()
            }
        }
        dialog.show()
    }
    // The system file picker is the choice between this device and Google Drive: Drive appears as a
    // location in it when the Drive app is installed and signed in.
    //
    // Export always writes the password-protected container (see SnippetCrypto). Import accepts that
    // container, and also a plain text file with one snippet per line.
    private var pendingExport: ByteArray? = null

    private fun pickImport() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            // Not text/*: an encrypted file has no text type and would be greyed out in the picker.
            type = "*/*"
        }
        launchPicker(intent, REQUEST_IMPORT)
    }

    private fun pickExport() {
        val entries = store.entries()
        if (entries.isEmpty()) { Toast.makeText(this, "エクスポートする定型文がありません", Toast.LENGTH_SHORT).show(); return }
        askNewPassword { password ->
            runBusy("暗号化しています…", {
                SnippetCrypto.encrypt(SnippetTransfer.encode(entries).toByteArray(Charsets.UTF_8), password)
            }) { result ->
                java.util.Arrays.fill(password, ' ')
                result.onSuccess { bytes ->
                    pendingExport = bytes
                    val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(android.content.Intent.EXTRA_TITLE, SnippetCrypto.FILE_NAME)
                    }
                    launchPicker(intent, REQUEST_EXPORT)
                }.onFailure { Toast.makeText(this, "暗号化できませんでした", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun launchPicker(intent: android.content.Intent, request: Int) {
        runCatching { startActivityForResult(intent, request) }
            .onFailure { Toast.makeText(this, "ファイルを選ぶ画面を開けませんでした", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Plain Activity; the result API of ComponentActivity is not available here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (requestCode == REQUEST_EXPORT) pendingExport = null
            return
        }
        when (requestCode) {
            REQUEST_IMPORT -> importFrom(uri)
            REQUEST_EXPORT -> exportTo(uri)
        }
    }

    private fun importFrom(uri: android.net.Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    check(out.size() <= SnippetTransfer.MAX_FILE_BYTES) { "file too large" }
                }
                out.toByteArray()
            }
        }.getOrNull()
        if (bytes == null) { Toast.makeText(this, "ファイルを読み込めませんでした(2MBまでのファイルです)", Toast.LENGTH_LONG).show(); return }
        if (SnippetCrypto.isEncrypted(bytes)) promptDecrypt(bytes, null)
        else applyImport(String(bytes, Charsets.UTF_8))
    }

    private fun promptDecrypt(bytes: ByteArray, error: String?) {
        askPassword("パスワードを入力", error) { password ->
            runBusy("復号しています…", { SnippetCrypto.decrypt(bytes, password) }) { result ->
                java.util.Arrays.fill(password, ' ')
                val plain = result.getOrNull()
                if (plain == null) promptDecrypt(bytes, "パスワードが違うか、ファイルが壊れています")
                else applyImport(String(plain, Charsets.UTF_8))
            }
        }
    }

    private fun applyImport(text: String) {
        val lines = SnippetTransfer.decode(text)
        if (lines.isEmpty()) { Toast.makeText(this, "取り込める行がありませんでした", Toast.LENGTH_LONG).show(); return }
        val merge = SnippetTransfer.merge(store.entries(), lines)
        if (merge.added > 0 && !store.save(merge.merged)) {
            Toast.makeText(this, "保存できませんでした", Toast.LENGTH_LONG).show(); return
        }
        val message = buildString {
            append("${merge.added}件を追加しました")
            if (merge.duplicates > 0) append("\n重複のため${merge.duplicates}件はスキップしました")
            if (merge.overLimit > 0) append("\n上限(${SnippetTransfer.MAX_ENTRIES}件)を超えた${merge.overLimit}件は追加できませんでした")
        }
        AlertDialog.Builder(this).setTitle("インポート結果").setMessage(message)
            .setPositiveButton("閉じる", null).show()
        refresh()
    }

    private fun exportTo(uri: android.net.Uri) {
        val bytes = pendingExport
        pendingExport = null
        if (bytes == null) { Toast.makeText(this, "もう一度エクスポートを実行してください", Toast.LENGTH_LONG).show(); return }
        val ok = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (ok) "パスワード付きで書き出しました(読み取りにはパスワードが必要です)" else "書き出せませんでした",
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Key stretching takes about a second, so it runs off the UI thread behind a small dialog. */
    private fun <T> runBusy(message: String, work: () -> T, done: (Result<T>) -> Unit) {
        val dialog = AlertDialog.Builder(this).setMessage(message).setCancelable(false).show()
        Thread {
            val result = runCatching(work)
            runOnUiThread {
                dialog.dismiss()
                if (!isFinishing && !isDestroyed) done(result)
            }
        }.start()
    }

    private fun passwordField(hint: String) = EditText(this).apply {
        this.hint = hint
        setSingleLine()
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    private fun askPassword(title: String, error: String?, onPassword: (CharArray) -> Unit) {
        val field = passwordField("パスワード")
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("キャンセル", null).setPositiveButton("OK", null).create()
        dialog.setOnShowListener {
            error?.let { field.error = it }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (field.text.isEmpty()) { field.error = "パスワードを入力してください"; return@setOnClickListener }
                val password = CharArray(field.text.length).also { field.text.getChars(0, it.size, it, 0) }
                dialog.dismiss()
                onPassword(password)
            }
        }
        dialog.show()
    }

    private fun askNewPassword(onPassword: (CharArray) -> Unit) {
        val first = passwordField("パスワード(${SnippetCrypto.MIN_PASSWORD_LENGTH}文字以上)")
        val second = passwordField("パスワード(確認)")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(first); addView(second)
        }
        val dialog = AlertDialog.Builder(this).setTitle("エクスポートのパスワード")
            .setMessage("このパスワードがないとファイルを読み取れません。忘れると復元できません。")
            .setView(form).setNegativeButton("キャンセル", null).setPositiveButton("暗号化して保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = first.text.toString()
                when {
                    a.length < SnippetCrypto.MIN_PASSWORD_LENGTH ->
                        first.error = "${SnippetCrypto.MIN_PASSWORD_LENGTH}文字以上にしてください"
                    a != second.text.toString() -> second.error = "パスワードが一致しません"
                    else -> { dialog.dismiss(); onPassword(a.toCharArray()) }
                }
            }
        }
        dialog.show()
    }

    private fun persist(items: List<String>): Boolean {
        if (!store.save(items)) { Toast.makeText(this, "保存できませんでした", Toast.LENGTH_LONG).show(); return false }
        refresh(); return true
    }
}
