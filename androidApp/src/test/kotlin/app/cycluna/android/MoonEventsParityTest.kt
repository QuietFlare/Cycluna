package app.cycluna.android

import app.cycluna.core.MoonEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The moon-event catalogue ships twice — once in the iOS bundle, once in Android assets —
 * because neither platform can read the other's resources. Two copies of a hand-maintained
 * data file drift, and the drift is invisible: each app looks right on its own. Pin them.
 */
class MoonEventsParityTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    @Test
    fun androidAssetMatchesTheIosBundleByteForByte() {
        val root = repoRoot()
        val android = File(root, "androidApp/src/main/assets/moon-events.json")
        val ios = File(root, "iosApp/iosApp/Resources/moon-events.json")
        assertTrue("missing Android asset at ${android.absolutePath}", android.exists())
        assertTrue("missing iOS resource at ${ios.absolutePath}", ios.exists())
        assertEquals(
            "moon-events.json has drifted between the platforms — copy one over the other",
            ios.readText(),
            android.readText(),
        )
    }

    @Test
    fun theSharedCoreCanParseTheAsset() {
        val text = File(repoRoot(), "androidApp/src/main/assets/moon-events.json").readText()
        assertTrue("MoonEvents.parse returned nothing", MoonEvents.parse(text).isNotEmpty())
    }
}
