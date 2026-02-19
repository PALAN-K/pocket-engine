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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.catalog.ServiceCatalog
import kr.co.palank.pocketserver.service.ServiceStatus
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme

@Composable
fun ServiceSetupScreen(
    viewModel: ServiceStoreViewModel,
    onComplete: () -> Unit,
    onCancel: () -> Unit = onComplete,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    val setupStep by viewModel.setupStep.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installMessage by viewModel.installMessage.collectAsState()
    val services by viewModel.services.collectAsState()
    val serviceDef = viewModel.currentServiceDef
    val serviceId = viewModel.currentServiceId.collectAsState().value
    val isReconfiguring by viewModel.isReconfiguring.collectAsState()
    val reconfigureDefaults by viewModel.reconfigureDefaults.collectAsState()

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
                current.status == ServiceStatus.ERROR && setupStep == SetupStep.CONFIGURING -> {
                    viewModel.onError()
                }
                current.status == ServiceStatus.ERROR && setupStep == SetupStep.INSTALLING -> {
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
                isReconfiguring = isReconfiguring,
                reconfigureDefaults = reconfigureDefaults,
                onCancel = {
                    if (isReconfiguring) viewModel.clearReconfigure()
                    onCancel()
                },
            )
            SetupStep.CONFIGURING -> ConfiguringStep(
                serviceName = serviceDef?.name ?: "",
                isReconfiguring = isReconfiguring,
            )
            SetupStep.COMPLETED -> CompletedStep(
                serviceName = serviceDef?.name ?: "",
                isReconfiguring = isReconfiguring,
                onDone = {
                    if (isReconfiguring) viewModel.clearReconfigure()
                    onComplete()
                },
            )
            SetupStep.ERROR -> ErrorStep(
                serviceName = serviceDef?.name ?: "",
                errorMessage = services.find { it.serviceId == serviceId }?.errorMessage ?: "알 수 없는 오류",
                isReconfiguring = isReconfiguring,
                onRetry = {
                    if (isReconfiguring) {
                        serviceId?.let { viewModel.startReconfigure(it) }
                    } else {
                        serviceId?.let { viewModel.startSetup(it) }
                    }
                },
                onBack = {
                    if (isReconfiguring) viewModel.clearReconfigure()
                    onComplete()
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputStep(
    viewModel: ServiceStoreViewModel,
    isReconfiguring: Boolean = false,
    reconfigureDefaults: Map<String, String> = emptyMap(),
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val serviceDef = viewModel.currentServiceDef ?: return

    val providers = ServiceCatalog.providers

    // Resolve initial provider/model from reconfigure defaults
    val initialProvider = if (isReconfiguring) {
        val pid = reconfigureDefaults["provider"] ?: ""
        providers.find { it.id == pid } ?: providers.first()
    } else providers.first()

    val initialModel = if (isReconfiguring) {
        val mid = reconfigureDefaults["model"] ?: ""
        initialProvider.models.find { it.id == mid } ?: initialProvider.models.first()
    } else initialProvider.models.first()

    var selectedProvider by remember(isReconfiguring) { mutableStateOf(initialProvider) }
    var selectedModel by remember(isReconfiguring) { mutableStateOf(initialModel) }
    var apiKeyValue by remember(isReconfiguring) { mutableStateOf(if (isReconfiguring) reconfigureDefaults["api_key"] ?: "" else "") }
    val inputValues = remember(isReconfiguring) { mutableStateMapOf<String, String>().also { map ->
        if (isReconfiguring) {
            serviceDef.inputs.forEach { input ->
                reconfigureDefaults[input.id]?.let { v -> map[input.id] = v }
            }
        }
    } }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Back button header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로",
                    tint = colorScheme.onBackground,
                )
            }
            Text(
                text = if (isReconfiguring) "${serviceDef.name} 설정 변경" else "${serviceDef.name} 설정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
            )
        }

        Text(
            text = if (isReconfiguring) "변경할 항목을 수정해 주세요" else "아래 정보를 입력해 주세요",
            fontSize = 15.sp,
            color = extColors.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

        // --- Step 1: Provider Selection ---
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
                            text = "1",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "AI 제공자",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                providers.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedProvider = provider
                                selectedModel = provider.models.first()
                                apiKeyValue = ""
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedProvider.id == provider.id,
                            onClick = {
                                selectedProvider = provider
                                selectedModel = provider.models.first()
                                apiKeyValue = ""
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary,
                            ),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = provider.displayName,
                            fontSize = 15.sp,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Step 2: Model Selection ---
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
                            text = "2",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "모델 선택",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedModel.displayName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                    ) {
                        selectedProvider.models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName, fontSize = 14.sp) },
                                onClick = {
                                    selectedModel = model
                                    modelDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Step 3: API Key Input (dynamic per provider) ---
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
                            text = "3",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${selectedProvider.displayName.substringBefore(" (")} API Key",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiKeyValue,
                    onValueChange = { apiKeyValue = it },
                    placeholder = { Text("${selectedProvider.apiKeyPrefix}...", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.apiKeyHelpUrl))
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = selectedProvider.apiKeyHelpLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Step 4+: Remaining InputFields (telegram_token, etc.) ---
        serviceDef.inputs.forEachIndexed { index, input ->
            val stepNumber = index + 4

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
                                text = "$stepNumber",
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

        val apiKeyValid = apiKeyValue.matches(Regex(selectedProvider.apiKeyRegex))
        val inputFieldsValid = serviceDef.inputs.all { input ->
            val value = inputValues[input.id] ?: ""
            if (input.validationRegex != null) {
                value.matches(Regex(input.validationRegex))
            } else {
                value.isNotBlank()
            }
        }
        val allValid = apiKeyValid && inputFieldsValid

        Button(
            onClick = {
                val merged = inputValues.toMutableMap()
                merged["provider"] = selectedProvider.id
                merged["model"] = selectedModel.id
                merged["api_key"] = apiKeyValue
                if (isReconfiguring) {
                    viewModel.submitReconfigure(merged)
                } else {
                    viewModel.submitInputs(merged)
                }
            },
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
                text = if (isReconfiguring) "설정 변경" else "설정 완료",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
        } // end inner Column (horizontal padding)
    }
}

@Composable
private fun ConfiguringStep(
    serviceName: String,
    isReconfiguring: Boolean = false,
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
            text = if (isReconfiguring) "$serviceName 설정 변경 중..." else "$serviceName 설정 중...",
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
            text = if (isReconfiguring) "설정을 변경하고 서비스를 재시작합니다" else "API 키를 설정하고 서비스를 시작합니다",
            fontSize = 15.sp,
            color = extColors.textSecondary,
        )
    }
}

@Composable
private fun CompletedStep(
    serviceName: String,
    isReconfiguring: Boolean = false,
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
            text = if (isReconfiguring) "설정이 변경되었습니다" else "$serviceName 준비 완료!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isReconfiguring) "$serviceName 이 새 설정으로\n다시 시작되었습니다"
                   else "Telegram에서 봇에게\n메시지를 보내보세요",
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
    isReconfiguring: Boolean = false,
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
            text = if (isReconfiguring) "$serviceName 설정 변경 실패" else "$serviceName 설치 실패",
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
