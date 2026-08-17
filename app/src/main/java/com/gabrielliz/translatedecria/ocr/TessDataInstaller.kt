package com.gabrielliz.translatedecria.ocr

import android.content.Context
import java.io.File

object TessDataInstaller {
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

        requiredFiles.forEach { fileName ->
            val destination = File(tessDataDir, fileName)
            val assetPath = "tessdata/$fileName"
            val expectedSize = context.assets.openFd(assetPath).use { it.length }
            if (!destination.exists() || destination.length() != expectedSize) {
                val temporary = File(tessDataDir, "$fileName.tmp")
                context.assets.open(assetPath).use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.length() == expectedSize) { "Incomplete OCR model copy: $fileName" }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Could not install OCR model: $fileName" }
            }
        }

        return dataRoot
    }
}
