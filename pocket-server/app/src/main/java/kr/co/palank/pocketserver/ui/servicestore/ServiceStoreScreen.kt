package kr.co.palank.pocketserver.ui.servicestore

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.catalog.ServiceCatalog
import kr.co.palank.pocketserver.service.ServiceStatus
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme

@Composable
fun ServiceStoreScreen(
    viewModel: ServiceStoreViewModel,
    onStartSetup: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val installedServices by viewModel.services.collectAsState()
    val deviceRamMb = viewModel.getDeviceRamMb()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로",
                    tint = colorScheme.onBackground,
                )
            }
            Text(
                text = "AI 비서",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "서버에 AI 비서를 설치하세요",
                fontSize = 15.sp,
                color = extColors.textSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Service cards
            ServiceCatalog.services.forEach { serviceDef ->
                val installed = installedServices.find { it.serviceId == serviceDef.id }
                val status = installed?.status ?: ServiceStatus.NOT_INSTALLED
                val meetsRam = deviceRamMb >= serviceDef.minRamMb
                val isFirst = serviceDef.id == "picoclaw"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = serviceDef.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                    )
                                    if (isFirst) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "추천",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary,
                                            modifier = Modifier
                                                .background(
                                                    colorScheme.primaryContainer.copy(alpha = 0.3f),
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            // Status dot
                            if (status != ServiceStatus.NOT_INSTALLED) {
                                val dotColor = when (status) {
                                    ServiceStatus.RUNNING -> extColors.statusGreen
                                    ServiceStatus.ERROR -> extColors.statusRed
                                    else -> extColors.statusAmber
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = serviceDef.description,
                            fontSize = 14.sp,
                            color = extColors.textSecondary,
                            lineHeight = 20.sp,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "설치 시간: ${serviceDef.installTimeEstimate} | RAM ${serviceDef.minRamMb}MB+",
                            fontSize = 12.sp,
                            color = extColors.textSecondary.copy(alpha = 0.7f),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        when (status) {
                            ServiceStatus.NOT_INSTALLED -> {
                                Button(
                                    onClick = { onStartSetup(serviceDef.id) },
                                    enabled = meetsRam,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        disabledContainerColor = colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = if (meetsRam) "설치하기" else "RAM 부족 (${deviceRamMb}MB)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            ServiceStatus.RUNNING -> {
                                OutlinedButton(
                                    onClick = { viewModel.stopService(serviceDef.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = "중지",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            ServiceStatus.INSTALLED, ServiceStatus.STOPPED -> {
                                Button(
                                    onClick = { viewModel.startService(serviceDef.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = "시작",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            ServiceStatus.INSTALLING, ServiceStatus.CONFIGURING -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = "설치 중...",
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                            ServiceStatus.ERROR -> {
                                Column {
                                    Text(
                                        text = installed?.errorMessage ?: "오류 발생",
                                        fontSize = 13.sp,
                                        color = extColors.statusRed,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onStartSetup(serviceDef.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(
                                            text = "다시 시도",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Cost comparison card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.primaryContainer.copy(alpha = 0.15f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "비용 비교",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CostRow(label = "클라우드 호스팅", cost = "$39/월", highlight = false)
                    Spacer(modifier = Modifier.height(6.dp))
                    CostRow(label = "VPS 서버", cost = "$5/월", highlight = false)
                    Spacer(modifier = Modifier.height(6.dp))
                    CostRow(label = "PocketEngine", cost = "$0", highlight = true)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CostRow(
    label: String,
    cost: String,
    highlight: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (highlight) colorScheme.primary else extColors.textSecondary,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = cost,
            fontSize = 14.sp,
            color = if (highlight) colorScheme.primary else colorScheme.onSurface,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
