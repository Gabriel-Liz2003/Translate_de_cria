package com.gabrielliz.translatedecria

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.ocr.ScreenOcrService

class SafeMainActivity : ComponentActivity() {
    private lateinit var settingsStore: AppSettingsStore
    private var settings by mutableStateOf(SettingsSnapshot(captureMode = CaptureMode.OCR))
    private var overlayPermission by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Pronto")
    private var showPrivacyDialog by mutableStateOf(false)

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            settings = settings.copy(captureMode = CaptureMode.OCR, translationEnabled = true)
            settingsStore.save(settings)
            val serviceIntent = Intent(this, ScreenOcrService::class.java).apply {
                action = ScreenOcrService.ACTION_START
                putExtra(ScreenOcrService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenOcrService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            statusMessage = "OCR local ativo. Nenhum frame é salvo."
        } else {
            settingsStore.setTranslationEnabled(false)
            statusMessage = "Captura não autorizada."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = AppSettingsStore(this)
        settings = settingsStore.load().copy(captureMode = CaptureMode.OCR)
        settingsStore.save(settings)
        overlayPermission = Settings.canDrawOverlays(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermission = Settings.canDrawOverlays(this)
        settings = settingsStore.load().copy(captureMode = CaptureMode.OCR)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        Scaffold(topBar = { TopAppBar(title = { Text("Translate de Cria Safe") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Versão de compatibilidade", fontWeight = FontWeight.Bold)
                            Text("Esta edição NÃO instala AccessibilityService. Ela usa somente OCR por MediaProjection durante uma sessão autorizada pelo Android.")
                            Text("Continua sem permissão de internet, câmera, microfone ou armazenamento. FLAG_SECURE é respeitado.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("OCR", fontWeight = FontWeight.Bold)
                            SourceDropdown(settings.sourceLanguage) { selected ->
                                settings = settings.copy(sourceLanguage = selected, captureMode = CaptureMode.OCR)
                                settingsStore.save(settings)
                            }
                            Text("Análises por segundo: ${settings.analysesPerSecond}")
                            Slider(
                                value = settings.analysesPerSecond.toFloat(),
                                onValueChange = {
                                    settings = settings.copy(analysesPerSecond = it.toInt().coerceIn(1, 5), captureMode = CaptureMode.OCR)
                                    settingsStore.save(settings)
                                },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                            Text(if (overlayPermission) "Overlay: autorizado" else "Overlay: precisa de autorização")
                            Button(onClick = ::openOverlaySettings) { Text("Permitir sobreposição") }
                        }
                    }
                }
                item {
                    Button(onClick = ::activateOcr, modifier = Modifier.fillMaxWidth()) { Text("Ativar tradução OCR") }
                    TextButton(onClick = ::stopOcr, modifier = Modifier.fillMaxWidth()) { Text("Encerrar tradução") }
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyDialog = false },
                title = { Text("Captura temporária da tela") },
                text = { Text("O Android pedirá autorização para MediaProjection. Frames existem apenas em RAM durante OCR local e são descartados. Nenhum screenshot, vídeo ou texto reconhecido é salvo ou enviado.") },
                confirmButton = { Button(onClick = { showPrivacyDialog = false; requestProjection() }) { Text("Continuar") } },
                dismissButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Cancelar") } }
            )
        }
    }

    @Composable
    private fun SourceDropdown(selected: SourceLanguage, onSelected: (SourceLanguage) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Column {
            Text("Idioma de origem", style = MaterialTheme.typography.labelLarge)
            Button(onClick = { expanded = true }) { Text(selected.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SourceLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = { expanded = false; onSelected(language) }
                    )
                }
            }
        }
    }

    private fun activateOcr() {
        if (!overlayPermission) {
            statusMessage = "Autorize a sobreposição para mostrar traduções sobre outros apps."
            openOverlaySettings()
            return
        }
        showPrivacyDialog = true
    }

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopOcr() {
        settings = settings.copy(captureMode = CaptureMode.OCR, translationEnabled = false)
        settingsStore.save(settings)
        stopService(Intent(this, ScreenOcrService::class.java))
        statusMessage = "Sessão encerrada e recursos liberados."
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
}
