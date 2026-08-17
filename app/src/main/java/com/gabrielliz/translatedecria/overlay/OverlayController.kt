package com.gabrielliz.translatedecria.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.TranslatedBlock
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
    }

    fun showTranslations(blocks: List<TranslatedBlock>, settings: SettingsSnapshot) {
        if (translationsHidden) return
        val layer = ensureTranslationLayer()
        layer.removeAllViews()
        if (blocks.isEmpty()) return

        val metrics = appContext.resources.displayMetrics
        val placements = OverlayLayoutEngine.resolve(
            bounds = blocks.map { OverlayLayoutEngine.Box(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            gapPx = dp(4)
        )

        blocks.zip(placements).forEach { (block, placement) ->
            val rect = placement.placed
            val textView = TextView(appContext).apply {
                text = if (settings.showOriginal) {
                    "${block.originalText}\n${block.translatedText}"
                } else {
                    block.translatedText
                }
                setTextColor(Color.WHITE)
                textSize = min(settings.fontSizeSp, (rect.height / metrics.density * 0.60f).coerceAtLeast(11f))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(3), dp(5), dp(3))
                background = roundedBackground(
                    Color.argb((settings.overlayOpacity * 235).toInt().coerceIn(70, 235), 14, 14, 18),
                    dp(5).toFloat()
                )
                maxLines = 6
            }

            val params = FrameLayout.LayoutParams(rect.width, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = rect.left
                topMargin = rect.top
            }
            layer.addView(textView, params)
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
        rebuildControls()
    }

    fun setTranslationsHidden(hidden: Boolean) {
        translationsHidden = hidden
        translationLayer?.visibility = if (hidden) View.GONE else View.VISIBLE
        rebuildControls()
    }

    fun clearTranslations() {
        translationLayer?.removeAllViews()
    }

    fun destroy() {
        translationLayer?.let { runCatching { windowManager.removeViewImmediate(it) } }
        controlRoot?.let { runCatching { windowManager.removeViewImmediate(it) } }
        translationLayer = null
        controlRoot = null
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
        if (!expanded) {
            root.addView(controlButton("T") { expanded = true; rebuildControls() })
            return
        }
        root.addView(controlButton(if (paused) "Continuar" else "Pausar") { callback.onTogglePause() })
        root.addView(controlButton("Traduzir") { callback.onRefresh() })
        root.addView(controlButton(if (translationsHidden) "Mostrar" else "Ocultar") { callback.onToggleTranslations() })
        root.addView(controlButton("Config") { callback.onOpenSettings() })
        root.addView(controlButton("Fechar") { callback.onStop() })
        root.addView(controlButton("‹") { expanded = false; rebuildControls() })
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
