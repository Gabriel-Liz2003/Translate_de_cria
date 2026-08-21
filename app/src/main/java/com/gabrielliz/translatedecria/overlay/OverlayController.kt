package com.gabrielliz.translatedecria.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.TranslatedBlock
import kotlin.math.max
import kotlin.math.min

class OverlayController(
    context: Context,
    private val windowType: Int,
    private val callback: ControlCallback
) {
    interface ControlCallback {
        fun onTogglePause()
        fun onRefresh()
        fun onToggleTranslations()
        fun onOpenSettings()
        fun onStop()
    }

    private val appContext = context
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var translationLayer: FrameLayout? = null
    private var controlRoot: LinearLayout? = null
    private var expanded = false
    private var paused = false
    private var translationsHidden = false

    @Volatile
    private var renderedTranslationBounds: List<Rect> = emptyList()

    @Volatile
    private var renderedControlBounds: Rect? = null

    fun showControlBubble() {
        if (controlRoot != null) return
        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(Color.argb(225, 32, 30, 36), dp(22).toFloat())
        }
        controlRoot = root
        rebuildControls()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(96)
        }
        windowManager.addView(root, params)
        updateControlBoundsWhenLaidOut(root)
    }

    fun showTranslations(blocks: List<TranslatedBlock>, settings: SettingsSnapshot) {
        if (translationsHidden) return
        val layer = ensureTranslationLayer()
        layer.removeAllViews()
        if (blocks.isEmpty()) {
            renderedTranslationBounds = emptyList()
            return
        }

        val metrics = appContext.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val rendered = mutableListOf<Rect>()

        blocks.forEach { block ->
            val source = block.bounds
            val minWidth = dp(120)
            val desiredWidth = max(source.width(), minWidth).coerceAtMost(screenWidth)
            val left = source.left.coerceIn(0, (screenWidth - desiredWidth).coerceAtLeast(0))
            val top = source.top.coerceIn(0, (screenHeight - dp(32)).coerceAtLeast(0))

            val textView = TextView(appContext).apply {
                text = if (settings.showOriginal) {
                    "${block.originalText}\n${block.translatedText}"
                } else {
                    block.translatedText
                }
                setTextColor(Color.WHITE)
                textSize = min(settings.fontSizeSp, (source.height() / metrics.density * 0.72f).coerceIn(11f, 18f))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = roundedBackground(
                    Color.argb((settings.overlayOpacity * 245).toInt().coerceIn(150, 245), 16, 16, 20),
                    dp(4).toFloat()
                )
                maxLines = 3
            }

            val params = FrameLayout.LayoutParams(desiredWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = left
                topMargin = top
            }
            layer.addView(textView, params)
            textView.post {
                if (!textView.isAttachedToWindow) return@post
                val bottom = (top + textView.height).coerceAtMost(screenHeight)
                rendered += Rect(left, top, left + desiredWidth, bottom)
                renderedTranslationBounds = rendered.map(::Rect)
            }
        }
    }

    fun translationBoundsSnapshot(): List<Rect> = renderedTranslationBounds.map(::Rect)

    fun controlBoundsSnapshot(): Rect? = renderedControlBounds?.let(::Rect)

    fun setPaused(value: Boolean) {
        paused = value
        rebuildControls()
    }

    fun setTranslationsHidden(hidden: Boolean) {
        translationsHidden = hidden
        translationLayer?.visibility = if (hidden) View.GONE else View.VISIBLE
        if (hidden) renderedTranslationBounds = emptyList()
        rebuildControls()
    }

    fun clearTranslations() {
        translationLayer?.removeAllViews()
        renderedTranslationBounds = emptyList()
    }

    fun destroy() {
        translationLayer?.let { runCatching { windowManager.removeViewImmediate(it) } }
        controlRoot?.let { runCatching { windowManager.removeViewImmediate(it) } }
        translationLayer = null
        controlRoot = null
        renderedTranslationBounds = emptyList()
        renderedControlBounds = null
    }

    private fun ensureTranslationLayer(): FrameLayout {
        translationLayer?.let { return it }
        val layer = FrameLayout(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(layer, params)
        translationLayer = layer
        return layer
    }

    private fun rebuildControls() {
        val root = controlRoot ?: return
        root.removeAllViews()
        root.orientation = if (expanded) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        root.gravity = if (expanded) Gravity.END else Gravity.CENTER_VERTICAL

        if (!expanded) {
            root.addView(controlButton("T") { expanded = true; rebuildControls() })
            updateControlBoundsWhenLaidOut(root)
            return
        }

        addExpandedButton(root, if (paused) "Continuar" else "Pausar") { callback.onTogglePause() }
        addExpandedButton(root, "Traduzir novamente") { callback.onRefresh() }
        addExpandedButton(root, if (translationsHidden) "Mostrar traduções" else "Ocultar traduções") { callback.onToggleTranslations() }
        addExpandedButton(root, "Configurações") { callback.onOpenSettings() }
        addExpandedButton(root, "Encerrar") { callback.onStop() }
        addExpandedButton(root, "Recolher") { expanded = false; rebuildControls() }
        updateControlBoundsWhenLaidOut(root)
    }

    private fun addExpandedButton(root: LinearLayout, label: String, onClick: () -> Unit) {
        val button = controlButton(label, onClick).apply {
            textSize = 12f
            maxLines = 1
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(dp(154), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun updateControlBoundsWhenLaidOut(root: View) {
        root.post {
            if (!root.isAttachedToWindow || root.width <= 0 || root.height <= 0) return@post
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            renderedControlBounds = Rect(
                location[0],
                location[1],
                location[0] + root.width,
                location[1] + root.height
            )
        }
    }

    private fun controlButton(label: String, onClick: () -> Unit): Button = Button(appContext).apply {
        text = label
        textSize = 11f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(7), dp(2), dp(7), dp(2))
        setOnClickListener { onClick() }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()
}
