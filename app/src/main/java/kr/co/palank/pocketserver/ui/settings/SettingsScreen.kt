package kr.co.palank.pocketserver.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.linux.InstallManager
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme

@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onNavigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // --- Header ---
        SettingsHeader(onNavigateBack = onNavigateBack)

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // --- SSH Section ---
            SettingsSshSection(sessionManager = sessionManager, context = context)

            Spacer(modifier = Modifier.height(28.dp))

            // --- Server Section ---
            SettingsServerSection(context = context)

            Spacer(modifier = Modifier.height(28.dp))

            // --- Danger Zone ---
            SettingsDangerSection(
                sessionManager = sessionManager,
                onNavigateBack = onNavigateBack,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // --- App Info Section ---
            SettingsAppInfoSection()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "뒤로",
                tint = colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "설정",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            letterSpacing = (-0.5).sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Section Header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    val extColors = PocketServerExtendedTheme.colors

    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = extColors.textSecondary,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

// ---------------------------------------------------------------------------
// SSH Section
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSshSection(
    sessionManager: SessionManager,
    context: Context,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors
    val clipboardManager = LocalClipboardManager.current

    var isPasswordVisible by remember { mutableStateOf(false) }
    var showNewPasswordDialog by remember { mutableStateOf(false) }
    var newGeneratedPassword by remember { mutableStateOf("") }

    val currentPassword = sessionManager.sshPassword ?: "---"

    SectionHeader(title = "SSH 설정")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // --- Password Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SSH 비밀번호",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isPasswordVisible) currentPassword else "\u2022".repeat(
                            currentPassword.length.coerceAtMost(12)
                        ),
                        fontSize = 14.sp,
                        fontFamily = if (isPasswordVisible) FontFamily.Monospace else FontFamily.Default,
                        color = extColors.textSecondary,
                    )
                }

                TextButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                ) {
                    Text(
                        text = if (isPasswordVisible) "숨기기" else "보기",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                    )
                }
            }

            // --- Divider ---
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(horizontal = 16.dp)
                    .background(colorScheme.outline),
            )

            // --- Change Password Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val password = InstallManager.generatePassword()
                        context
                            .getSharedPreferences("pocketserver_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("ssh_password", password)
                            .apply()
                        newGeneratedPassword = password
                        showNewPasswordDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "비밀번호 변경",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary,
                )
            }
        }
    }

    // --- New Password Dialog ---
    if (showNewPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showNewPasswordDialog = false },
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "새 비밀번호 생성 완료",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text = "새 SSH 비밀번호:",
                        fontSize = 14.sp,
                        color = extColors.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = newGeneratedPassword,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(newGeneratedPassword))
                                },
                            ) {
                                Text(
                                    text = "복사",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "다음 서버 재시작 시 적용됩니다",
                        fontSize = 13.sp,
                        color = extColors.statusAmber,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewPasswordDialog = false }) {
                    Text(
                        text = "확인",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Server Section
// ---------------------------------------------------------------------------

@Composable
private fun SettingsServerSection(context: Context) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    val serverPrefs = remember {
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
    }
    var autoStartEnabled by remember {
        mutableStateOf(serverPrefs.getBoolean("auto_start_on_boot", true))
    }

    SectionHeader(title = "서버 설정")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "부팅 시 자동 시작",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "폰 재부팅 후 서버를 자동으로 시작합니다",
                    fontSize = 13.sp,
                    color = extColors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = autoStartEnabled,
                onCheckedChange = { enabled ->
                    autoStartEnabled = enabled
                    serverPrefs.edit()
                        .putBoolean("auto_start_on_boot", enabled)
                        .apply()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Danger Zone Section
// ---------------------------------------------------------------------------

@Composable
private fun SettingsDangerSection(
    sessionManager: SessionManager,
    onNavigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    var showResetDialog by remember { mutableStateOf(false) }

    SectionHeader(title = "위험 구역")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showResetDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "서버 초기화",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.error,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "리눅스 환경을 완전히 삭제하고 처음부터 재설치합니다",
                    fontSize = 13.sp,
                    color = extColors.textSecondary,
                )
            }
        }
    }

    // --- Reset Confirmation Dialog ---
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = colorScheme.surface,
            title = {
                Text(
                    text = "서버 초기화",
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.error,
                )
            },
            text = {
                Text(
                    text = "서버를 초기화하면 리눅스 환경이 완전히 삭제됩니다. 정말 초기화하시겠습니까?",
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        sessionManager.stop()
                        sessionManager.reset()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "초기화",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetDialog = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "취소",
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// App Info Section
// ---------------------------------------------------------------------------

@Composable
private fun SettingsAppInfoSection() {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    SectionHeader(title = "앱 정보")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // --- Version Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "버전",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )

                Text(
                    text = "1.0",
                    fontSize = 15.sp,
                    color = extColors.textSecondary,
                )
            }

            // --- Divider ---
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(horizontal = 16.dp)
                    .background(colorScheme.outline),
            )

            // --- Open Source License Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "오픈소스 라이선스",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}
