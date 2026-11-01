package com.saavndl.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min

enum class DeviceMode { PHONE, TABLET, CAR }

val LocalDeviceMode = staticCompositionLocalOf { DeviceMode.PHONE }

/**
 * Looks at the hardware once: car head unit (automotive / car UI mode, or a very wide landscape panel without a phone modem),
 * tablet (smallest width >= 600dp) or phone.
 */
fun detectDeviceMode(ctx: Context): DeviceMode {
    val pm = ctx.packageManager
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) || ui?.currentModeType == Configuration.UI_MODE_TYPE_CAR) return DeviceMode.CAR
    val res = ctx.resources
    val dm = res.displayMetrics
    val longer = max(dm.widthPixels, dm.heightPixels).toFloat()
    val shorter = min(dm.widthPixels, dm.heightPixels).toFloat()
    val ratio = if (shorter > 0f) longer / shorter else 1f
    val noModem = !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    return when {
        noModem && ratio >= 1.9f && longer / dm.density >= 600f -> DeviceMode.CAR   // typical aftermarket car LCD panel
        res.configuration.smallestScreenWidthDp >= 600 -> DeviceMode.TABLET
        else -> DeviceMode.PHONE
    }
}

/** "auto" follows the detected hardware; a very wide, tall-enough window (e.g. a phone unfolded) also gets the tablet layout. */
@Composable
fun resolveDeviceMode(pref: String): DeviceMode {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    return when (pref) {
        "phone" -> DeviceMode.PHONE
        "tablet" -> DeviceMode.TABLET
        "car" -> DeviceMode.CAR
        else -> {
            val base = remember { detectDeviceMode(ctx) }
            if (base == DeviceMode.PHONE && cfg.screenWidthDp >= 840 && cfg.screenHeightDp >= 480) DeviceMode.TABLET else base
        }
    }
}

/** Interface size: 0 = automatic (cars get bigger touch targets), 1..4 = small .. extra large. */
fun uiScaleFor(pref: Int, mode: DeviceMode): Float = when (pref) {
    1 -> 0.9f
    2 -> 1.0f
    3 -> 1.15f
    4 -> 1.3f
    else -> if (mode == DeviceMode.CAR) 1.2f else 1.0f
}
