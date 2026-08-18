package app.cycluna.android.features.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri

internal const val SITE_URL = "https://quietflare.net/cycluna"

/** The Play Store app's own package name — the installer recorded for a Play install. */
private const val PLAY_STORE_PACKAGE = "com.android.vending"

/**
 * What a share hands to the other app. Deliberately points at the QuietFlare page rather
 * than a store listing: the same build ships through Play and F-Droid, and a store-specific
 * link would send half the users to a store they don't use.
 */
private fun shareText(): String =
    "Cycluna — a private cycle and mood tracker that keeps everything on your phone. $SITE_URL"

/**
 * Which app installed us, or null when that is unknown (older sideloads, adb, a wiped
 * install record). Only used to decide whether the Play Store is reachable, so "unknown"
 * must fall on the same side as "not Play".
 */
private fun installerPackage(context: Context): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)
    }
}.getOrNull()

/**
 * Whether this copy of Cycluna came from Play.
 *
 * This one boolean is the whole reason the F-Droid build stays clean: it gates the "Rate
 * Cycluna" row, so a build installed from anywhere else contains no route to the Play
 * Store at all. Anything other than Play — F-Droid, adb, unknown — must return false.
 */
internal fun isPlayInstall(installer: String?): Boolean = installer == PLAY_STORE_PACKAGE

/** Whether to offer the Play rating row on this install. */
internal fun canRateOnPlay(context: Context): Boolean = isPlayInstall(installerPackage(context))

/**
 * Hands a link to the system share sheet. Same mechanism as the data export: nothing leaves
 * the phone until the user picks a destination, and this one carries no user data at all.
 */
internal fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText())
    }
    context.startActivity(Intent.createChooser(send, "Share Cycluna"))
}

/**
 * Opens the Play listing so the user can leave a rating.
 *
 * `market://` hands off to the installed Play Store app; the web URL is the fallback for
 * the rare Play install whose Store app has since been disabled. Deliberately NOT the Play
 * In-App Review API — that library would put the Play dependency metadata back into the
 * APK, which is exactly what build 7 stripped out for F-Droid's scanner.
 *
 * The failure path is a try/catch rather than `resolveActivity`, which since Android 11
 * would need a `<queries>` entry in the manifest to see the other app at all.
 */
internal fun rateApp(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${context.packageName}".toUri(),
        )
        runCatching { context.startActivity(web) }
    }
}
