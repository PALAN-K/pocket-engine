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
    /** true = OAuth 브라우저 로그인 (API 키 불필요), false = API 키 입력 */
    val isOAuth: Boolean = false,
    /** OAuth 프로바이더가 지원하는 서비스 ID 목록 (빈 목록 = 전체 서비스 지원) */
    val supportedServiceIds: List<String> = emptyList(),
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
        ProviderDef(
            id = "openrouter",
            displayName = "OpenRouter (300+ 모델, 자동 로테이션)",
            apiKeyPrefix = "sk-or-v1-",
            apiKeyRegex = "^sk-or-v1-[a-f0-9]{64}$",
            apiKeyHelpUrl = "https://openrouter.ai/keys",
            apiKeyHelpLabel = "OpenRouter API 키 받기",
            models = listOf(
                ModelDef("google/gemini-2.5-flash", "Gemini 2.5 Flash (무료)"),
                ModelDef("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B (무료)"),
                ModelDef("openai/gpt-oss-120b", "GPT-OSS 120B (무료)"),
            ),
        ),
        ProviderDef(
            id = "chatgpt",
            displayName = "ChatGPT 구독 (추가비용 없음)",
            apiKeyPrefix = "",
            apiKeyRegex = ".*",
            apiKeyHelpUrl = "https://chatgpt.com",
            apiKeyHelpLabel = "ChatGPT로 로그인",
            isOAuth = true,
            supportedServiceIds = emptyList(), // 양쪽 모두 지원 (PicoClaw: codex-cli, OpenClaw: openai-codex)
            models = listOf(
                ModelDef("openai-codex/gpt-5.3-codex", "GPT-5.3 Codex (최신)"),
                ModelDef("openai-codex/gpt-5.2-codex", "GPT-5.2 Codex"),
                ModelDef("openai-codex/gpt-5-codex-mini", "GPT-5 Codex Mini (경량)"),
            ),
        ),
        ProviderDef(
            id = "openai",
            displayName = "OpenAI API (유료, ~$1/월)",
            apiKeyPrefix = "sk-",
            apiKeyRegex = "^sk-.{20,}$",
            apiKeyHelpUrl = "https://platform.openai.com/api-keys",
            apiKeyHelpLabel = "OpenAI API 키 받기",
            models = listOf(
                ModelDef("gpt-4.1-nano", "GPT-4.1 Nano (최저가)"),
                ModelDef("gpt-4.1-mini", "GPT-4.1 Mini (밸런스)"),
                ModelDef("gpt-4.1", "GPT-4.1 (최고품질)"),
            ),
        ),
    )

    /** 주어진 서비스에 사용 가능한 프로바이더 목록 */
    fun providersForService(serviceId: String): List<ProviderDef> {
        return providers.filter { provider ->
            provider.supportedServiceIds.isEmpty() || serviceId in provider.supportedServiceIds
        }
    }

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
