package app.cycluna.android

import app.cycluna.android.features.settings.isPlayInstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isPlayInstall` gates the "Rate Cycluna" row, which is the only Play Store link in the
 * app. If it ever answered true off Play, the F-Droid build would start pointing users at
 * a proprietary store — invisible on screen unless you happen to run that build.
 */
class AppLinksTest {

    @Test
    fun `play store install is recognised`() {
        assertTrue(isPlayInstall("com.android.vending"))
    }

    @Test
    fun `f-droid install is not a play install`() {
        assertFalse(isPlayInstall("org.fdroid.fdroid"))
        assertFalse(isPlayInstall("org.fdroid.basic"))
    }

    @Test
    fun `adb and package-installer sideloads are not play installs`() {
        assertFalse(isPlayInstall("com.android.shell"))
        assertFalse(isPlayInstall("com.google.android.packageinstaller"))
        assertFalse(isPlayInstall("com.android.packageinstaller"))
    }

    @Test
    fun `unknown installer is not a play install`() {
        // Older sideloads and a wiped install record both report null. Unknown has to fall
        // on the same side as "not Play" — hiding the row is the safe direction.
        assertFalse(isPlayInstall(null))
        assertFalse(isPlayInstall(""))
    }

    @Test
    fun `lookalike installer packages are not play installs`() {
        assertFalse(isPlayInstall("com.android.vending.fake"))
        assertFalse(isPlayInstall("org.evil.com.android.vending"))
        assertFalse(isPlayInstall("COM.ANDROID.VENDING"))
    }
}
