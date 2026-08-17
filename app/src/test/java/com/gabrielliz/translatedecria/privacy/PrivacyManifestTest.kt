package com.gabrielliz.translatedecria.privacy

import com.gabrielliz.translatedecria.PrivacyContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyManifestTest {
    @Test
    fun manifestNeverRequestsCameraMicrophoneOrStoragePermissions() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull(File::exists)

        assertTrue("AndroidManifest.xml not found from test working directory", manifest != null)
        val contents = manifest!!.readText()
        PrivacyContract.forbiddenRuntimePermissions.forEach { forbidden ->
            assertFalse("Forbidden permission present: $forbidden", contents.contains(forbidden))
        }
    }

    @Test
    fun privacyContractDisablesPersistenceTransmissionAndTelemetry() {
        assertFalse(PrivacyContract.SCREEN_CONTENT_PERSISTED)
        assertFalse(PrivacyContract.SCREEN_CONTENT_TRANSMITTED)
        assertFalse(PrivacyContract.ANALYTICS_ENABLED)
        assertFalse(PrivacyContract.TELEMETRY_ENABLED)
    }
}
