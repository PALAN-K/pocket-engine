package kr.co.palank.pocketmonitor.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.palank.pocketmonitor.monitor.DeviceStatus
import kr.co.palank.pocketmonitor.monitor.HistoryEntry
import kr.co.palank.pocketmonitor.ipc.EngineStatus
import kr.co.palank.pocketmonitor.ui.theme.PocketMonitorExtendedTheme

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        DashboardHeader(onSettingsClick = onNavigateToSettings)

        Spacer(modifier = Modifier.height(20.dp))

        // Server status card (only when Engine connected)
        val engine = uiState.engine
        if (engine != null) {
            ServerStatusCard(engine = engine)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Device metrics grid
        DeviceMetricsGrid(device = uiState.device)

        Spacer(modifier = Modifier.height(16.dp))

        // Temperature history chart
        TemperatureHistoryCard(history = uiState.history)

        Spacer(modifier = Modifier.height(16.dp))

        // SSH info + controls (only when Engine connected)
        if (engine != null) {
            SshInfoCard(engine = engine, context = context)
            Spacer(modifier = Modifier.height(12.dp))
            ServerControlButtons(
                engineState = engine.state,
                onStart = { viewModel.sendCommand("start") },
                onStop = { viewModel.sendCommand("stop") },
                onRestart = { viewModel.sendCommand("restart") },
            )
        } else {
            // Engine not connected card
            EngineNotConnectedCard(context = context)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DashboardHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PocketMonitor",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedButton(onClick = onSettingsClick) {
            Text("설정")
        }
    }
}

@Composable
private fun ServerStatusCard(engine: EngineStatus) {
    val extColors = PocketMonitorExtendedTheme.colors
    val (statusColor, statusBg, statusText) = when (engine.state) {
        "running" -> Triple(extColors.statusGreen, extColors.statusGreenBg, "서비스 실행 중")
        "stopped", "idle" -> Triple(extColors.statusAmber, extColors.statusAmberBg, "서비스 중지됨")
        "error" -> Triple(extColors.statusRed, extColors.statusRedBg, "오류 발생")
        "installing" -> Triple(extColors.statusAmber, extColors.statusAmberBg, "설치 중...")
        "starting" -> Triple(extColors.statusAmber, extColors.statusAmberBg, "시작 중...")
        "stopping" -> Triple(extColors.statusAmber, extColors.statusAmberBg, "중지 중...")
        else -> Triple(extColors.statusAmber, extColors.statusAmberBg, engine.state)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = statusText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (engine.state == "running" && engine.uptimeMs > 0) {
                    Text(
                        text = "가동시간: ${formatUptime(engine.uptimeMs)}",
                        fontSize = 13.sp,
                        color = PocketMonitorExtendedTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceMetricsGrid(device: DeviceStatus) {
    val extColors = PocketMonitorExtendedTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "CPU",
            value = "${device.cpuUsage}%",
            color = getMetricColor(device.cpuUsage, extColors),
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "메모리",
            value = "%.1f/%.1fGB".format(device.ramUsedGb, device.ramTotalGb),
            color = getMetricColor(
                if (device.ramTotalGb > 0) (device.ramUsedGb / device.ramTotalGb * 100).toInt() else 0,
                extColors,
            ),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "저장공간",
            value = "${device.storageUsedGb}/${device.storageTotalGb}GB",
            color = getMetricColor(
                if (device.storageTotalGb > 0) (device.storageUsedGb * 100 / device.storageTotalGb).toInt() else 0,
                extColors,
            ),
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "온도",
            value = "${device.temperature.toInt()}°C",
            subtitle = getTemperatureLabel(device.temperature),
            color = getTemperatureColor(device.temperature, extColors),
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String? = null,
    color: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = PocketMonitorExtendedTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = PocketMonitorExtendedTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureHistoryCard(history: List<HistoryEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "24시간 온도 히스토리",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (history.isEmpty()) {
                Text(
                    text = "데이터 수집 중...",
                    fontSize = 13.sp,
                    color = PocketMonitorExtendedTheme.colors.textSecondary,
                )
            } else {
                SimpleTemperatureChart(
                    entries = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
        }
    }
}

@Composable
private fun SimpleTemperatureChart(
    entries: List<HistoryEntry>,
    modifier: Modifier = Modifier,
) {
    val extColors = PocketMonitorExtendedTheme.colors

    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (entries.size < 2) return@Canvas

        val maxTemp = entries.maxOf { it.temperature }.coerceAtLeast(50f)
        val minTemp = entries.minOf { it.temperature }.coerceAtMost(20f)
        val range = (maxTemp - minTemp).coerceAtLeast(1f)
        val stepX = size.width / (entries.size - 1).coerceAtLeast(1)

        val path = androidx.compose.ui.graphics.Path()
        entries.forEachIndexed { index, entry ->
            val x = index * stepX
            val y = size.height - ((entry.temperature - minTemp) / range * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val lineColor = when {
            entries.last().temperature >= 50f -> extColors.statusRed
            entries.last().temperature >= 45f -> extColors.statusAmber
            else -> extColors.statusGreen
        }

        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun SshInfoCard(engine: EngineStatus, context: Context) {
    if (engine.ip == null) return

    val sshCommand = "ssh ${engine.user}@${engine.ip} -p ${engine.port}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SSH 접속 정보",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${engine.ip}:${engine.port}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ssh", sshCommand))
                        Toast.makeText(context, "복사됨", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("복사", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ServerControlButtons(
    engineState: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStart,
            modifier = Modifier.weight(1f),
            enabled = engineState != "running" && engineState != "starting",
            colors = ButtonDefaults.buttonColors(
                containerColor = PocketMonitorExtendedTheme.colors.statusGreen,
            ),
        ) {
            Text("시작")
        }
        Button(
            onClick = onStop,
            modifier = Modifier.weight(1f),
            enabled = engineState == "running",
            colors = ButtonDefaults.buttonColors(
                containerColor = PocketMonitorExtendedTheme.colors.statusRed,
            ),
        ) {
            Text("중지")
        }
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.weight(1f),
            enabled = engineState == "running",
        ) {
            Text("재시작")
        }
    }
}

@Composable
private fun EngineNotConnectedCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "서버 엔진이 감지되지 않았습니다",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "서버 기능을 사용하려면\nPocketServer Engine이 필요합니다",
                fontSize = 13.sp,
                color = PocketMonitorExtendedTheme.colors.textSecondary,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ENGINE_DOWNLOAD_URL))
                    context.startActivity(intent)
                },
            ) {
                Text("자세히 보기")
            }
        }
    }
}

private fun formatUptime(ms: Long): String {
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return buildString {
        if (days > 0) append("${days}일 ")
        append("${hours}시간 ${minutes}분")
    }
}

private fun getMetricColor(
    percent: Int,
    colors: kr.co.palank.pocketmonitor.ui.theme.PocketMonitorColors,
): Color {
    return when {
        percent >= 90 -> colors.statusRed
        percent >= 70 -> colors.statusAmber
        else -> colors.statusGreen
    }
}

private fun getTemperatureColor(
    temp: Float,
    colors: kr.co.palank.pocketmonitor.ui.theme.PocketMonitorColors,
): Color {
    return when {
        temp >= 50f -> colors.statusRed
        temp >= 45f -> colors.statusAmber
        else -> colors.statusGreen
    }
}

private fun getTemperatureLabel(temp: Float): String {
    return when {
        temp >= 50f -> "위험"
        temp >= 45f -> "주의"
        else -> "정상"
    }
}

private const val ENGINE_DOWNLOAD_URL = "https://pocket-server-palank.web.app"
