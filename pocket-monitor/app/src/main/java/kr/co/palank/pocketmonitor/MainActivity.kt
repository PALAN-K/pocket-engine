package kr.co.palank.pocketmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.palank.pocketmonitor.ad.BannerAd
import kr.co.palank.pocketmonitor.notification.DailyReportWorker
import kr.co.palank.pocketmonitor.notification.WeeklyReportWorker
import kr.co.palank.pocketmonitor.ui.dashboard.DashboardScreen
import kr.co.palank.pocketmonitor.ui.dashboard.DashboardViewModel
import kr.co.palank.pocketmonitor.ui.settings.SettingsScreen
import kr.co.palank.pocketmonitor.ui.theme.PocketMonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DailyReportWorker.schedule(this)
        WeeklyReportWorker.schedule(this)

        setContent {
            PocketMonitorTheme {
                val dashboardViewModel: DashboardViewModel = viewModel()
                var currentScreen by remember { mutableStateOf("dashboard") }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentScreen == "dashboard") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                BannerAd(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (currentScreen) {
                            "dashboard" -> DashboardScreen(
                                onNavigateToSettings = { currentScreen = "settings" },
                                viewModel = dashboardViewModel,
                            )
                            "settings" -> {
                                val uiState by dashboardViewModel.uiState.collectAsState()
                                SettingsScreen(
                                    engineConnected = uiState.engine != null,
                                    onBack = { currentScreen = "dashboard" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
