package kr.co.palank.pocketserver.manufacturer

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.palank.pocketserver.ui.theme.PocketServerExtendedTheme
import java.util.Locale

@Composable
fun OptimizationGuideScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val steps = remember { ManufacturerOptimizationHelper.getRequiredSteps(context) }
    val manufacturerName = remember { getDisplayManufacturerName() }

    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Header
        Text(
            text = "백그라운드 실행 설정",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "${manufacturerName} 기기가\n감지되었습니다",
            fontSize = 17.sp,
            color = extColors.textSecondary,
            lineHeight = 24.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "서버가 안정적으로 동작하려면\n아래 설정이 필요합니다:",
            fontSize = 15.sp,
            color = colorScheme.onBackground,
            lineHeight = 22.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Step cards
        steps.forEachIndexed { index, step ->
            OptimizationStepCard(
                stepNumber = index + 1,
                step = step,
                onOpenSettings = {
                    val launched = ManufacturerOptimizationHelper.launchIntent(context, step.intents)
                    if (!launched) {
                        Toast.makeText(
                            context,
                            "설정을 열 수 없습니다. 수동으로 진행해주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            if (index < steps.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Warning box
        WarningBox()

        Spacer(modifier = Modifier.height(16.dp))

        // dontkillmyapp.com link hint
        Text(
            text = "자세한 내용은 dontkillmyapp.com을 참고하세요.",
            fontSize = 13.sp,
            color = extColors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons
        Button(
            onClick = onDismiss,
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
                text = "완료",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = extColors.textSecondary,
            ),
        ) {
            Text(
                text = "나중에 하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun OptimizationStepCard(
    stepNumber: Int,
    step: OptimizationStep,
    onOpenSettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extColors = PocketServerExtendedTheme.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = extColors.cardBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Step number badge
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
                    text = step.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = step.description,
                fontSize = 14.sp,
                color = extColors.textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 40.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "설정 열기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fallback user instructions
            Text(
                text = step.userInstructions,
                fontSize = 12.sp,
                color = extColors.textSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun WarningBox() {
    val extColors = PocketServerExtendedTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = extColors.statusAmberBg,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = extColors.statusAmber,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(extColors.statusAmber.copy(alpha = 0.15f)),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "이 설정을 하지 않으면\n화면이 꺼질 때 서버가 중지될 수 있습니다",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = extColors.statusAmber,
                lineHeight = 20.sp,
            )
        }
    }
}

private fun getDisplayManufacturerName(): String {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return when {
        manufacturer.contains("samsung") -> "Samsung Galaxy"
        manufacturer.contains("xiaomi") -> "Xiaomi"
        manufacturer.contains("redmi") -> "Redmi"
        manufacturer.contains("poco") -> "POCO"
        manufacturer.contains("huawei") -> "Huawei"
        manufacturer.contains("honor") -> "Honor"
        manufacturer.contains("oppo") -> "OPPO"
        manufacturer.contains("realme") -> "Realme"
        manufacturer.contains("oneplus") -> "OnePlus"
        manufacturer.contains("vivo") -> "Vivo"
        else -> Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
