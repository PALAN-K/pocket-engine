package kr.co.palank.pocketserver.ui.dashboard

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.palank.pocketserver.ad.AdMobBanner
import kr.co.palank.pocketserver.linux.ServerState
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // --- Header ---
        DashboardHeader(onNavigateToSettings = onNavigateToSettings)

        Spacer(modifier = Modifier.height(24.dp))

        // --- Server Status Card ---
        DashboardServerStatusCard(
            serverState = state.serverState,
            uptime = state.uptime,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Stats Grid (2x2) ---
        DashboardStatsGrid(
            cpuUsage = state.cpuUsage,
            ramUsedGb = state.ramUsedGb,
            ramTotalGb = state.ramTotalGb,
            storageFreeGb = state.storageFreeGb,
            storageTotalGb = state.storageTotalGb,
            temp = state.temp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- SSH Info Section (shown when server has been installed) ---
        if (state.serverState is ServerState.Running ||
            state.serverState is ServerState.Stopped ||
            state.serverState is ServerState.Idle
        ) {
            DashboardSshInfoSection(
                ipAddress = state.ipAddress,
                isWifiConnected = state.isWifiConnected,
                sshPort = state.sshPort,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Error Card ---
        if (state.serverState is ServerState.Error) {
            DashboardErrorCard(
                errorMessage = (state.serverState as ServerState.Error).message,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Control Buttons ---
        DashboardControlButtons(
            serverState = state.serverState,
            onStart = { viewModel.startServer() },
            onStop = { viewModel.stopServer() },
            onRestart = { viewModel.restartServer() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- AdMob Banner ---
        AdMobBanner(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun DashboardHeader(
    onNavigateToSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PocketServer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            letterSpacing = (-0.5).sp,
        )

        TextButton(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.textButtonColors(
                contentColor = extColors.textSecondary,
            ),
        ) {
            Text(
                text = "\u2699\uFE0F \uC124\uC815",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Server Status Card
// ---------------------------------------------------------------------------

@Composable
private fun DashboardServerStatusCard(
    serverState: ServerState,
    uptime: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    val (statusText, dotColor, bgColor) = getServerStatusDisplay(serverState, extColors, colorScheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = statusText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
            }

            if (serverState is ServerState.Running) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "\uAC00\uB3D9\uC2DC\uAC04: $uptime",
                    fontSize = 14.sp,
                    color = extColors.textSecondary,
                )
            }
        }
    }
}

private data class StatusDisplay(
    val text: String,
    val dotColor: Color,
    val bgColor: Color,
)

private fun getServerStatusDisplay(
    serverState: ServerState,
    extColors: kr.co.palank.pocketserver.ui.theme.PocketServerColors,
    colorScheme: androidx.compose.material3.ColorScheme,
): StatusDisplay {
    return when (serverState) {
        is ServerState.Idle -> StatusDisplay(
            text = "\uC11C\uBC84 \uB300\uAE30 \uC911",
            dotColor = colorScheme.outline,
            bgColor = colorScheme.surfaceVariant,
        )
        is ServerState.Installing -> StatusDisplay(
            text = "\uC11C\uBC84 \uC124\uCE58 \uC911...",
            dotColor = extColors.statusAmber,
            bgColor = extColors.statusAmberBg,
        )
        is ServerState.Starting -> StatusDisplay(
            text = "\uC11C\uBC84 \uC2DC\uC791 \uC911...",
            dotColor = extColors.statusAmber,
            bgColor = extColors.statusAmberBg,
        )
        is ServerState.Running -> StatusDisplay(
            text = "\uC11C\uBC84 \uC2E4\uD589 \uC911",
            dotColor = extColors.statusGreen,
            bgColor = extColors.statusGreenBg,
        )
        is ServerState.Stopping -> StatusDisplay(
            text = "\uC11C\uBC84 \uC911\uC9C0 \uC911...",
            dotColor = extColors.statusAmber,
            bgColor = extColors.statusAmberBg,
        )
        is ServerState.Stopped -> StatusDisplay(
            text = "\uC11C\uBC84 \uC911\uC9C0\uB428",
            dotColor = extColors.statusRed,
            bgColor = extColors.statusRedBg,
        )
        is ServerState.Error -> StatusDisplay(
            text = "\uC624\uB958 \uBC1C\uC0DD",
            dotColor = extColors.statusRed,
            bgColor = extColors.statusRedBg,
        )
    }
}

// ---------------------------------------------------------------------------
// Stats Grid (2x2)
// ---------------------------------------------------------------------------

@Composable
private fun DashboardStatsGrid(
    cpuUsage: Int,
    ramUsedGb: Double,
    ramTotalGb: Double,
    storageFreeGb: Long,
    storageTotalGb: Long,
    temp: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardStatCard(
                title = "CPU",
                value = "${cpuUsage}%",
                modifier = Modifier.weight(1f),
            )

            DashboardStatCard(
                title = "\uBA54\uBAA8\uB9AC",
                value = "${String.format("%.1f", ramUsedGb)}/${ramTotalGb.toInt()}GB",
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardStatCard(
                title = "\uC800\uC7A5\uACF5\uAC04",
                value = "${storageFreeGb}/${storageTotalGb}GB",
                modifier = Modifier.weight(1f),
            )

            DashboardStatCard(
                title = "\uC628\uB3C4",
                value = "${temp.toInt()}\u00B0C",
                valueColor = getTempColor(temp),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = extColors.textSecondary,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun getTempColor(temp: Float): Color {
    val extColors = PocketServerExtendedTheme.colors
    return when {
        temp >= 50f -> extColors.statusRed
        temp >= 45f -> extColors.statusAmber
        else -> extColors.statusGreen
    }
}

// ---------------------------------------------------------------------------
// SSH Info Section
// ---------------------------------------------------------------------------

@Composable
private fun DashboardSshInfoSection(
    ipAddress: String?,
    isWifiConnected: Boolean,
    sshPort: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val clipboardManager = LocalClipboardManager.current

    val displayIp = ipAddress ?: "WiFi \uC5F0\uACB0 \uB300\uAE30 \uC911..."
    val hasIp = ipAddress != null
    val sshCommand = "ssh pocketserver@${ipAddress ?: "IP"} -p $sshPort"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "SSH \uC811\uC18D \uC815\uBCF4",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = extColors.textSecondary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasIp) "$ipAddress:$sshPort" else displayIp,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (hasIp) FontFamily.Monospace else FontFamily.Default,
                    color = if (hasIp) colorScheme.onSurface else extColors.textSecondary,
                    modifier = Modifier.weight(1f),
                )

                if (hasIp) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(sshCommand))
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.primary,
                        ),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(
                            text = "\uBCF5\uC0AC",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (!isWifiConnected && !hasIp) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "WiFi\uC5D0 \uC5F0\uACB0\uD574\uC57C SSH \uC811\uC18D\uC774 \uAC00\uB2A5\uD569\uB2C8\uB2E4",
                    fontSize = 12.sp,
                    color = extColors.statusAmber,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error Card
// ---------------------------------------------------------------------------

@Composable
private fun DashboardErrorCard(
    errorMessage: String,
) {
    val extColors = PocketServerExtendedTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.statusRedBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(extColors.statusRed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "\uC624\uB958",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.statusRed,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = extColors.statusRed,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Control Buttons
// ---------------------------------------------------------------------------

@Composable
private fun DashboardControlButtons(
    serverState: ServerState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    when (serverState) {
        is ServerState.Idle, is ServerState.Stopped, is ServerState.Error -> {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "\uC2DC\uC791",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is ServerState.Running -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = extColors.statusRed,
                    ),
                ) {
                    Text(
                        text = "\uC911\uC9C0",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = "\uC7AC\uC2DC\uC791",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        is ServerState.Installing, is ServerState.Starting, is ServerState.Stopping -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = colorScheme.outline,
                    disabledContentColor = extColors.textSecondary,
                ),
            ) {
                Text(
                    text = "\uCC98\uB9AC \uC911...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

