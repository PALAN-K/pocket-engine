package kr.co.palank.pocketserver.ui.servicestore

import android.content.Intent
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.service.ServiceStatus
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme

@Composable
fun ServiceSetupScreen(
    viewModel: ServiceStoreViewModel,
    onComplete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    val setupStep by viewModel.setupStep.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installMessage by viewModel.installMessage.collectAsState()
    val services by viewModel.services.collectAsState()
    val serviceDef = viewModel.currentServiceDef
    val serviceId = viewModel.currentServiceId.collectAsState().value

    // Watch service status to transition steps
    LaunchedEffect(services) {
        val current = serviceId?.let { id -> services.find { it.serviceId == id } }
        if (current != null) {
            when {
                current.status == ServiceStatus.INSTALLED && setupStep == SetupStep.INSTALLING -> {
                    viewModel.onInstallComplete()
                }
                current.status == ServiceStatus.RUNNING && setupStep == SetupStep.CONFIGURING -> {
                    viewModel.onConfigureComplete()
                }
                current.status == ServiceStatus.ERROR -> {
                    viewModel.onError()
                }
            }
        }
    }

    AnimatedContent(
        targetState = setupStep,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn())
                .togetherWith(slideOutHorizontally { -it } + fadeOut())
        },
        label = "setup_step",
    ) { step ->
        when (step) {
            SetupStep.INSTALLING -> InstallingStep(
                serviceName = serviceDef?.name ?: "",
                progress = installProgress,
                message = installMessage,
            )
            SetupStep.API_KEY_INPUT -> InputStep(
                viewModel = viewModel,
            )
            SetupStep.CONFIGURING -> ConfiguringStep(
                serviceName = serviceDef?.name ?: "",
            )
            SetupStep.COMPLETED -> CompletedStep(
                serviceName = serviceDef?.name ?: "",
                onDone = onComplete,
            )
            SetupStep.ERROR -> ErrorStep(
                serviceName = serviceDef?.name ?: "",
                errorMessage = services.find { it.serviceId == serviceId }?.errorMessage ?: "알 수 없는 오류",
                onRetry = {
                    serviceId?.let { viewModel.startSetup(it) }
                },
                onBack = onComplete,
            )
        }
    }
}

@Composable
private fun InstallingStep(
    serviceName: String,
    progress: Int,
    message: String,
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
        Text(
            text = "$serviceName 설치 중",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(40.dp))

        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${progress}%",
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
    }
}

@Composable
private fun InputStep(
    viewModel: ServiceStoreViewModel,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val serviceDef = viewModel.currentServiceDef ?: return
    val inputValues = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "${serviceDef.name} 설정",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "아래 정보를 입력해 주세요",
            fontSize = 15.sp,
            color = extColors.textSecondary,
        )

        Spacer(modifier = Modifier.height(32.dp))

        serviceDef.inputs.forEachIndexed { index, input ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary,
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = input.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputValues[input.id] ?: "",
                        onValueChange = { inputValues[input.id] = it },
                        placeholder = { Text(input.hint, fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    if (input.helpUrl != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(input.helpUrl))
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = input.helpLabel ?: "도움말",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        val allValid = serviceDef.inputs.all { input ->
            val value = inputValues[input.id] ?: ""
            if (input.validationRegex != null) {
                value.matches(Regex(input.validationRegex))
            } else {
                value.isNotBlank()
            }
        }

        Button(
            onClick = { viewModel.submitInputs(inputValues.toMap()) },
            enabled = allValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                disabledContainerColor = colorScheme.outline,
            ),
        ) {
            Text(
                text = "설정 완료",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun ConfiguringStep(
    serviceName: String,
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
        Text(
            text = "$serviceName 설정 중...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "API 키를 설정하고 서비스를 시작합니다",
            fontSize = 15.sp,
            color = extColors.textSecondary,
        )
    }
}

@Composable
private fun CompletedStep(
    serviceName: String,
    onDone: () -> Unit,
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
            text = "$serviceName 준비 완료!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Telegram에서 봇에게\n메시지를 보내보세요",
            fontSize = 16.sp,
            color = extColors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
            ),
        ) {
            Text(
                text = "완료",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ErrorStep(
    serviceName: String,
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
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
            text = "$serviceName 설치 실패",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = extColors.statusRedBg),
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
        ) {
            Text(
                text = "다시 시도",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = "돌아가기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
