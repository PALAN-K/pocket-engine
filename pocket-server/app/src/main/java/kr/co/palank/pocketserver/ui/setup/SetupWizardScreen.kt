package kr.co.palank.pocketserver.ui.setup

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.linux.InstallState
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.monitor.NetworkMonitor
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme
import kr.co.palank.pocketserver.util.DeviceSpec

@Composable
fun SetupWizardScreen(
    spec: DeviceSpec,
    sessionManager: SessionManager,
    networkMonitor: NetworkMonitor,
    optimizationGuideDone: Boolean,
    onNavigateToOptimizationGuide: () -> Unit,
) {
    val installState by sessionManager.installState.collectAsState()
    val networkState by networkMonitor.state.collectAsState()

    var currentPhase by remember { mutableStateOf("spec_check") }

    // React to install state changes
    when (installState) {
        is InstallState.Progress -> {
            if (currentPhase != "installing") {
                currentPhase = "installing"
            }
        }
        is InstallState.Completed -> {
            if (currentPhase != "completed") {
                currentPhase = "completed"
            }
        }
        is InstallState.Error -> {
            if (currentPhase != "error") {
                currentPhase = "error"
            }
        }
        is InstallState.Idle -> { /* keep current phase */ }
    }

    AnimatedContent(
        targetState = currentPhase,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn())
                .togetherWith(slideOutHorizontally { -it } + fadeOut())
        },
        label = "setup_wizard_phase",
    ) { phase ->
        when (phase) {
            "spec_check" -> SpecCheckPhase(
                spec = spec,
                onStartInstall = {
                    sessionManager.install()
                    currentPhase = "installing"
                },
            )
            "installing" -> InstallingPhase(
                installState = installState,
            )
            "completed" -> CompletedPhase(
                sshPassword = sessionManager.sshPassword,
                sshPort = sessionManager.sshPort,
                ipAddress = networkState.ipAddress,
                optimizationGuideDone = optimizationGuideDone,
                onNavigateToOptimizationGuide = onNavigateToOptimizationGuide,
            )
            "error" -> ErrorPhase(
                errorMessage = (installState as? InstallState.Error)?.error ?: "알 수 없는 오류",
                onRetry = {
                    currentPhase = "spec_check"
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Phase A: Spec Check
// ---------------------------------------------------------------------------

@Composable
private fun SpecCheckPhase(
    spec: DeviceSpec,
    onStartInstall: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val allSpecsOk = spec.isVersionOk && spec.isRamOk && spec.isStorageOk && spec.isArchOk

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "PocketEngine",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "서랍 속 스마트폰이\n나만의 서버가 됩니다",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = extColors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartInstall,
            enabled = allSpecsOk,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                disabledContainerColor = colorScheme.outline,
                disabledContentColor = extColors.textSecondary,
            ),
        ) {
            Text(
                text = "서버 설치 시작하기",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!allSpecsOk) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "기기 사양이 최소 요구사항을 충족하지 않습니다",
                fontSize = 13.sp,
                color = extColors.statusRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "기기 사양",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                SpecRow(label = spec.androidVersion, isOk = spec.isVersionOk)
                Spacer(modifier = Modifier.height(12.dp))
                SpecRow(label = "RAM ${String.format("%.1f", spec.ramSizeGb)}GB", isOk = spec.isRamOk)
                Spacer(modifier = Modifier.height(12.dp))
                SpecRow(label = "저장공간 ${spec.storageFreeGb}GB 여유", isOk = spec.isStorageOk)
                Spacer(modifier = Modifier.height(12.dp))
                SpecRow(label = "${spec.arch} 프로세서", isOk = spec.isArchOk)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SpecRow(
    label: String,
    isOk: Boolean,
) {
    val extColors = PocketServerExtendedTheme.colors
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isOk) extColors.statusGreenBg
                    else extColors.statusRedBg
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isOk) "\u2713" else "\u2717",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOk) extColors.statusGreen else extColors.statusRed,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            fontSize = 16.sp,
            color = colorScheme.onBackground,
        )
    }
}

// ---------------------------------------------------------------------------
// Phase B: Installing
// ---------------------------------------------------------------------------

@Composable
private fun InstallingPhase(
    installState: InstallState,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    val percentage = when (installState) {
        is InstallState.Progress -> installState.percentage
        is InstallState.Completed -> 100
        else -> 0
    }
    val message = when (installState) {
        is InstallState.Progress -> installState.message
        is InstallState.Completed -> "설치 완료!"
        else -> "준비 중..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "서버를 설치하고 있습니다",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(40.dp))

        LinearProgressIndicator(
            progress = percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${percentage}%",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 15.sp,
            color = extColors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "예상 소요시간: 약 5-10분",
            fontSize = 13.sp,
            color = extColors.textSecondary.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "설치 중에는 앱을 종료하지 마세요",
            fontSize = 13.sp,
            color = extColors.statusAmber,
        )
    }
}

