package kr.co.palank.pocketserver.manufacturer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

data class OptimizationStep(
    val title: String,
    val description: String,
    val intents: List<Intent>,
    val userInstructions: String
)

object ManufacturerOptimizationHelper {

    fun getRequiredSteps(context: Context): List<OptimizationStep> {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return when {
            manufacturer.contains("samsung") -> samsungSteps(context)
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> xiaomiSteps(context)
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor") -> huaweiSteps(context)
            manufacturer.contains("oppo") -> oppoSteps(context)
            manufacturer.contains("realme") -> realmeSteps(context)
            manufacturer.contains("oneplus") -> oneplusSteps(context)
            manufacturer.contains("vivo") -> vivoSteps(context)
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
        return try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) { false }
    }

    // --- Samsung ---

    private fun samsungSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "배터리 사용량 제한 없음",
            description = "앱의 배터리 사용이 제한되지 않도록 설정합니다.",
            intents = listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            ),
            userInstructions = "설정 > 앱 > PocketServer > 배터리 > '제한 없음' 선택"
        ),
        OptimizationStep(
            title = "절대 절전 안 함 목록에 추가",
            description = "백그라운드에서 앱이 종료되지 않도록 '절대 절전 안 함' 목록에 추가합니다.",
            intents = listOf(
                Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY").apply {
                    putExtra("activity_type", 2)
                }
            ),
            userInstructions = "설정 > 배터리 > 백그라운드 사용 제한 > 절대 절전 안 함 > PocketServer 추가"
        )
    )

    // --- Xiaomi / MIUI ---

    private fun xiaomiSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "자동 시작(AutoStart) 활성화",
            description = "앱이 자동으로 시작될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            ),
            userInstructions = "설정 > 앱 > 앱 관리 > PocketServer > 자동 시작 > 활성화"
        ),
        OptimizationStep(
            title = "배터리 절약 제외",
            description = "배터리 절약 모드에서 앱이 제한되지 않도록 설정합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", "PocketServer")
                }
            ),
            userInstructions = "설정 > 배터리 > 앱 배터리 절약 > PocketServer > '제한 없음' 선택"
        )
    )

    // --- Huawei / EMUI ---

    private fun huaweiSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "시작 관리자에서 허용",
            description = "앱이 백그라운드에서 자동으로 실행될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
            ),
            userInstructions = "설정 > 배터리 > 앱 시작 관리 > PocketServer > 수동 관리 > 모두 활성화"
        )
    )

    // --- OPPO / ColorOS ---

    private fun oppoSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "자동 시작 허용",
            description = "앱이 백그라운드에서 자동 시작될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            ),
            userInstructions = "설정 > 앱 관리 > 자동 시작 > PocketServer 활성화"
        )
    )

    // --- Realme ---

    private fun realmeSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "자동 시작 허용",
            description = "앱이 백그라운드에서 자동 시작될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                }
            ),
            userInstructions = "설정 > 앱 관리 > 자동 시작 > PocketServer 활성화"
        )
    )

    // --- OnePlus ---

    private fun oneplusSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "자동 실행 허용",
            description = "앱이 자동으로 실행될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
            ),
            userInstructions = "설정 > 앱 > 앱 관리 > PocketServer > 자동 실행 허용"
        )
    )

    // --- Vivo ---

    private fun vivoSteps(context: Context): List<OptimizationStep> = listOf(
        OptimizationStep(
            title = "백그라운드 자동 시작 허용",
            description = "앱이 백그라운드에서 자동으로 시작될 수 있도록 허용합니다.",
            intents = listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            ),
            userInstructions = "설정 > 앱 및 권한 > 자동 시작 관리 > PocketServer 활성화"
        )
    )

    // --- Generic (Google Pixel, stock Android, etc.) ---

    private fun genericDozeStep(context: Context): OptimizationStep = OptimizationStep(
        title = "배터리 최적화 해제",
        description = "배터리 최적화에서 앱을 제외하여 백그라운드 실행을 보장합니다.",
        intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        ),
        userInstructions = "설정 > 배터리 > 배터리 최적화 > 모든 앱 > PocketServer > '최적화하지 않음' 선택"
    )
}
