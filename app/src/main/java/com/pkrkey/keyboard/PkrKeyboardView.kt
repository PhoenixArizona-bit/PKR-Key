package com.pkrkey.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/**
 * The keyboard UI. It knows nothing about text fields: every action goes to [listener].
 * The same view is used by the real input method service and by the in-app preview.
 */
class PkrKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        /** Negative = left, positive = right. */
        fun onCursorMove(steps: Int)
        fun onSymbolKey()
        fun onSymbolKeyLongPress()
        fun onSettings()
    }

    private enum class Page { LETTERS, SYMBOLS_1, SYMBOLS_2 }
    private enum class Shift { OFF, ONCE, LOCK }
    private enum class KeyStyle { NORMAL, SPECIAL, ACCENT }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardThemes.emerald
    private var keyHeightPx = 0
    private var showSymbolKey = true
    private var page = Page.LETTERS
    private var shift = Shift.OFF
    private var shiftAuto = false
    private var lastShiftTap = 0L
    private val letterLabels = mutableListOf<TextView>()
    private var shiftFrame: FrameLayout? = null
    private var shiftIcon: ImageView? = null

    private val symbols1 = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )
    private val symbols2 = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("€", "£", "¥", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("[", "]", "<", ">", "_", "Rs")
    )

    init {
        orientation = LinearLayout.VERTICAL
        reloadConfig()
    }

    // ---------------------------------------------------------------- public API

    /** Re-reads theme, key height and symbol-key visibility, then rebuilds the keys. */
    fun reloadConfig() {
        theme = KeyboardThemes.byId(KeyboardPrefs.themeId(context))
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val heightDp = KeyboardPrefs.keyHeightDp(context)
        keyHeightPx = dp(if (landscape) (heightDp * 0.8f).toInt() else heightDp)
        showSymbolKey = KeyboardPrefs.showSymbolKey(context)
        setBackgroundColor(theme.background)
        setPadding(dp(4), dp(4), dp(4), dp(6))
        buildKeys()
    }

    /** Called when a new text field gets focus. */
    fun setInitialPage(symbols: Boolean) {
        page = if (symbols) Page.SYMBOLS_1 else Page.LETTERS
        shift = Shift.OFF
        shiftAuto = false
        buildKeys()
    }

    /** The host asks for sentence-style capitalisation. Never overrides a manual shift choice. */
    fun setAutoCaps(wanted: Boolean) {
        if (page != Page.LETTERS) return
        if (wanted && shift == Shift.OFF) {
            shift = Shift.ONCE
            shiftAuto = true
            refreshShiftState()
        } else if (!wanted && shift == Shift.ONCE && shiftAuto) {
            shift = Shift.OFF
            shiftAuto = false
            refreshShiftState()
        }
    }

    // ---------------------------------------------------------------- building

    private fun buildKeys() {
        removeAllViews()
        letterLabels.clear()
        shiftFrame = null
        shiftIcon = null

        when (page) {
            Page.LETTERS -> {
                val r1 = newRow()
                "qwertyuiop".forEach { r1.addView(letterKey(it.toString())) }
                addView(r1)

                val r2 = newRow()
                r2.addView(spacer(0.5f))
                "asdfghjkl".forEach { r2.addView(letterKey(it.toString())) }
                r2.addView(spacer(0.5f))
                addView(r2)

                val r3 = newRow()
                r3.addView(buildShiftKey())
                "zxcvbnm".forEach { r3.addView(letterKey(it.toString())) }
                r3.addView(backspaceKey())
                addView(r3)
            }
            Page.SYMBOLS_1, Page.SYMBOLS_2 -> {
                val rows = if (page == Page.SYMBOLS_1) symbols1 else symbols2
                addView(symbolRow(rows[0]))
                addView(symbolRow(rows[1]))

                val r3 = newRow()
                val toggleLabel = if (page == Page.SYMBOLS_1) "=\\<" else "?123"
                val toggle = textKey(toggleLabel, 1.5f, KeyStyle.SPECIAL, 15f)
                attach(toggle) {
                    page = if (page == Page.SYMBOLS_1) Page.SYMBOLS_2 else Page.SYMBOLS_1
                    buildKeys()
                }
                r3.addView(toggle)
                rows[2].forEach { r3.addView(charKey(it)) }
                r3.addView(backspaceKey())
                addView(r3)
            }
        }
        addView(bottomRow())
    }

    private fun symbolRow(labels: List<String>): LinearLayout {
        val row = newRow()
        labels.forEach { row.addView(charKey(it)) }
        return row
    }

    private fun bottomRow(): LinearLayout {
        val row = newRow()

        val mode = textKey(if (page == Page.LETTERS) "?123" else "ABC", 1.4f, KeyStyle.SPECIAL, 15f)
        attach(mode) {
            page = if (page == Page.LETTERS) Page.SYMBOLS_1 else Page.LETTERS
            buildKeys()
        }
        row.addView(mode)

        val comma = textKey(",", 1f)
        attach(comma) { listener?.onText(",") }
        row.addView(comma)

        val settings = iconKey(R.drawable.ic_tune, context.getString(R.string.cd_settings), 1f)
        attach(settings) { listener?.onSettings() }
        row.addView(settings)

        if (showSymbolKey) row.addView(buildSymbolKey(1.5f))

        val space = textKey("English", if (showSymbolKey) 3f else 4.5f, KeyStyle.NORMAL, 13f)
        space.contentDescription = context.getString(R.string.cd_space)
        attach(space, onDrag = { steps -> listener?.onCursorMove(steps) }) { listener?.onText(" ") }
        row.addView(space)

        val dot = textKey(".", 1f)
        attach(dot) { listener?.onText(".") }
        row.addView(dot)

        val enter = iconKey(R.drawable.ic_enter, context.getString(R.string.cd_enter), 1.4f, KeyStyle.ACCENT)
        attach(enter) { listener?.onEnter() }
        row.addView(enter)
        return row
    }

    private fun letterKey(base: String): FrameLayout {
        val key = textKey(if (shift == Shift.OFF) base else base.uppercase(), 1f)
        val label = key.getChildAt(0) as TextView
        label.tag = base
        letterLabels.add(label)
        attach(key) {
            val out = if (shift == Shift.OFF) base else base.uppercase()
            if (shift == Shift.ONCE) {
                shift = Shift.OFF
                shiftAuto = false
                refreshShiftState()
            }
            listener?.onText(out)
        }
        return key
    }

    private fun charKey(label: String): FrameLayout {
        val key = textKey(label, 1f, KeyStyle.NORMAL, if (label.length > 1) 16f else 20f)
        attach(key) { listener?.onText(label) }
        return key
    }

    private fun backspaceKey(): FrameLayout {
        val key = iconKey(R.drawable.ic_backspace, context.getString(R.string.cd_backspace), 1.5f)
        attach(key, repeat = true) { listener?.onBackspace() }
        return key
    }

    private fun buildShiftKey(): FrameLayout {
        val key = iconKey(shiftIconRes(), context.getString(R.string.cd_shift), 1.5f, shiftStyle())
        shiftFrame = key
        shiftIcon = key.getChildAt(0) as ImageView
        attach(key) { onShiftTap() }
        return key
    }

    /** The dedicated key. Shows the PKR symbol image, and sends it as an image. */
    private fun buildSymbolKey(weight: Float): FrameLayout {
        val key = frame(KeyStyle.NORMAL, weight, stroke = theme.accent, overrideBg = SYMBOL_TILE_COLOR)
        key.contentDescription = context.getString(R.string.cd_symbol_key)
        val image = ImageView(context).apply {
            setImageResource(R.drawable.pkr_symbol)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        key.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        attach(
            key,
            onLongPress = { listener?.onSymbolKeyLongPress() }
        ) { listener?.onSymbolKey() }
        return key
    }

    // ---------------------------------------------------------------- shift

    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        shiftAuto = false
        shift = when (shift) {
            Shift.OFF -> Shift.ONCE
            Shift.ONCE -> if (now - lastShiftTap < 350L) Shift.LOCK else Shift.OFF
            Shift.LOCK -> Shift.OFF
        }
        lastShiftTap = now
        refreshShiftState()
    }

    private fun shiftStyle(): KeyStyle = if (shift == Shift.OFF) KeyStyle.SPECIAL else KeyStyle.ACCENT

    private fun shiftIconRes(): Int =
        if (shift == Shift.LOCK) R.drawable.ic_shift_lock else R.drawable.ic_shift

    private fun refreshShiftState() {
        letterLabels.forEach { label ->
            val base = label.tag as String
            label.text = if (shift == Shift.OFF) base else base.uppercase()
        }
        val style = shiftStyle()
        val frame = shiftFrame ?: return
        val normal = bgFor(style)
        frame.background = keyBackground(normal, pressedColor(normal, style), null)
        shiftIcon?.apply {
            setImageResource(shiftIconRes())
            setColorFilter(fgFor(style), PorterDuff.Mode.SRC_IN)
        }
    }

    // ---------------------------------------------------------------- view factories

    private fun newRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH, keyHeightPx + dp(6))
    }

    private fun spacer(weight: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, MATCH, weight)
    }

    private fun keyParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, MATCH, weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }

    private fun bgFor(style: KeyStyle): Int = when (style) {
        KeyStyle.NORMAL -> theme.key
        KeyStyle.SPECIAL -> theme.specialKey
        KeyStyle.ACCENT -> theme.accent
    }

    private fun fgFor(style: KeyStyle): Int = when (style) {
        KeyStyle.NORMAL -> theme.keyText
        KeyStyle.SPECIAL -> theme.specialText
        KeyStyle.ACCENT -> theme.onAccent
    }

    private fun pressedColor(normal: Int, style: KeyStyle): Int =
        if (style == KeyStyle.ACCENT) ColorUtils.blendARGB(normal, Color.BLACK, 0.2f)
        else ColorUtils.blendARGB(normal, theme.accent, 0.3f)

    private fun keyBackground(normal: Int, pressed: Int, stroke: Int?): StateListDrawable {
        fun box(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(color)
            if (stroke != null) setStroke(dp(2), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), box(pressed))
            addState(intArrayOf(), box(normal))
        }
    }

    private fun frame(
        style: KeyStyle,
        weight: Float,
        stroke: Int? = null,
        overrideBg: Int? = null
    ): FrameLayout {
        val normal = overrideBg ?: bgFor(style)
        return FrameLayout(context).apply {
            layoutParams = keyParams(weight)
            background = keyBackground(normal, pressedColor(normal, style), stroke)
        }
    }

    private fun textKey(
        label: String,
        weight: Float,
        style: KeyStyle = KeyStyle.NORMAL,
        sp: Float = 20f
    ): FrameLayout {
        val key = frame(style, weight)
        val text = TextView(context).apply {
            this.text = label
            setTextColor(fgFor(style))
            textSize = sp
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        key.addView(text, FrameLayout.LayoutParams(MATCH, MATCH))
        return key
    }

    private fun iconKey(
        @DrawableRes res: Int,
        description: String,
        weight: Float,
        style: KeyStyle = KeyStyle.SPECIAL
    ): FrameLayout {
        val key = frame(style, weight)
        key.contentDescription = description
        val icon = ImageView(context).apply {
            setImageResource(res)
            setColorFilter(fgFor(style), PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        key.addView(icon, FrameLayout.LayoutParams(MATCH, MATCH))
        return key
    }

    private fun attach(
        key: View,
        repeat: Boolean = false,
        onLongPress: (() -> Unit)? = null,
        onDrag: ((Int) -> Unit)? = null,
        onTap: () -> Unit
    ) {
        key.setOnTouchListener(KeyTouch(repeat, onLongPress, onDrag, onTap))
        // Used by accessibility services (TalkBack double-tap), since touches are handled above.
        key.setOnClickListener { onTap() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    // ---------------------------------------------------------------- touch handling

    /**
     * One handler per key.
     * - tap: fires on release
     * - repeat: fires on press, then repeats while held (backspace)
     * - long press: fires once after a delay and cancels the tap
     * - drag: horizontal movement reports steps (space bar cursor movement) and cancels the tap
     */
    @SuppressLint("ClickableViewAccessibility")
    private inner class KeyTouch(
        private val repeat: Boolean,
        private val onLongPress: (() -> Unit)?,
        private val onDrag: ((Int) -> Unit)?,
        private val onTap: () -> Unit
    ) : View.OnTouchListener {

        private val handler = Handler(Looper.getMainLooper())
        private var target: View? = null
        private var startX = 0f
        private var lastX = 0f
        private var consumed = false
        private var dragging = false

        private val longPressRunnable = Runnable {
            if (!dragging) {
                consumed = true
                target?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongPress?.invoke()
            }
        }

        private val repeatRunnable = object : Runnable {
            override fun run() {
                val t = target
                if (t == null || !t.isPressed || !t.isAttachedToWindow) return
                onTap()
                handler.postDelayed(this, 45L)
            }
        }

        private fun stopCallbacks() {
            handler.removeCallbacks(longPressRunnable)
            handler.removeCallbacks(repeatRunnable)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target = v
                    v.isPressed = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    consumed = false
                    dragging = false
                    startX = event.rawX
                    lastX = event.rawX
                    if (repeat) {
                        onTap()
                        handler.postDelayed(repeatRunnable, 400L)
                    } else if (onLongPress != null) {
                        handler.postDelayed(longPressRunnable, 450L)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val drag = onDrag
                    if (drag != null) {
                        if (!dragging && abs(event.rawX - startX) > dp(14).toFloat()) {
                            dragging = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        if (dragging) {
                            val step = dp(16).toFloat()
                            val n = ((event.rawX - lastX) / step).toInt()
                            if (n != 0) {
                                drag(n)
                                lastX += n * step
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    stopCallbacks()
                    v.isPressed = false
                    if (!repeat && !consumed && !dragging) onTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopCallbacks()
                    v.isPressed = false
                }
            }
            return true
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        /** Same emerald as the symbol tile image, so the key blends with the artwork. */
        val SYMBOL_TILE_COLOR = 0xFF064E3B.toInt()
    }
}
