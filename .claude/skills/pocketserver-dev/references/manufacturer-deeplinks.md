# Manufacturer-Specific Deep Links for Battery Optimization Bypass

## Table of Contents
1. [Overview](#overview)
2. [Manufacturer Detection](#manufacturer-detection)
3. [Samsung (5/5)](#samsung)
4. [Xiaomi / MIUI (5/5)](#xiaomi--miui)
5. [Huawei / EMUI (5/5)](#huawei--emui)
6. [OPPO / ColorOS (3/5)](#oppo--coloros)
7. [Realme (3/5)](#realme)
8. [OnePlus (2/5)](#oneplus)
9. [Vivo (4/5)](#vivo)
10. [Google Pixel / Stock Android](#google-pixel--stock-android)
11. [Implementation Pattern](#implementation-pattern)
12. [dontkillmyapp.com API Integration](#dontkillmyappcom-api-integration)

---

## Overview

Android OEMs add proprietary battery management on top of stock Android that kills background
apps even when they hold a Foreground Service and Wake Lock. PocketServer runs a Linux server
24/7 and must survive these manufacturer kill policies.

- **Primary reference**: [dontkillmyapp.com](https://dontkillmyapp.com)
- **API**: `https://dontkillmyapp.com/api/v2/{manufacturer}.json`
- **AutoStarter library**: [github.com/judemanutd/AutoStarter](https://github.com/judemanutd/AutoStarter) -- wraps many deep links with fallback logic

## Manufacturer Detection

```kotlin
val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)

when {
    manufacturer.contains("samsung")  -> handleSamsung()
    manufacturer.contains("xiaomi") ||
    manufacturer.contains("redmi") ||
    manufacturer.contains("poco")     -> handleXiaomi()
    manufacturer.contains("huawei") ||
    manufacturer.contains("honor")    -> handleHuawei()
    manufacturer.contains("oppo")     -> handleOppo()
    manufacturer.contains("realme")   -> handleRealme()
    manufacturer.contains("oneplus")  -> handleOnePlus()
    manufacturer.contains("vivo")     -> handleVivo()
    manufacturer.contains("meizu")    -> handleMeizu()
    manufacturer.contains("asus")     -> handleAsus()
    manufacturer.contains("nokia")    -> handleNokia()
    manufacturer.contains("sony")     -> handleSony()
    manufacturer.contains("google")   -> handlePixel()
    else                              -> handleGenericDoze()
}
```

## Samsung

**Severity: 5/5** -- Adaptive Battery, Sleeping Apps, Deep Sleeping Apps. Re-adds apps to
sleeping lists after firmware updates.

```kotlin
// "Never sleeping apps" list
Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY").apply {
    putExtra("activity_type", 2)
}

// Battery unrestricted (fallback)
Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:${context.packageName}")
}
```

**User instructions**: Settings > Apps > [Your App] > Battery > Unrestricted.

## Xiaomi / MIUI

**Severity: 5/5** -- AutoStart denied by default. Aggressive battery saver kills services
regardless of foreground notification.

```kotlin
// AutoStart permission
Intent().apply {
    component = ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )
}

// Battery saver whitelist
Intent().apply {
    component = ComponentName(
        "com.miui.powerkeeper",
        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
    )
    putExtra("package_name", context.packageName)
    putExtra("package_label", context.getString(R.string.app_name))
}
```

**Checking autostart state**: [MIUI-autostart](https://github.com/XomaDev/MIUI-Autostart)
library can programmatically check if autostart is enabled (uses hidden APIs).

**User instructions**: Settings > Apps > Manage Apps > [Your App] > Autostart > Enable.

## Huawei / EMUI

**Severity: 5/5** -- PowerGenie kills apps after ~60 minutes of background execution with no
user-accessible workaround in the UI.

```kotlin
// Startup manager (protected apps)
Intent().apply {
    component = ComponentName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
    )
}

// Alternative: older EMUI versions
Intent().apply {
    component = ComponentName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.optimize.process.ProtectActivity"
    )
}
```

**ADB workaround** (optional advanced step -- communicate carefully to users):
```
adb shell pm uninstall -k --user 0 com.huawei.powergenie
```
This disables PowerGenie entirely. Survives reboots but is reversed by factory reset.

## OPPO / ColorOS

**Severity: 3/5** -- Background services killed on screen off unless whitelisted.

```kotlin
Intent().apply {
    component = ComponentName(
        "com.coloros.safecenter",
        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
    )
}
```

**User instructions**: Settings > App Management > Auto Start-up > Enable.

## Realme

**Severity: 3/5** -- ColorOS-based, similar to OPPO with slightly different activity paths.

```kotlin
Intent().apply {
    component = ComponentName(
        "com.coloros.safecenter",
        "com.coloros.safecenter.startupapp.StartupAppListActivity"
    )
}
```

## OnePlus

**Severity: 2/5** -- Better than other Chinese OEMs. Still has Auto Launch management that
can block background execution.

```kotlin
Intent().apply {
    component = ComponentName(
        "com.oneplus.security",
        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
    )
}
```

## Vivo

**Severity: 4/5** -- Aggressive background task manager that terminates services proactively.

```kotlin
Intent().apply {
    component = ComponentName(
        "com.vivo.permissionmanager",
        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
    )
}
```

## Google Pixel / Stock Android

No aggressive killing beyond standard Doze mode. The Layer 4 battery optimization exemption
(`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is sufficient for PocketServer.

## Implementation Pattern

```kotlin
data class OptimizationStep(
    val title: String,
    val description: String,
    val intents: List<Intent>,  // tried in order; first successful one wins
    val userInstructions: String
)

object ManufacturerOptimizationHelper {

    fun getRequiredSteps(context: Context): List<OptimizationStep> {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return when {
            manufacturer.contains("samsung") -> samsungSteps(context)
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")   -> xiaomiSteps(context)
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor")  -> huaweiSteps(context)
            // ... other manufacturers
            else -> listOf(genericDozeStep(context))
        }
    }

    fun launchIntent(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) { /* intent unavailable on this OS version */ }
        }
        // Final fallback: generic battery optimization settings
        return try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) { false }
    }
}
```

Key points:
- Every deep link must be wrapped in `try/catch`; activity paths change between OS versions.
- Always provide a fallback chain ending at `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
- Use `resolveActivity()` before `startActivity()` to avoid `ActivityNotFoundException`.

## dontkillmyapp.com API Integration

Fetch live, up-to-date user instructions per manufacturer at runtime.

```kotlin
// Endpoint pattern
val url = "https://dontkillmyapp.com/api/v2/${manufacturer}.json"

// Response contains:
// - "name": display name
// - "award": severity rating (1-5)
// - "position": ranking
// - "explanation": HTML instructions for the user
// - "user_solution": step-by-step fix in HTML
// - "developer_solution": what the developer should do
```

Use this to display a guided walkthrough when the app detects it is being killed. Cache the
response; the data changes infrequently.

---

*Primary source: [dontkillmyapp.com](https://dontkillmyapp.com). Deep link paths verified
against AutoStarter library and manufacturer firmware as of 2025.*
