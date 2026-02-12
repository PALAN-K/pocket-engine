package kr.co.palank.pocketmonitor.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.co.palank.pocketmonitor.ui.theme.PocketMonitorExtendedTheme
import kr.co.palank.pocketmonitor.util.SettingsDataStore
import kr.co.palank.pocketmonitor.util.SettingsKeys

@Composable
fun SettingsScreen(
    engineConnected: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }

    val dailyReportEnabled by settingsDataStore.getBoolean(SettingsKeys.DAILY_REPORT)
        .collectAsState(initial = true)
    val tempAlertEnabled by settingsDataStore.getBoolean(SettingsKeys.TEMP_ALERT)
        .collectAsState(initial = true)
    val weeklyReportEnabled by settingsDataStore.getBoolean(SettingsKeys.WEEKLY_REPORT)
        .collectAsState(initial = true)

    var showLicenseDialog by remember { mutableStateOf(false) }

    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "뒤로",
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "설정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Notification settings section
        SectionTitle("알림 설정")
        Spacer(modifier = Modifier.height(8.dp))

        SettingToggle(
            title = "일일 리포트",
            checked = dailyReportEnabled,
            onCheckedChange = {
                scope.launch { settingsDataStore.setBoolean(SettingsKeys.DAILY_REPORT, it) }
            },
        )
        SettingToggle(
            title = "온도 경고 알림",
            checked = tempAlertEnabled,
            onCheckedChange = {
                scope.launch { settingsDataStore.setBoolean(SettingsKeys.TEMP_ALERT, it) }
            },
        )
        SettingToggle(
            title = "주간 요약",
            checked = weeklyReportEnabled,
            onCheckedChange = {
                scope.launch { settingsDataStore.setBoolean(SettingsKeys.WEEKLY_REPORT, it) }
            },
        )

        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // Extension features section
        SectionTitle("확장 기능")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "확장 도구",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (engineConnected) "연결됨" else "사용 가능",
                fontSize = 15.sp,
                color = if (engineConnected) {
                    PocketMonitorExtendedTheme.colors.statusGreen
                } else {
                    PocketMonitorExtendedTheme.colors.textSecondary
                },
                fontWeight = FontWeight.Medium,
            )
        }

        if (!engineConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = "PocketEngine 확장 도구",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "디바이스를 더 다양하게 활용할 수 있는\n확장 도구를 확인해보세요",
                    fontSize = 13.sp,
                    color = PocketMonitorExtendedTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "자세히 보기",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ENGINE_DOWNLOAD_URL))
                        context.startActivity(intent)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // Support section
        SectionTitle("지원")
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow(
            label = "커뮤니티 (Telegram)",
            value = "",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
                context.startActivity(intent)
            },
        )
        InfoRow(
            label = "이메일 문의",
            value = "",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "[PocketMonitor] 문의")
                }
                context.startActivity(intent)
            },
        )

        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // App info section
        SectionTitle("앱 정보")
        Spacer(modifier = Modifier.height(8.dp))

        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }
        InfoRow(label = "버전", value = versionName)
        InfoRow(
            label = "오픈소스 라이선스",
            value = "",
            onClick = { showLicenseDialog = true },
        )
        InfoRow(
            label = "개인정보 처리방침",
            value = "",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                context.startActivity(intent)
            },
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "오픈소스 라이선스",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                LicenseItem("Jetpack Compose", "Apache License 2.0")
                LicenseItem("Material Design 3", "Apache License 2.0")
                LicenseItem("Google AdMob SDK", "Google APIs Terms of Service")
                LicenseItem("AndroidX WorkManager", "Apache License 2.0")
                LicenseItem("AndroidX DataStore", "Apache License 2.0")
                LicenseItem("AndroidX Lifecycle", "Apache License 2.0")
                LicenseItem("Kotlin Coroutines", "Apache License 2.0")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun LicenseItem(name: String, license: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = license,
            fontSize = 12.sp,
            color = PocketMonitorExtendedTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = PocketMonitorExtendedTheme.colors.textSecondary,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                fontSize = 15.sp,
                color = PocketMonitorExtendedTheme.colors.textSecondary,
            )
        }
    }
}

private const val ENGINE_DOWNLOAD_URL = "https://pocket-server-palank.web.app"
private const val PRIVACY_POLICY_URL = "https://pocket-server-palank.web.app/privacy-ko.html"
private const val TELEGRAM_URL = "https://t.me/pocketseever"
private const val SUPPORT_EMAIL = "naegeon@gmail.com"
