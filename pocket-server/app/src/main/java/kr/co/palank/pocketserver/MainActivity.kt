package kr.co.palank.pocketserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import kr.co.palank.pocketserver.ad.AdManager
import kr.co.palank.pocketserver.linux.SessionManager
import kr.co.palank.pocketserver.manufacturer.OptimizationGuideScreen
import kr.co.palank.pocketserver.monitor.NetworkMonitor
import kr.co.palank.pocketserver.ui.dashboard.DashboardScreen
import kr.co.palank.pocketserver.ui.dashboard.DashboardViewModel
import kr.co.palank.pocketserver.ui.onboarding.OnboardingScreen
import kr.co.palank.pocketserver.ui.settings.SettingsScreen
import kr.co.palank.pocketserver.ui.theme.PocketServerTheme
import kr.co.palank.pocketserver.util.SpecChecker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AdManager.initialize(this)
        AdManager.loadInterstitial(this)

        setContent {
            PocketServerTheme {
                val sessionManager = remember { SessionManager(this@MainActivity) }
                val networkMonitor = remember { NetworkMonitor(this@MainActivity) }
                val spec = remember { SpecChecker.check(this@MainActivity) }

                // Start network monitoring and set DashboardViewModel companions
                DisposableEffect(Unit) {
                    networkMonitor.start()
                    DashboardViewModel.sessionManager = sessionManager
                    DashboardViewModel.networkMonitor = networkMonitor
                    onDispose {
                        networkMonitor.stop()
                        DashboardViewModel.sessionManager = null
                        DashboardViewModel.networkMonitor = null
                    }
                }

                // Initial screen: onboarding if not installed, dashboard if installed
                val initialScreen = if (sessionManager.isInstalled) "dashboard" else "onboarding"
                var currentScreen by remember { mutableStateOf(initialScreen) }

                when (currentScreen) {
                    "onboarding" -> OnboardingScreen(
                        spec = spec,
                        sessionManager = sessionManager,
                        networkMonitor = networkMonitor,
                        onNavigateToDashboard = {
                            currentScreen = "dashboard"
                        },
                        onNavigateToOptimizationGuide = {
                            currentScreen = "optimization_guide"
                        },
                    )

                    "optimization_guide" -> OptimizationGuideScreen(
                        onDismiss = {
                            currentScreen = "dashboard"
                        },
                    )

                    "dashboard" -> DashboardScreen(
                        onNavigateToSettings = {
                            currentScreen = "settings"
                        },
                    )

                    "settings" -> SettingsScreen(
                        sessionManager = sessionManager,
                        onNavigateBack = {
                            // After reset, isInstalled becomes false -> go to onboarding
                            // Otherwise, go back to dashboard
                            currentScreen = if (sessionManager.isInstalled) "dashboard" else "onboarding"
                        },
                    )
                }
            }
        }
    }
}
