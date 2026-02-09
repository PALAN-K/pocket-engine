package kr.co.palank.pocketmonitor.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.palank.pocketmonitor.ipc.EngineStatus
import kr.co.palank.pocketmonitor.monitor.DeviceStatus
import kr.co.palank.pocketmonitor.monitor.HistoryEntry
import kr.co.palank.pocketmonitor.ui.theme.BlueAccent
import kr.co.palank.pocketmonitor.ui.theme.ChartAmberGradient
import kr.co.palank.pocketmonitor.ui.theme.ChartGreenGradient
import kr.co.palank.pocketmonitor.ui.theme.ChartRedGradient
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

        DashboardHeader(
            onSettingsClick = onNavigateToSettings,
            device = uiState.device,
        )

        Spacer(modifier = Modifier.height(20.dp))

        HealthScoreGauge(score = uiState.healthScore, device = uiState.device)

        Spacer(modifier = Modifier.height(20.dp))

        val engine = uiState.engine
        if (engine != null) {
            ServerStatusCard(engine = engine)
            Spacer(modifier = Modifier.height(16.dp))
        }

        DeviceMetricsGrid(device = uiState.device)

        Spacer(modifier = Modifier.height(16.dp))

        BatteryDetailCard(device = uiState.device)

        Spacer(modifier = Modifier.height(16.dp))

        HistoryChartCard(history = uiState.history)

        Spacer(modifier = Modifier.height(16.dp))

        if (engine != null) {
            SshInfoCard(engine = engine, context = context)
            Spacer(modifier = Modifier.height(12.dp))
            ServerControlButtons(
                engineState = engine.state,
                onStart = { viewModel.sendCommand("start") },
                onStop = { viewModel.sendCommand("stop") },
                onRestart = { viewModel.sendCommand("restart") },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DashboardHeader(
    onSettingsClick: () -> Unit,
    device: DeviceStatus,
) {
    val extColors = PocketMonitorExtendedTheme.colors
    val statusDotColor = when {
        device.temperature >= 50f -> extColors.statusRed
        device.temperature >= 45f -> extColors.statusAmber
        else -> extColors.statusGreen
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "PocketMonitor",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusDotColor),
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "설정",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerStatusCard(engine: EngineStatus) {
    val extColors = PocketMonitorExtendedTheme.colors
    val (statusColor, statusBg, statusText, statusIcon) = when (engine.state) {
        "running" -> StatusInfo(extColors.statusGreen, extColors.statusGreenBg, "서비스 실행 중", Icons.Outlined.CloudDone)
        "stopped", "idle" -> StatusInfo(extColors.statusAmber, extColors.statusAmberBg, "서비스 중지됨", Icons.Outlined.CloudOff)
        "error" -> StatusInfo(extColors.statusRed, extColors.statusRedBg, "오류 발생", Icons.Outlined.Warning)
        "installing" -> StatusInfo(extColors.statusAmber, extColors.statusAmberBg, "설치 중...", Icons.Outlined.CloudOff)
        "starting" -> StatusInfo(extColors.statusAmber, extColors.statusAmberBg, "시작 중...", Icons.Outlined.CloudOff)
        "stopping" -> StatusInfo(extColors.statusAmber, extColors.statusAmberBg, "중지 중...", Icons.Outlined.CloudOff)
        else -> StatusInfo(extColors.statusAmber, extColors.statusAmberBg, engine.state, Icons.Outlined.CloudOff)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (engine.state == "running" && engine.uptimeMs > 0) {
                    Text(
                        text = formatUptime(engine.uptimeMs),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

private data class StatusInfo(
    val color: Color,
    val bg: Color,
    val text: String,
    val icon: ImageVector,
)

@Composable
private fun DeviceMetricsGrid(device: DeviceStatus) {
    val extColors = PocketMonitorExtendedTheme.colors

    val cpuPercent = device.cpuUsage
    val ramPercent = if (device.ramTotalGb > 0) (device.ramUsedGb / device.ramTotalGb * 100).toInt() else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCardWithGauge(
            modifier = Modifier.weight(1f),
            label = "CPU",
            value = "${device.cpuUsage}%",
            percent = cpuPercent,
            icon = Icons.Outlined.Speed,
            color = getMetricColor(cpuPercent, extColors),
        )
        MetricCardWithGauge(
            modifier = Modifier.weight(1f),
            label = "메모리",
            value = "%.1f/%.1fGB".format(device.ramUsedGb, device.ramTotalGb),
            percent = ramPercent,
            icon = Icons.Outlined.Memory,
            color = getMetricColor(ramPercent, extColors),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val storagePercent = if (device.storageTotalGb > 0) {
            (device.storageUsedGb * 100 / device.storageTotalGb).toInt()
        } else 0

        MetricCardSimple(
            modifier = Modifier.weight(1f),
            label = "저장공간",
            value = "${device.storageUsedGb}/${device.storageTotalGb}GB",
            icon = Icons.Outlined.Storage,
            color = getMetricColor(storagePercent, extColors),
        )
        MetricCardSimple(
            modifier = Modifier.weight(1f),
            label = "온도",
            value = "${device.temperature.toInt()}°C",
            subtitle = getTemperatureLabel(device.temperature),
            icon = Icons.Outlined.Thermostat,
            color = getTemperatureColor(device.temperature, extColors),
        )
    }
}

@Composable
private fun MetricCardWithGauge(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    percent: Int,
    icon: ImageVector,
    color: Color,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "gauge",
    )
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PocketMonitorExtendedTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = PocketMonitorExtendedTheme.colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = value,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Circular gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(44.dp)
                                .aspectRatio(1f),
                        ) {
                            val strokeWidth = 5.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = -225f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = color,
                                startAngle = -225f,
                                sweepAngle = 270f * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            )
                        }
                        Text(
                            text = "$percent",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCardSimple(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PocketMonitorExtendedTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = PocketMonitorExtendedTheme.colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
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
}

@Composable
private fun HealthScoreGauge(score: Int, device: DeviceStatus) {
    val extColors = PocketMonitorExtendedTheme.colors

    val scoreColor = when {
        score >= 90 -> extColors.statusGreen
        score >= 70 -> BlueAccent
        score >= 50 -> extColors.statusAmber
        else -> extColors.statusRed
    }

    val gradeText = when {
        score >= 90 -> "우수"
        score >= 70 -> "양호"
        score >= 50 -> "주의"
        else -> "위험"
    }

    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "healthGauge",
    )

    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .aspectRatio(1f),
            ) {
                val strokeWidth = 10.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = scoreColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$score",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "디바이스 건강",
                    fontSize = 13.sp,
                    color = PocketMonitorExtendedTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = gradeText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = scoreColor,
        )
    }
}

@Composable
private fun BatteryDetailCard(device: DeviceStatus) {
    val extColors = PocketMonitorExtendedTheme.colors

    val batteryColor = when {
        device.isCharging -> extColors.statusGreen
        device.batteryLevel >= 50 -> extColors.statusGreen
        device.batteryLevel >= 20 -> extColors.statusAmber
        else -> extColors.statusRed
    }

    val batteryIcon = when {
        device.isCharging -> Icons.Outlined.BatteryChargingFull
        device.batteryLevel >= 20 -> Icons.Outlined.BatteryStd
        else -> Icons.Outlined.BatteryAlert
    }

    val chargingStatusText = when {
        device.isCharging && device.chargingType.isNotEmpty() -> "${device.chargingType} 충전 중"
        device.isCharging -> "충전 중"
        else -> "방전 중"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(batteryColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = batteryIcon,
                            contentDescription = null,
                            tint = batteryColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "배터리",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = chargingStatusText,
                        fontSize = 12.sp,
                        color = batteryColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "${device.batteryLevel}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (device.isCharging && device.chargingType.isNotEmpty()) {
                            Text(
                                text = "${device.chargingType} 충전 중",
                                fontSize = 12.sp,
                                color = extColors.textSecondary,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.1fV".format(device.voltage),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "온도 ${device.temperature.toInt()}°C",
                            fontSize = 12.sp,
                            color = extColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryChartCard(history: List<HistoryEntry>) {
    val extColors = PocketMonitorExtendedTheme.colors
    val selectedTab = remember { mutableIntStateOf(0) }
    val tabLabels = listOf("온도", "CPU", "메모리")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabLabels.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab.intValue == index,
                        onClick = { selectedTab.intValue = index },
                        label = {
                            Text(text = label, fontSize = 13.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (history.isEmpty()) {
                Text(
                    text = "데이터 수집 중...",
                    fontSize = 13.sp,
                    color = extColors.textSecondary,
                )
            } else {
                val values: List<Float>
                val unit: String
                val lineColor: Color
                val gradientColor: Color
                val warningLines: List<Pair<Float, Color>>
                val minVal: Float
                val maxVal: Float

                when (selectedTab.intValue) {
                    0 -> {
                        values = history.map { it.temperature }
                        unit = "°C"
                        val lastTemp = values.last()
                        lineColor = when {
                            lastTemp >= 50f -> extColors.statusRed
                            lastTemp >= 45f -> extColors.statusAmber
                            else -> extColors.statusGreen
                        }
                        gradientColor = when {
                            lastTemp >= 50f -> ChartRedGradient
                            lastTemp >= 45f -> ChartAmberGradient
                            else -> ChartGreenGradient
                        }
                        warningLines = listOf(
                            45f to extColors.statusAmber.copy(alpha = 0.5f),
                            50f to extColors.statusRed.copy(alpha = 0.5f),
                        )
                        minVal = values.minOrNull()?.coerceAtMost(20f) ?: 20f
                        maxVal = values.maxOrNull()?.coerceAtLeast(55f) ?: 55f
                    }
                    1 -> {
                        values = history.map { it.cpuUsage.toFloat() }
                        unit = "%"
                        lineColor = BlueAccent
                        gradientColor = BlueAccent.copy(alpha = 0.25f)
                        warningLines = emptyList()
                        minVal = 0f
                        maxVal = 100f
                    }
                    else -> {
                        values = history.map { it.ramUsagePercent.toFloat() }
                        unit = "%"
                        lineColor = BlueAccent
                        gradientColor = BlueAccent.copy(alpha = 0.25f)
                        warningLines = emptyList()
                        minVal = 0f
                        maxVal = 100f
                    }
                }

                val displayMin = values.minOrNull()?.toInt() ?: 0
                val displayMax = values.maxOrNull()?.toInt() ?: 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "최저 $displayMin$unit",
                        fontSize = 11.sp,
                        color = extColors.textSecondary,
                    )
                    Text(
                        text = "최고 $displayMax$unit",
                        fontSize = 11.sp,
                        color = extColors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                HistoryLineChart(
                    values = values,
                    minVal = minVal,
                    maxVal = maxVal,
                    lineColor = lineColor,
                    gradientColor = gradientColor,
                    warningLines = warningLines,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryLineChart(
    values: List<Float>,
    minVal: Float,
    maxVal: Float,
    lineColor: Color,
    gradientColor: Color,
    warningLines: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val range = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        for ((threshold, warnColor) in warningLines) {
            val y = size.height - ((threshold - minVal) / range * size.height)
            if (y in 0f..size.height) {
                drawLine(
                    color = warnColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }
        }

        val linePath = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minVal) / range * size.height)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(linePath)
            val lastX = (values.size - 1) * stepX
            lineTo(lastX, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(gradientColor, Color.Transparent),
                startY = 0f,
                endY = size.height,
            ),
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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

