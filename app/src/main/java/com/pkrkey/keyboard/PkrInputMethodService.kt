package com.pkrkey.keyboard

import android.content.ClipDescription
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlin.math.abs

/**
 * PKR Key input method.
 *
 * Privacy: this class only forwards key presses to the focused text field.
 * It never reads, logs, stores or transmits what is typed, and has no network access.
 */
class PkrInputMethodService : InputMethodService(), PkrKeyboardView.Listener {

    private var keyboard: PkrKeyboardView? = null
    private var appliedSignature = ""

    override fun onCreateInputView(): View {
        val view = PkrKeyboardView(this)
        view.listener = this
        keyboard = view
        appliedSignature = KeyboardPrefs.signature(this)
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.reloadConfig()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val kb = keyboard ?: return

        val signature = KeyboardPrefs.signature(this)
        if (signature != appliedSignature) {
            kb.reloadConfig()
            appliedSignature = signature
        }

        if (!restarting) {
            val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
            val numeric = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            kb.setInitialPage(numeric)
        }
        updateAutoCaps()
    }

    // ---------------------------------------------------------------- PkrKeyboardView.Listener

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        updateAutoCaps()
    }

    override fun onBackspace() {
        // A key event deletes the selection if there is one, otherwise one character.
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoCaps()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info != null) {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noEnterAction &&
                action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updateAutoCaps()
    }

    override fun onCursorMove(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun onSettings() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requestHideSelf(0)
    }

    // ---------------------------------------------------------------- PKR symbol image

    override fun onSymbolKey() {
        if (!commitSymbolImage()) {
            // The app cannot take images from a keyboard: copy it so it can be pasted.
            val copied = SymbolImage.copyToClipboard(this)
            Toast.makeText(
                this,
                if (copied) R.string.toast_copied else R.string.toast_copy_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onSymbolKeyLongPress() {
        if (!SymbolImage.share(this, getString(R.string.share_chooser_title))) {
            Toast.makeText(this, R.string.toast_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sends the symbol with InputConnection.commitContent().
     * Returns false when the focused app does not accept PNG images from keyboards
     * or rejects the content, so the caller can use the fallback.
     */
    private fun commitSymbolImage(): Boolean {
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false

        val accepted = EditorInfoCompat.getContentMimeTypes(info).any {
            ClipDescription.compareMimeTypes(SymbolImage.MIME, it)
        }
        if (!accepted) return false

        return try {
            val uri = SymbolImage.uri(this)
            val description = ClipDescription("PKR symbol", arrayOf(SymbolImage.MIME))
            val content = InputContentInfoCompat(uri, description, null)

            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            }
            // Also grant read access to the target app directly.
            try {
                grantUriPermission(info.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // The commitContent flag above still requests the grant.
            }
            InputConnectionCompat.commitContent(ic, info, content, flags, null)
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun updateAutoCaps() {
        val kb = keyboard ?: return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val isText = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val capsMode = if (isText) ic.getCursorCapsMode(info.inputType) else 0
        kb.setAutoCaps(capsMode != 0)
    }
}
