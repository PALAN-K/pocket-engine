package kr.co.palank.pocketserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kr.co.palank.pocketserver.ui.dashboard.DashboardScreen
import kr.co.palank.pocketserver.ui.onboarding.OnboardingScreen
import kr.co.palank.pocketserver.util.SpecChecker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf("dashboard") } // MVP 개발을 위해 대시보드 우선 표시
            val spec = remember { SpecChecker.check(this@MainActivity) }

            when (currentScreen) {
                "dashboard" -> DashboardScreen()
                "onboarding" -> OnboardingScreen(spec = spec) {
                    currentScreen = "dashboard"
                }
            }
        }
    }
}
