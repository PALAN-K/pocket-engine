package kr.co.palank.pocketserver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.manufacturer.OptimizationGuideScreen
import kr.co.palank.pocketserver.monitor.NetworkMonitor
import kr.co.palank.pocketserver.ui.setup.SetupWizardScreen
import kr.co.palank.pocketserver.ui.theme.PocketServerTheme
import kr.co.palank.pocketserver.util.SpecChecker
import kr.co.palank.pocketserver.util.UpdateChecker
import kr.co.palank.pocketserver.util.UpdateInfo

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketServerTheme {
                val sessionManager = remember { SessionManager(this@MainActivity) }
                val networkMonitor = remember { NetworkMonitor(this@MainActivity) }
                val spec = remember { SpecChecker.check(this@MainActivity) }

                DisposableEffect(Unit) {
                    networkMonitor.start()
                    onDispose {
                        networkMonitor.stop()
                    }
                }

                // Update check
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        updateInfo = UpdateChecker.check(this@MainActivity)
                    }
                }

                var currentScreen by remember {
                    mutableStateOf(if (sessionManager.isInstalled) "setup" else "setup")
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Update banner
                    val update = updateInfo
                    if (update != null && update.hasUpdate) {
                        UpdateBanner(
                            latestVersion = update.latestVersion,
                            onTap = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                            },
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            "setup" -> SetupWizardScreen(
                                spec = spec,
                                sessionManager = sessionManager,
                                networkMonitor = networkMonitor,
                                onNavigateToOptimizationGuide = {
                                    currentScreen = "optimization_guide"
                                },
                            )

                            "optimization_guide" -> OptimizationGuideScreen(
                                onDismiss = {
                                    this@MainActivity.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    latestVersion: String,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "v$latestVersion 업데이트 가능",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "다운로드",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
