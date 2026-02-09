package kr.co.palank.pocketmonitor.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketmonitor.ui.theme.PocketMonitorExtendedTheme

@Composable
fun SettingsScreen(
    engineConnected: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var dailyReportEnabled by remember { mutableStateOf(true) }
    var tempAlertEnabled by remember { mutableStateOf(true) }
    var weeklyReportEnabled by remember { mutableStateOf(true) }

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
            onCheckedChange = { dailyReportEnabled = it },
        )
        SettingToggle(
            title = "온도 경고 알림",
            checked = tempAlertEnabled,
            onCheckedChange = { tempAlertEnabled = it },
        )
        SettingToggle(
            title = "주간 요약",
            checked = weeklyReportEnabled,
            onCheckedChange = { weeklyReportEnabled = it },
        )

        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // Server connection section
        SectionTitle("서버 연동")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "연결 상태",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (engineConnected) "연결됨" else "미연결",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ENGINE_DOWNLOAD_URL))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    text = "서버 엔진 설치 안내",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // App info section
        SectionTitle("앱 정보")
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow(label = "버전", value = "1.0")
        InfoRow(
            label = "오픈소스 라이선스",
            value = "",
            onClick = {
                Toast.makeText(context, "오픈소스 라이선스", Toast.LENGTH_SHORT).show()
            },
        )

        Spacer(modifier = Modifier.height(40.dp))
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
