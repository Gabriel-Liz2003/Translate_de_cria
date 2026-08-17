package com.gabrielliz.translatedecria.ocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.gabrielliz.translatedecria.AppSettingsStore
import com.gabrielliz.translatedecria.MainActivity
import com.gabrielliz.translatedecria.R
import com.gabrielliz.translatedecria.overlay.OverlayController
import com.gabrielliz.translatedecria.translation.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ScreenOcrService : Service() {
    private val workerThread = HandlerThread("screen-ocr-capture")
    private lateinit var workerHandler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsStore: AppSettingsStore
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlayController: OverlayController? = null
    private var ocrEngine: OcrEngine? = null
    private var translationEngine: TranslationEngine? = null
    private val processing = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val translationsHidden = AtomicBoolean(false)
    private val releasing = AtomicBoolean(false)
    private val changeDetector = FrameChangeDetector()
    private var lastProcessAt = 0L
    private var forceRefresh = true

    override fun onCreate() {
        super.onCreate()
        settingsStore = AppSettingsStore(this)
        workerThread.start()
        workerHandler = Handler(workerThread.looper)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (mediaProjection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = extractProjectionData(intent)
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startProjectionForeground()
        startProjection(resultCode, resultData)
        return START_NOT_STICKY
    }

    private fun startProjectionForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!releasing.get()) stopSelf()
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    workerHandler.post { resizeCapture(width, height) }
                }
            }
        }, workerHandler)

        runCatching {
            ocrEngine = OcrEngine(this)
            translationEngine = TranslationEngine(this)
        }.onFailure {
            stopSelf()
            return
        }

        overlayController = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            callback = object : OverlayController.ControlCallback {
                override fun onTogglePause() {
                    paused.set(!paused.get())
                    overlayController?.setPaused(paused.get())
                }

                override fun onRefresh() {
                    changeDetector.reset()
                    forceRefresh = true
                }

                override fun onToggleTranslations() {
                    translationsHidden.set(!translationsHidden.get())
                    overlayController?.setTranslationsHidden(translationsHidden.get())
                }

                override fun onOpenSettings() {
                    startActivity(
                        Intent(this@ScreenOcrService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }

                override fun onStop() = stopSelf()
            }
        ).also { it.showControlBubble() }

        val metrics = resources.displayMetrics
        createCaptureSurface(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        settingsStore.setTranslationEnabled(true)
    }

    private fun createCaptureSurface(width: Int, height: Int, densityDpi: Int) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val reader = ImageReader.newInstance(safeWidth, safeHeight, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ source -> onImageAvailable(source) }, workerHandler)
        imageReader = reader

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TranslateDeCriaScreen",
            safeWidth,
            safeHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            workerHandler
        )
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        if (paused.get() || processing.get()) {
            image.close()
            return
        }

        val settings = settingsStore.load()
        val now = System.currentTimeMillis()
        val minInterval = 1000L / settings.analysesPerSecond.coerceIn(1, 5)
        if (!forceRefresh && now - lastProcessAt < minInterval) {
            image.close()
            return
        }
        lastProcessAt = now

        val bitmap = try {
            copyImageToBitmap(image)
        } finally {
            image.close()
        } ?: return

        if (!forceRefresh && !changeDetector.hasChanged(bitmap)) {
            bitmap.recycle()
            return
        }
        if (forceRefresh) changeDetector.hasChanged(bitmap)
        forceRefresh = false
        processing.set(true)

        val excludedOverlayBounds = buildList {
            addAll(overlayController?.translationBoundsSnapshot().orEmpty())
            overlayController?.controlBoundsSnapshot()?.let(::add)
        }

        scope.launch {
            try {
                val recognized = ocrEngine?.recognize(bitmap, settings.sourceLanguage).orEmpty()
                val blocks = recognized.filterNot { block -> isOwnOverlay(block.bounds, excludedOverlayBounds) }
                if (!bitmap.isRecycled) bitmap.recycle()

                val translated = translationEngine?.translateBlocks(
                    blocks,
                    settings.sourceLanguage,
                    settings.targetLanguage
                ).orEmpty()
                if (!translationsHidden.get()) {
                    mainHandler.post { overlayController?.showTranslations(translated, settings) }
                }
            } catch (_: Throwable) {
                // Intentionally no captured text, image or exception payload is logged.
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                processing.set(false)
            }
        }
    }

    private fun isOwnOverlay(bounds: Rect, excluded: List<Rect>): Boolean {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        return excluded.any { it.contains(centerX, centerY) }
    }

    private fun copyImageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        if (paddedWidth <= 0 || image.height <= 0) return null

        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded

        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun resizeCapture(width: Int, height: Int) {
        val display = virtualDisplay ?: return
        val oldReader = imageReader
        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        newReader.setOnImageAvailableListener({ source -> onImageAvailable(source) }, workerHandler)
        display.resize(width, height, resources.displayMetrics.densityDpi)
        display.surface = newReader.surface
        imageReader = newReader
        oldReader?.setOnImageAvailableListener(null, null)
        oldReader?.close()
        changeDetector.reset()
        forceRefresh = true
    }

    override fun onDestroy() {
        releasing.set(true)
        settingsStore.setTranslationEnabled(false)
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        overlayController?.destroy()
        overlayController = null
        ocrEngine?.close()
        ocrEngine = null
        translationEngine?.close()
        translationEngine = null
        scope.cancel()
        workerThread.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Indica quando a sessão OCR local por MediaProjection está ativa."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun extractProjectionData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    companion object {
        const val ACTION_START = "com.gabrielliz.translatedecria.action.START_OCR"
        const val ACTION_STOP = "com.gabrielliz.translatedecria.action.STOP_OCR"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_translation"
        private const val NOTIFICATION_ID = 1001
    }
}