// ---------------------------------------------------------------------------
// Phase C: Completed
// ---------------------------------------------------------------------------

@Composable
private fun CompletedPhase(
    sshPassword: String?,
    sshPort: Int,
    ipAddress: String?,
    optimizationGuideDone: Boolean,
    onNavigateToOptimizationGuide: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val clipboardManager = LocalClipboardManager.current

    val displayIp = ipAddress ?: "IP 확인 중..."
    val displayPassword = sshPassword ?: "--------"
    val sshCommand = "ssh root@$displayIp -p $sshPort"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Success icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(extColors.statusGreenBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u2713",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = extColors.statusGreen,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "서버 준비 완료!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // SSH info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = extColors.cardBackground,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "SSH 접속 정보",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SshInfoRow(label = "주소", value = displayIp)
                Spacer(modifier = Modifier.height(10.dp))
                SshInfoRow(label = "포트", value = "$sshPort")
                Spacer(modifier = Modifier.height(10.dp))
                SshInfoRow(label = "사용자", value = "root")
                Spacer(modifier = Modifier.height(10.dp))

                SshInfoRow(label = "비밀번호", value = displayPassword)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SSH command section
        Text(
            text = "PC에서 접속하기:",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = extColors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = extColors.cardBackground,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sshCommand,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

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
                        text = "복사",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // PocketMonitor 설치 안내 카드
        val monitorPackage = "kr.co.palank.pocketmonitor"
        val isMonitorInstalled = remember {
            try {
                context.packageManager.getPackageInfo(monitorPackage, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isMonitorInstalled) extColors.statusGreenBg else extColors.cardBackground,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isMonitorInstalled) {
                    Text(
                        text = "✓ PocketMonitor 연결됨",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = extColors.statusGreen,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "PocketMonitor에서 서버 상태를\n확인하고 관리할 수 있습니다",
                        fontSize = 14.sp,
                        color = extColors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                } else {
                    Text(
                        text = "PocketMonitor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "설치하면 매일 서버 상태를 확인하고\n안전하게 관리할 수 있습니다",
                        fontSize = 14.sp,
                        color = extColors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            try {
                                val marketIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=$monitorPackage"),
                                )
                                context.startActivity(marketIntent)
                            } catch (e: Exception) {
                                val browserIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$monitorPackage"),
                                )
                                context.startActivity(browserIntent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Play Store에서 설치",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 안전 권장사항 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = extColors.cardBackground,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "안전 권장사항",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extColors.textSecondary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                SafetyTipRow(text = "스마트플러그로 70~80% 충전 제한 권장")
                Spacer(modifier = Modifier.height(8.dp))
                SafetyTipRow(text = "폰 케이스를 제거하여 방열 확보")
                Spacer(modifier = Modifier.height(8.dp))
                SafetyTipRow(text = "금속판 또는 타일 위에 배치")
                Spacer(modifier = Modifier.height(8.dp))
                SafetyTipRow(text = "월 1회 배터리 팽창 여부 점검")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telegram 커뮤니티 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "도움이 필요하거나 다른 사용자와\n경험을 나누고 싶다면",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/pocketseever"),
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Telegram 커뮤니티 참여",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!optimizationGuideDone) {
            // 최초: 백그라운드 설정으로 안내
            Button(
                onClick = onNavigateToOptimizationGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "백그라운드 실행 설정",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            // 가이드 완료 후: 부수적 버튼으로 다시 볼 수 있게
            Text(
                text = "서버가 백그라운드에서 실행 중입니다",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = extColors.statusGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToOptimizationGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = extColors.textSecondary,
                ),
            ) {
                Text(
                    text = "백그라운드 설정 다시 보기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "이 앱은 닫아도 됩니다\n서버는 백그라운드에서 계속 실행됩니다",
            fontSize = 13.sp,
            color = extColors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        val engineVersionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }
        Text(
            text = "PocketEngine v$engineVersionName",
            fontSize = 12.sp,
            color = extColors.textSecondary.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SafetyTipRow(text: String) {
    val extColors = PocketServerExtendedTheme.colors
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "\u2022",
            fontSize = 14.sp,
            color = extColors.textSecondary,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = colorScheme.onSurface,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun SshInfoRow(
    label: String,
    value: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = extColors.textSecondary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Error Phase
// ---------------------------------------------------------------------------

@Composable
private fun ErrorPhase(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(extColors.statusRedBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = extColors.statusRed,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "설치 중 오류가 발생했습니다",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = extColors.statusRedBg,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = extColors.statusRed,
                lineHeight = 20.sp,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
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
                text = "다시 시도",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
