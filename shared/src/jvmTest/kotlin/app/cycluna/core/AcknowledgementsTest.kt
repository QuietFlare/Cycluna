package app.cycluna.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every third-party library linked into the app must appear on the Acknowledgements screen.
 *
 * Apache 2.0 §4 requires the attribution notices to travel with the distributed binary, and
 * the obligation is easy to break silently: add a dependency, ship it, forget the screen.
 * This is a JVM-only test because it reads the build file and the Swift source off disk.
 */
class AcknowledgementsTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }

    @Test
    fun everyShippedDependencyIsAcknowledged() {
        val root = repoRoot()
        val buildFile = File(root, "shared/build.gradle.kts")
        val screen = File(root, "iosApp/iosApp/Features/Settings/AcknowledgementsView.swift")
        if (!buildFile.exists() || !screen.exists()) {
            fail("could not locate sources from ${root.absolutePath}")
        }

        // Runtime deps are declared as full coordinates; kotlin("test") is test-only and
        // never ships, so it is correctly absent from this list.
        val coordinates = Regex("""implementation\("([^"]+:[^"]+):[^"]+"\)""")
            .findAll(buildFile.readText())
            .map { it.groupValues[1] }
            .toList()

        assertTrue(coordinates.isNotEmpty(), "no dependencies parsed — has the build file moved?")

        val acknowledged = screen.readText()
        val missing = coordinates.filterNot { acknowledged.contains(it) }
        assertTrue(
            missing.isEmpty(),
            "shipped but not acknowledged: $missing — add them to AcknowledgementsView.libraries"
        )
    }
}
