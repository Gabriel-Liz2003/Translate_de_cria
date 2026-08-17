package com.gabrielliz.translatedecria

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrielliz.translatedecria.accessibility.TranslatorAccessibilityService
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.model.TargetLanguage
import com.gabrielliz.translatedecria.ocr.ScreenOcrService
import com.gabrielliz.translatedecria.translation.OfflineModelManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: AppSettingsStore
    private var settings by mutableStateOf(SettingsSnapshot())
    private var accessibilityEnabled by mutableStateOf(false)
    private var overlayPermission by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Pronto")
    private var showOcrPrivacyDialog by mutableStateOf(false)

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val startIntent = Intent(this, ScreenOcrService::class.java).apply {
                action = ScreenOcrService.ACTION_START
                putExtra(ScreenOcrService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenOcrService.EXTRA_RESULT_DATA, result.data)
            }
            settings = settings.copy(translationEnabled = true)
            settingsStore.save(settings)
            startForegroundService(startIntent)
            statusMessage = "OCR local ativo. Nenhum frame é salvo."
        } else {
            settingsStore.setTranslationEnabled(false)
            statusMessage = "Captura não autorizada. Nada foi capturado."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(this)
        settings = settingsStore.load()
        refreshPermissionState()

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        settings = settingsStore.load()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        val scope = rememberCoroutineScope()
        var modelDownloadState by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Translate de Cria") }) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Privacidade primeiro", fontWeight = FontWeight.Bold)
                            Text(
                                "Accessibility não captura a tela. No modo OCR, frames existem apenas em RAM durante o processamento local; não há screenshots, vídeo, armazenamento, analytics ou envio do conteúdo da tela."
                            )
                            Text(
                                "Conteúdo protegido por FLAG_SECURE pode aparecer em branco e não será contornado.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item {
                    SettingsCard(
                        settings = settings,
                        onChange = { updated ->
                            settings = updated
                            settingsStore.save(updated)
                        }
                    )
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Permissões e serviços", fontWeight = FontWeight.Bold)
                            StatusRow("Accessibility", accessibilityEnabled)
                            StatusRow("Sobrepor outros apps (necessário no OCR)", overlayPermission)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openAccessibilitySettings() }) { Text("Accessibility") }
                                Button(onClick = { openOverlaySettings() }) { Text("Overlay") }
                            }
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Modelos offline", fontWeight = FontWeight.Bold)
                            Text("OCR e identificação de idioma já vêm no APK. Baixe uma vez os modelos EN/JA/ZH/KO → PT para traduzir depois sem internet.")
                            Button(
                                enabled = modelDownloadState?.startsWith("Baixando") != true,
                                onClick = {
                                    modelDownloadState = "Baixando 0/4…"
                                    scope.launch {
                                        runCatching {
                                            OfflineModelManager.downloadEssentials { completed, total ->
                                                modelDownloadState = "Baixando $completed/$total…"
                                            }
                                        }.onSuccess {
                                            modelDownloadState = "Modelos essenciais disponíveis offline."
                                        }.onFailure {
                                            modelDownloadState = "Falha no download. Verifique a internet e tente novamente."
                                        }
                                    }
                                }
                            ) { Text("Baixar modelos essenciais") }
                            modelDownloadState?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { activateTranslation() }
                        ) {
                            Text("Ativar tradução")
                        }
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { stopTranslation() }
                        ) {
                            Text("Encerrar tradução e limpar cache em memória")
                        }
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        if (showOcrPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showOcrPrivacyDialog = false },
                title = { Text("OCR da tela: consentimento e privacidade") },
                text = {
                    Text(
                        "O Android pedirá autorização para uma sessão de MediaProjection. Frames serão copiados temporariamente para RAM, processados localmente pelo ML Kit e descartados imediatamente. Nenhuma imagem, vídeo ou texto reconhecido é salvo ou enviado. Ao encerrar a sessão, ImageReader, VirtualDisplay, MediaProjection, bitmaps, cache e overlays são liberados."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showOcrPrivacyDialog = false
                        requestProjection()
                    }) { Text("Continuar") }
                },
                dismissButton = {
                    TextButton(onClick = { showOcrPrivacyDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }

    @Composable
    private fun SettingsCard(settings: SettingsSnapshot, onChange: (SettingsSnapshot) -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Configurações", fontWeight = FontWeight.Bold)

                DropdownSetting("Idioma de origem", settings.sourceLanguage, SourceLanguage.entries) {
                    onChange(settings.copy(sourceLanguage = it))
                }
                DropdownSetting("Idioma de destino", settings.targetLanguage, TargetLanguage.entries) {
                    onChange(settings.copy(targetLanguage = it))
                }
                DropdownSetting("Modo de captura", settings.captureMode, CaptureMode.entries) {
                    onChange(settings.copy(captureMode = it))
                }

                Text("Análises por segundo: ${settings.analysesPerSecond}")
                Slider(
                    value = settings.analysesPerSecond.toFloat(),
                    onValueChange = { onChange(settings.copy(analysesPerSecond = it.toInt().coerceIn(1, 5))) },
                    valueRange = 1f..5f,
                    steps = 3
                )

                Text("Tamanho máximo da fonte: ${settings.fontSizeSp.toInt()} sp")
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = { onChange(settings.copy(fontSizeSp = it)) },
                    valueRange = 12f..28f,
                    steps = 15
                )

                Text("Transparência: ${(settings.overlayOpacity * 100).toInt()}%")
                Slider(
                    value = settings.overlayOpacity,
                    onValueChange = { onChange(settings.copy(overlayOpacity = it)) },
                    valueRange = 0.35f..1f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar texto original")
                        Text("Exibe original + tradução no overlay", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.showOriginal,
                        onCheckedChange = { onChange(settings.copy(showOriginal = it)) }
                    )
                }

                if (settings.sourceLanguage == SourceLanguage.AUTO && settings.captureMode == CaptureMode.OCR) {
                    Text(
                        "Automático no OCR consulta os quatro reconhecedores locais quando a imagem muda. Selecionar o idioma manualmente reduz CPU e bateria.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    private fun <T> DropdownSetting(label: String, selected: T, options: List<T>, onSelected: (T) -> Unit) where T : Enum<T> {
        var expanded by remember { mutableStateOf(false) }
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Button(onClick = { expanded = true }) {
                Text(enumLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(enumLabel(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }

    private fun enumLabel(value: Any): String = when (value) {
        is SourceLanguage -> value.label
        is TargetLanguage -> value.label
        is CaptureMode -> value.label
        else -> value.toString()
    }

    @Composable
    private fun StatusRow(label: String, enabled: Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(if (enabled) "Ativo" else "Desativado", fontWeight = FontWeight.SemiBold)
        }
    }

    private fun activateTranslation() {
        when (settings.captureMode) {
            CaptureMode.ACCESSIBILITY -> {
                if (!accessibilityEnabled) {
                    statusMessage = "Ative o serviço de Accessibility e volte ao app."
                    openAccessibilitySettings()
                    return
                }
                stopService(Intent(this, ScreenOcrService::class.java))
                settings = settings.copy(translationEnabled = true)
                settingsStore.save(settings)
                val connected = TranslatorAccessibilityService.requestStart()
                statusMessage = if (connected) {
                    "Tradução por Accessibility ativa. Sem captura de tela."
                } else {
                    "Serviço habilitado; aguardando o Android conectá-lo."
                }
            }

            CaptureMode.OCR -> {
                if (!overlayPermission) {
                    statusMessage = "Autorize 'sobrepor outros apps' para mostrar as traduções no modo OCR."
                    openOverlaySettings()
                    return
                }
                TranslatorAccessibilityService.requestStop()
                showOcrPrivacyDialog = true
            }
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopTranslation() {
        settings = settings.copy(translationEnabled = false)
        settingsStore.save(settings)
        TranslatorAccessibilityService.requestStop()
        stopService(Intent(this, ScreenOcrService::class.java))
        statusMessage = "Tradução encerrada; caches e overlays da sessão foram descartados."
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun refreshPermissionState() {
        overlayPermission = Settings.canDrawOverlays(this)
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val target = ComponentName(this, TranslatorAccessibilityService::class.java)
        accessibilityEnabled = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(serviceInfo.packageName, serviceInfo.name) == target
            }
    }
}
