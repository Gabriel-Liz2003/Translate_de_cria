package com.gabrielliz.translatedecria.ocr

import android.content.Context
import java.io.File

object TessDataInstaller {
    private const val DATA_VERSION = "tessdata-fast-87416418657359cb625c412a48b6e1d6d41c29bd"
    private const val VERSION_FILE = ".version"

    private val requiredFiles = listOf(
        "eng.traineddata",
        "jpn.traineddata",
        "chi_sim.traineddata",
        "kor.traineddata"
    )

    fun ensureInstalled(context: Context): File {
        val dataRoot = File(context.filesDir, "tesseract")
        val tessDataDir = File(dataRoot, "tessdata")
        check(tessDataDir.exists() || tessDataDir.mkdirs()) { "Could not create OCR model directory" }

        val versionMarker = File(tessDataDir, VERSION_FILE)
        val alreadyCurrent = versionMarker.takeIf(File::exists)?.readText() == DATA_VERSION &&
            requiredFiles.all { File(tessDataDir, it).isFile }

        if (!alreadyCurrent) {
            requiredFiles.forEach { fileName ->
                copyAssetAtomically(context, "tessdata/$fileName", File(tessDataDir, fileName))
            }
            versionMarker.writeText(DATA_VERSION)
        }

        return dataRoot
    }

    private fun copyAssetAtomically(context: Context, assetPath: String, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        if (temporary.exists()) temporary.delete()

        context.assets.open(assetPath).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.length() > 0L) { "Empty OCR model asset: $assetPath" }
        if (destination.exists()) check(destination.delete()) { "Could not replace OCR model" }
        check(temporary.renameTo(destination)) { "Could not install OCR model" }
    }
}
