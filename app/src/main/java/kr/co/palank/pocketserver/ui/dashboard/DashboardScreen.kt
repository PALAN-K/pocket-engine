package kr.co.palank.pocketserver.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "PocketServer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        ServerStatusCard(state.uptime)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard("CPU 사용률", "${state.cpuUsage}%", Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            StatCard(
                "가용 메모리", 
                "${String.format("%.1f", state.ramUsedGb)} / ${state.ramTotalGb.toInt()}GB", 
                Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                "배터리 온도", 
                "${state.temp.toInt()}C", 
                Modifier.weight(1f), 
                getTempColor(state.temp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            StatCard("저장공간", "5.2/23GB", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Google AdMob 배너 영역", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ServerStatusCard(uptime: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), RoundedCornerShape(50)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("서버 정상 실행 중", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text("가동시간: $uptime", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Black) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

fun getTempColor(temp: Float): Color {
    return when {
        temp >= 50f -> Color.Red
        temp >= 45f -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
}
