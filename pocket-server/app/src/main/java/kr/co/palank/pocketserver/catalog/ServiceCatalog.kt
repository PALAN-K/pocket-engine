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

data class ModelDef(
    val id: String,
    val displayName: String,
)

data class ProviderDef(
    val id: String,
    val displayName: String,
    val apiKeyPrefix: String,
    val apiKeyRegex: String,
    val apiKeyHelpUrl: String,
    val apiKeyHelpLabel: String,
    val models: List<ModelDef>,
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
    fun readCurrentConfig(): Map<String, String>?
}

object ServiceCatalog {

    val providers = listOf(
        ProviderDef(
            id = "gemini",
            displayName = "Google Gemini (권장, 250회/일)",
            apiKeyPrefix = "AIzaSy",
            apiKeyRegex = "^AIzaSy[a-zA-Z0-9_-]{33}$",
            apiKeyHelpUrl = "https://aistudio.google.com/app/apikey",
            apiKeyHelpLabel = "Gemini API 키 받기",
            models = listOf(
                ModelDef("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
                ModelDef("gemini-2.5-flash", "Gemini 2.5 Flash"),
                ModelDef("gemini-2.0-flash", "Gemini 2.0 Flash"),
            ),
        ),
        ProviderDef(
            id = "groq",
            displayName = "Groq (10만 토큰/일 제한)",
            apiKeyPrefix = "gsk_",
            apiKeyRegex = "^gsk_[a-zA-Z0-9_-]{36,56}$",
            apiKeyHelpUrl = "https://console.groq.com/keys",
            apiKeyHelpLabel = "Groq API 키 받기",
            models = listOf(
                ModelDef("llama-3.1-8b-instant", "Llama 3.1 8B (권장, 50만 토큰/일)"),
                ModelDef("llama-3.3-70b-versatile", "Llama 3.3 70B (10만 토큰/일)"),
                ModelDef("llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B"),
            ),
        ),
    )

    val services: List<ServiceDefinition> = listOf(
        ServiceDefinition(
            id = "picoclaw",
            name = "PicoClaw",
            description = "경량 AI 비서 — Telegram, Discord 지원\nGo 바이너리, 10MB RAM",
            minRamMb = 512,
            installTimeEstimate = "약 10초",
            inputs = listOf(
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
