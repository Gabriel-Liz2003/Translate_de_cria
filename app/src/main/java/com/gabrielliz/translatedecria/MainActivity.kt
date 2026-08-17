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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gabrielliz.translatedecria.accessibility.TranslatorAccessibilityService
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.model.TargetLanguage
import com.gabrielliz.translatedecria.ocr.ScreenOcrService
import com.gabrielliz.translatedecria.translation.SystemTranslationSupport
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var translationSupport: SystemTranslationSupport
    private var settings by mutableStateOf(SettingsSnapshot())
    private var accessibilityEnabled by mutableStateOf(false)
    private var overlayPermission by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Pronto")
    private var translationModelsMessage by mutableStateOf("Verificando tradução on-device…")
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
        translationSupport = SystemTranslationSupport(this)
        settings = settingsStore.load()
        refreshPermissionState()
        refreshTranslationSupport()
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
        if (::translationSupport.isInitialized) refreshTranslationSupport()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text("Translate de Cria") }) }) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Privacidade primeiro", fontWeight = FontWeight.Bold)
                            Text("Accessibility não captura a tela. No OCR, frames existem somente em RAM durante o processamento local e são descartados em seguida.")
                            Text(
                                "O app não possui permissão de internet, câmera, microfone ou armazenamento; não inclui analytics nem telemetria e não tenta contornar FLAG_SECURE.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item { SettingsCard(settings) { updated -> settings = updated; settingsStore.save(updated) } }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Permissões e serviços", fontWeight = FontWeight.Bold)
                            StatusRow("Accessibility", accessibilityEnabled)
                            StatusRow("Sobrepor outros apps (OCR)", overlayPermission)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = ::openAccessibilitySettings) { Text("Accessibility") }
                                Button(onClick = ::openOverlaySettings) { Text("Overlay") }
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Processamento local", fontWeight = FontWeight.Bold)
                            Text("Os modelos OCR EN/JA/ZH/KO são embutidos no APK e copiados somente para o armazenamento privado do próprio app.")
                            Text(translationModelsMessage, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = ::openSystemTranslationSettings) { Text("Configurações de tradução do Android") }
                            Text(
                                "Se faltar um modelo de tradução, o download é gerenciado pelo serviço do sistema. O Translate de Cria continua sem permissão de rede.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::activateTranslation, modifier = Modifier.fillMaxWidth()) { Text("Ativar tradução") }
                        TextButton(onClick = ::stopTranslation, modifier = Modifier.fillMaxWidth()) { Text("Encerrar tradução e limpar cache em memória") }
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
                text = { Text("O Android pedirá autorização para uma sessão de MediaProjection. Cada frame é copiado temporariamente para RAM, lido pelo Tesseract local e descartado. Nenhuma imagem, vídeo ou texto reconhecido é salvo ou enviado. Ao encerrar, todos os recursos, bitmaps, resultados nativos do OCR, cache e overlays são liberados.") },
                confirmButton = { Button(onClick = { showOcrPrivacyDialog = false; requestProjection() }) { Text("Continuar") } },
                dismissButton = { TextButton(onClick = { showOcrPrivacyDialog = false }) { Text("Cancelar") } }
            )
        }
    }

    @Composable
    private fun SettingsCard(current: SettingsSnapshot, onChange: (SettingsSnapshot) -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Configurações", fontWeight = FontWeight.Bold)
                DropdownSetting("Idioma de origem", current.sourceLanguage, SourceLanguage.entries) { onChange(current.copy(sourceLanguage = it)) }
                DropdownSetting("Idioma de destino", current.targetLanguage, TargetLanguage.entries) { onChange(current.copy(targetLanguage = it)) }
                DropdownSetting("Modo de captura", current.captureMode, CaptureMode.entries) { onChange(current.copy(captureMode = it)) }
                Text("Análises por segundo: ${current.analysesPerSecond}")
                Slider(current.analysesPerSecond.toFloat(), { onChange(current.copy(analysesPerSecond = it.toInt().coerceIn(1, 5))) }, valueRange = 1f..5f, steps = 3)
                Text("Tamanho máximo da fonte: ${current.fontSizeSp.toInt()} sp")
                Slider(current.fontSizeSp, { onChange(current.copy(fontSizeSp = it)) }, valueRange = 12f..28f, steps = 15)
                Text("Transparência: ${(current.overlayOpacity * 100).toInt()}%")
                Slider(current.overlayOpacity, { onChange(current.copy(overlayOpacity = it)) }, valueRange = 0.35f..1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Mostrar texto original")
                        Text("Exibe original + tradução", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(current.showOriginal, { onChange(current.copy(showOriginal = it)) })
                }
                if (current.sourceLanguage == SourceLanguage.AUTO && current.captureMode == CaptureMode.OCR) {
                    Text("Automático carrega EN + JA + ZH + KO no mesmo OCR. Fixar o idioma normalmente reduz RAM e CPU.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun <T> DropdownSetting(label: String, selected: T, options: List<T>, onSelected: (T) -> Unit) where T : Enum<T> {
        var expanded by remember { mutableStateOf(false) }
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Button(onClick = { expanded = true }) { Text(enumLabel(selected)) }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(enumLabel(option)) }, onClick = { expanded = false; onSelected(option) })
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
                statusMessage = if (TranslatorAccessibilityService.requestStart()) "Tradução por Accessibility ativa. Sem captura de tela." else "Serviço habilitado; aguardando o Android conectá-lo."
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

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openOverlaySettings() = startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))

    private fun openSystemTranslationSettings() {
        val pendingIntent = translationSupport.settingsIntent()
        if (pendingIntent == null) {
            statusMessage = "O fabricante não disponibilizou uma tela de configurações do serviço de tradução."
            return
        }
        runCatching { pendingIntent.send() }.onFailure { statusMessage = "Não foi possível abrir as configurações de tradução do sistema." }
    }

    private fun refreshTranslationSupport() {
        lifecycleScope.launch {
            translationModelsMessage = runCatching { translationSupport.query().userMessage }
                .getOrElse { "Não foi possível consultar o serviço de tradução on-device deste aparelho." }
        }
    }

    private fun refreshPermissionState() {
        overlayPermission = Settings.canDrawOverlays(this)
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val target = ComponentName(this, TranslatorAccessibilityService::class.java)
        accessibilityEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            ComponentName(serviceInfo.packageName, serviceInfo.name) == target
        }
    }
}
