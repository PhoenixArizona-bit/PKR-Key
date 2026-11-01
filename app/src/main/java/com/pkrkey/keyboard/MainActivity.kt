package com.pkrkey.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var enableStatus: TextView
    private lateinit var selectStatus: TextView
    private lateinit var heightLabel: TextView
    private lateinit var previewButton: MaterialButton
    private lateinit var previewGroup: View
    private lateinit var previewInput: EditText
    private lateinit var previewHolder: FrameLayout
    private var previewKeyboard: PkrKeyboardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableStatus = findViewById(R.id.enableStatus)
        selectStatus = findViewById(R.id.selectStatus)
        heightLabel = findViewById(R.id.heightLabel)
        previewButton = findViewById(R.id.btnPreview)
        previewGroup = findViewById(R.id.previewGroup)
        previewInput = findViewById(R.id.previewInput)
        previewHolder = findViewById(R.id.previewHolder)

        findViewById<MaterialButton>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnSelect).setOnClickListener { showKeyboardPicker() }

        setupAppearance()

        previewButton.setOnClickListener { togglePreview() }

        findViewById<MaterialButton>(R.id.btnCopyImage).setOnClickListener {
            val ok = SymbolImage.copyToClipboard(this)
            Toast.makeText(
                this,
                if (ok) R.string.toast_copied_short else R.string.toast_copy_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<MaterialButton>(R.id.btnShareImage).setOnClickListener {
            if (!SymbolImage.share(this, getString(R.string.share_chooser_title))) {
                Toast.makeText(this, R.string.toast_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ---------------------------------------------------------------- setup status

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isKeyboardDefault(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return current != null && current.startsWith("$packageName/")
    }

    private fun updateStatus() {
        val enabled = isKeyboardEnabled()
        enableStatus.setText(if (enabled) R.string.status_on else R.string.status_off)
        selectStatus.setText(if (isKeyboardDefault()) R.string.status_on else R.string.status_off)
    }

    private fun showKeyboardPicker() {
        if (!isKeyboardEnabled()) {
            Toast.makeText(this, R.string.enable_first, Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            return
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    // ---------------------------------------------------------------- appearance settings

    private fun setupAppearance() {
        val themeGroup = findViewById<MaterialButtonToggleGroup>(R.id.themeGroup)
        val slider = findViewById<Slider>(R.id.heightSlider)
        val symbolSwitch = findViewById<MaterialSwitch>(R.id.symbolSwitch)

        // Show the saved values first, then attach listeners.
        themeGroup.check(
            when (KeyboardPrefs.themeId(this)) {
                KeyboardThemes.LIGHT_ID -> R.id.themeLight
                KeyboardThemes.DARK_ID -> R.id.themeDark
                else -> R.id.themeEmerald
            }
        )
        val height = KeyboardPrefs.keyHeightDp(this)
        slider.value = height.toFloat()
        heightLabel.text = getString(R.string.label_key_height, height)
        symbolSwitch.isChecked = KeyboardPrefs.showSymbolKey(this)

        themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val id = when (checkedId) {
                R.id.themeLight -> KeyboardThemes.LIGHT_ID
                R.id.themeDark -> KeyboardThemes.DARK_ID
                else -> KeyboardThemes.EMERALD_ID
            }
            KeyboardPrefs.setThemeId(this, id)
            previewKeyboard?.reloadConfig()
        }
        slider.addOnChangeListener { _, value, _ ->
            val dp = value.toInt()
            heightLabel.text = getString(R.string.label_key_height, dp)
            KeyboardPrefs.setKeyHeightDp(this, dp)
            previewKeyboard?.reloadConfig()
        }
        symbolSwitch.setOnCheckedChangeListener { _, checked ->
            KeyboardPrefs.setShowSymbolKey(this, checked)
            previewKeyboard?.reloadConfig()
        }
    }

    // ---------------------------------------------------------------- preview

    private fun togglePreview() {
        if (previewGroup.visibility == View.VISIBLE) {
            previewGroup.visibility = View.GONE
            previewButton.setText(R.string.btn_preview_show)
            return
        }
        if (previewKeyboard == null) {
            val keyboard = PkrKeyboardView(this)
            keyboard.listener = PreviewListener()
            previewHolder.addView(keyboard)
            previewKeyboard = keyboard
        } else {
            previewKeyboard?.reloadConfig()
        }
        previewGroup.visibility = View.VISIBLE
        previewButton.setText(R.string.btn_preview_hide)
        previewInput.requestFocus()
    }

    /** Sends preview key presses into the in-app preview text box only. */
    private inner class PreviewListener : PkrKeyboardView.Listener {

        private fun selection(): Pair<Int, Int> {
            val length = previewInput.text.length
            val s = previewInput.selectionStart
            val e = previewInput.selectionEnd
            val a = if (s < 0) length else s
            val b = if (e < 0) length else e
            return minOf(a, b) to maxOf(a, b)
        }

        override fun onText(text: String) {
            val (start, end) = selection()
            previewInput.text.replace(start, end, text)
            previewInput.setSelection(start + text.length)
        }

        override fun onBackspace() {
            val (start, end) = selection()
            if (start != end) {
                previewInput.text.delete(start, end)
                previewInput.setSelection(start)
            } else if (start > 0) {
                previewInput.text.delete(start - 1, start)
                previewInput.setSelection(start - 1)
            }
        }

        override fun onEnter() = onText("\n")

        override fun onCursorMove(steps: Int) {
            val (start, _) = selection()
            previewInput.setSelection((start + steps).coerceIn(0, previewInput.text.length))
        }

        override fun onSymbolKey() {
            val drawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.pkr_symbol) ?: return
            val size = (resources.displayMetrics.density * 30f).toInt()
            drawable.setBounds(0, 0, size, size)
            // In-app preview only: a placeholder character carries the image span.
            val span = SpannableString("\uFFFC")
            span.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val (start, end) = selection()
            previewInput.text.replace(start, end, span)
            previewInput.setSelection(start + 1)
        }

        override fun onSymbolKeyLongPress() {
            if (!SymbolImage.share(this@MainActivity, getString(R.string.share_chooser_title))) {
                Toast.makeText(this@MainActivity, R.string.toast_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onSettings() {
            Toast.makeText(this@MainActivity, R.string.preview_settings_toast, Toast.LENGTH_SHORT).show()
        }
    }
}
