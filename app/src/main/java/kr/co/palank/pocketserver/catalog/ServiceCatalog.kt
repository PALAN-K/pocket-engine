package kr.co.palank.pocketserver.catalog

import android.content.Context

enum class InputType { API_KEY, BOT_TOKEN, TEXT }

data class InputField(
    val id: String,
    val label: String,
    val hint: String,
    val type: InputType,
    val helpUrl: String? = null,
    val helpLabel: String? = null,
    val validationRegex: String? = null,
)

data class ServiceDefinition(
    val id: String,
    val name: String,
    val description: String,
    val minRamMb: Int,
    val installTimeEstimate: String,
    val inputs: List<InputField>,
    val installer: (Context) -> ServiceInstaller,
)

interface ServiceInstaller {
    suspend fun install(onProgress: (Int, String) -> Unit)
    suspend fun configure(inputs: Map<String, String>)
    suspend fun start(): Boolean
    suspend fun stop(): Boolean
    suspend fun isRunning(): Boolean
    fun isInstalled(): Boolean
}

object ServiceCatalog {
    val services: List<ServiceDefinition> = listOf(
        ServiceDefinition(
            id = "picoclaw",
            name = "PicoClaw",
            description = "경량 AI 비서 — Telegram, Discord 지원\nGo 바이너리, 10MB RAM",
            minRamMb = 512,
            installTimeEstimate = "약 10초",
            inputs = listOf(
                InputField(
                    id = "gemini_api_key",
                    label = "Gemini API Key",
                    hint = "AIzaSy...",
                    type = InputType.API_KEY,
                    helpUrl = "https://aistudio.google.com/app/apikey",
                    helpLabel = "API 키 받기",
                    validationRegex = "^AIzaSy[a-zA-Z0-9_-]{33}$",
                ),
                InputField(
                    id = "telegram_token",
                    label = "Telegram Bot Token",
                    hint = "123456789:ABCdef...",
                    type = InputType.BOT_TOKEN,
                    helpUrl = "https://t.me/BotFather",
                    helpLabel = "BotFather 열기",
                    validationRegex = "^[0-9]+:.{30,}$",
                ),
            ),
            installer = { context -> PicoClawInstaller(context) },
        ),
        ServiceDefinition(
            id = "openclaw",
            name = "OpenClaw",
            description = "풀스택 AI 비서 — 다채널 지원\nNode.js 기반, 2GB+ RAM 권장",
            minRamMb = 2048,
            installTimeEstimate = "약 5-15분",
            inputs = listOf(
                InputField(
                    id = "gemini_api_key",
                    label = "Gemini API Key",
                    hint = "AIzaSy...",
                    type = InputType.API_KEY,
                    helpUrl = "https://aistudio.google.com/app/apikey",
                    helpLabel = "API 키 받기",
                    validationRegex = "^AIzaSy[a-zA-Z0-9_-]{33}$",
                ),
                InputField(
                    id = "telegram_token",
                    label = "Telegram Bot Token",
                    hint = "123456789:ABCdef...",
                    type = InputType.BOT_TOKEN,
                    helpUrl = "https://t.me/BotFather",
                    helpLabel = "BotFather 열기",
                    validationRegex = "^[0-9]+:.{30,}$",
                ),
            ),
            installer = { context -> OpenClawInstaller(context) },
        ),
    )
}
