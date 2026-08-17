package com.gabrielliz.translatedecria.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.gabrielliz.translatedecria.AppSettingsStore
import com.gabrielliz.translatedecria.MainActivity
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.overlay.OverlayController
import com.gabrielliz.translatedecria.translation.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class TranslatorAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settingsStore: AppSettingsStore
    private var translationEngine: TranslationEngine? = null
    private var overlayController: OverlayController? = null
    private var processingJob: Job? = null
    private var lastProcessAt = 0L
    private var lastSignature = 0
    private val paused = AtomicBoolean(false)
    private val translationsHidden = AtomicBoolean(false)
    private var forceRefresh = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        settingsStore = AppSettingsStore(this)
        val settings = settingsStore.load()
        if (settings.translationEnabled && settings.captureMode == CaptureMode.ACCESSIBILITY) {
            startTranslation()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val settings = if (::settingsStore.isInitialized) settingsStore.load() else return
        if (!settings.translationEnabled || settings.captureMode != CaptureMode.ACCESSIBILITY || paused.get()) return
        if (event?.packageName?.toString() == packageName) return

        val now = System.currentTimeMillis()
        val minInterval = 1000L / settings.analysesPerSecond.coerceIn(1, 5)
        if (!forceRefresh && now - lastProcessAt < minInterval) return
        lastProcessAt = now

        val blocks = AccessibilityTextCollector.collect(rootInActiveWindow, packageName)
        val signature = blocks.fold(17) { acc, block ->
            31 * acc + block.originalText.hashCode() + block.bounds.hashCode()
        }
        if (!forceRefresh && signature == lastSignature) return
        lastSignature = signature
        forceRefresh = false

        processingJob?.cancel()
        processingJob = scope.launch {
            val engine = translationEngine ?: return@launch
            val translated = runCatching {
                engine.translateBlocks(blocks, settings.sourceLanguage, settings.targetLanguage)
            }.getOrElse { emptyList() }

            mainHandler.post {
                if (!translationsHidden.get()) {
                    overlayController?.showTranslations(translated, settings)
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    fun startTranslation() {
        settingsStore.setTranslationEnabled(true)
        paused.set(false)
        translationsHidden.set(false)
        ensureSessionObjects()
        forceRefresh = true
        onAccessibilityEvent(null)
    }

    fun stopTranslation() {
        settingsStore.setTranslationEnabled(false)
        processingJob?.cancel()
        processingJob = null
        overlayController?.destroy()
        overlayController = null
        translationEngine?.close()
        translationEngine = null
        lastSignature = 0
        lastProcessAt = 0L
        paused.set(false)
        translationsHidden.set(false)
    }

    private fun ensureSessionObjects() {
        if (translationEngine == null) translationEngine = TranslationEngine()
        if (overlayController == null) {
            overlayController = OverlayController(
                context = this,
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                callback = object : OverlayController.ControlCallback {
                    override fun onTogglePause() {
                        paused.set(!paused.get())
                        overlayController?.setPaused(paused.get())
                    }

                    override fun onRefresh() {
                        forceRefresh = true
                        lastSignature = 0
                        onAccessibilityEvent(null)
                    }

                    override fun onToggleTranslations() {
                        translationsHidden.set(!translationsHidden.get())
                        overlayController?.setTranslationsHidden(translationsHidden.get())
                    }

                    override fun onOpenSettings() {
                        startActivity(
                            Intent(this@TranslatorAccessibilityService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    }

                    override fun onStop() = stopTranslation()
                }
            ).also { it.showControlBubble() }
        }
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        stopTranslation()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: WeakReference<TranslatorAccessibilityService>? = null

        fun requestStart(): Boolean = instance?.get()?.let {
            it.startTranslation()
            true
        } ?: false

        fun requestStop() {
            instance?.get()?.stopTranslation()
        }
    }
}
