package kr.co.palank.pocketserver.linux

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed class InstallState {
    object Idle : InstallState()
    data class Progress(val percentage: Int, val message: String) : InstallState()
    object Completed : InstallState()
    data class Error(val error: String) : InstallState()
}

class InstallManager(private val context: Context) {
    
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    suspend fun startInstallation() {
        try {
            _state.value = InstallState.Progress(10, "PRoot 환경 초기화 중...")
            initDirectory()
            
            _state.value = InstallState.Progress(30, "Ubuntu 24.04 이미지 다운로드 중...")
            // TODO: 실제 다운로드 로직
            
            _state.value = InstallState.Progress(60, "시스템 압축 해제 중...")
            // TODO: 압축 해제 로직
            
            _state.value = InstallState.Progress(80, "스왑 메모리(2GB) 설정 중...")
            setupSwap()
            
            _state.value = InstallState.Progress(95, "Dropbear SSH 서버 설정 중...")
            
            _state.value = InstallState.Completed
        } catch (e: Exception) {
            _state.value = InstallState.Error(e.message ?: "알 수 없는 오류 발생")
        }
    }

    private fun initDirectory() {
        val rootDir = File(context.filesDir, "ubuntu")
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    private fun setupSwap() {
        // 스왑 설정 로직
    }
}
