package app.cycluna.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every third-party library linked into the Android app must appear on the Acknowledgements
 * screen.
 *
 * Apache 2.0 §4 requires the attribution notices to travel with the distributed binary, and
 * the obligation is easy to break silently: add a dependency, ship it, forget the screen.
 * The shared module has the same guard pointed at the iOS screen.
 */
class AndroidAcknowledgementsTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    @Test
    fun everyShippedDependencyIsAcknowledged() {
        val root = repoRoot()
        val buildFile = File(root, "androidApp/build.gradle.kts")
        val screen = File(
            root,
            "androidApp/src/main/kotlin/app/cycluna/android/features/settings/AboutScreen.kt",
        )
        assertTrue("missing ${buildFile.absolutePath}", buildFile.exists())
        assertTrue("missing ${screen.absolutePath}", screen.exists())

        // Coordinates may or may not carry a version — the Compose artifacts take theirs from
        // the BOM. `project(...)` deps and test-only dependencies never ship, so both are out.
        val coordinates = Regex("""implementation\("([a-z0-9.\-]+:[a-z0-9.\-]+)(?::[^"]+)?"\)""")
            .findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("androidx.compose:compose-bom") }
            .toList()

        assertTrue("no dependencies parsed — has the build file moved?", coordinates.isNotEmpty())

        val acknowledged = screen.readText()
        // Group artifacts are acknowledged by family (androidx.compose covers every
        // compose artifact), so a prefix match is the honest test.
        val missing = coordinates.filterNot { coordinate ->
            acknowledged.contains(coordinate) ||
                acknowledged.contains(coordinate.substringBefore(':'))
        }
        assertTrue(
            "shipped but not acknowledged: $missing — add them to AboutScreen's LIBRARIES",
            missing.isEmpty(),
        )
    }
}
