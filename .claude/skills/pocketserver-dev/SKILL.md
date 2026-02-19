---
name: pocketserver-dev
description: Development guide for PocketServer — a 2-app system (PocketMonitor + PocketServer Engine) that converts old smartphones into Linux SSH servers. PocketMonitor (Play Store) handles monitoring, ads, and push notifications. PocketServer Engine (sideloaded) handles PRoot Linux setup, Dropbear SSH, and 7-layer keep-alive. Use when implementing any PocketServer feature including IPC, monitoring dashboards, AdMob, PRoot, Dropbear, keep-alive, or either app. Trigger on any task related to Android background services, proot, dropbear, wake locks, LocalSocket IPC, or the PocketServer/PocketMonitor apps.
---

# PocketServer Development Guide

## Project Overview

2-App Android system referencing UserLand's PRoot/Dropbear approach. Provides one-click Linux server setup on old Android phones via sideloaded Engine app, with Play Store Monitor app for monitoring, ads, and engagement.

- **Architecture**: 2-App (Monitor + Engine)
- **PocketMonitor**: `kr.co.palank.pocketmonitor` → Play Store
- **PocketServer Engine**: `kr.co.palank.pocketserver` → Sideload (Firebase Hosting)
- **Target**: Vibe coders, AI agent users repurposing old phones as servers
- **Revenue**: Free + AdMob in Monitor only (banner + interstitial on open/exit)
- **IAP**: None. All features free. User acquisition is #1 priority.
- **Tech stack**: Kotlin, Jetpack Compose, PRoot, Dropbear, AdMob, LocalSocket IPC
- **Firebase**: Project `pocket-server-palank` (Crashlytics + Hosting)
- **Signing**: `keystore/pocketserver-release.jks` — both apps use same key (gitignored)
- **GitHub**: https://github.com/naegeon/pocket-server.git

## Why 2 Apps?

Google Play Store prohibits: runtime code execution (rootfs), PRoot binaries, specialUse FGS for servers, 7-layer keep-alive, indefinite wake locks. These are all in the Engine app (sideloaded, not subject to Play Store policies). Monitor app (Play Store) contains zero policy-violating code.

## Architecture

```
PocketMonitor (Play Store)           PocketServer Engine (Sideload)
┌────────────────────────┐           ┌────────────────────────┐
│ DeviceMonitor          │           │ PRoot + Ubuntu 24.04   │
│ DashboardScreen + VM   │           │ Dropbear SSH :2022     │
│ EngineConnector ───────┼─LocalSock─┼─► IpcServer            │
│ AlertReceiver ◄────────┼─Broadcast─┼── AlertBroadcaster     │
│ DailyReportScheduler   │           │ ServerForegroundService│
│ AdManager (AdMob)      │           │ 7-Layer Keep-Alive     │
│ NO PRoot/rootfs/SSH    │           │ NO ads                 │
└────────────────────────┘           └────────────────────────┘
```

## App Structure — PocketMonitor (Play Store)

```
kr.co.palank.pocketmonitor/
├── MainActivity.kt                    메인 액티비티
├── ui/
│   ├── dashboard/
│   │   ├── DashboardScreen.kt         메인 대시보드
│   │   └── DashboardViewModel.kt      상태 관리
│   ├── settings/
│   │   └── SettingsScreen.kt          설정
│   └── theme/
│       ├── Color.kt                   컬러
│       └── Theme.kt                   테마
├── ipc/
│   ├── EngineConnector.kt             LocalSocket 클라이언트
│   └── AlertReceiver.kt              Broadcast 수신기
├── monitor/
│   ├── DeviceMonitor.kt               CPU/RAM/온도 자체 모니터링
│   └── HistoryTracker.kt              24시간 히스토리
├── notification/
│   ├── DailyReportScheduler.kt        매일 9시 리포트
│   ├── WeeklyReportScheduler.kt       매주 일요일 요약
│   └── AlertNotifier.kt              온도/이벤트 알림
├── ad/
│   └── AdManager.kt                   AdMob (배너 + 전면)
└── util/
    └── BatteryMonitor.kt              온도 모니터링
```

## App Structure — PocketServer Engine (Sideload)

```
kr.co.palank.pocketserver/
├── MainActivity.kt                    셋업 마법사 호스트
├── ui/
│   ├── setup/
│   │   └── SetupWizardScreen.kt       설치 마법사
│   └── optimization/
│       └── OptimizationGuide.kt       제조사별 딥링크
├── service/
│   ├── ServerForegroundService.kt     7계층 Keep-Alive
│   └── WatchdogWorker.kt             15분 헬스체크
├── receiver/
│   └── BootReceiver.kt               BOOT_COMPLETED
├── linux/
│   ├── ProotBinaryManager.kt         PRoot 바이너리 추출
│   ├── ProotManager.kt               PRoot 프로세스 관리
│   ├── InstallManager.kt             Ubuntu 설치
│   ├── DropbearManager.kt            Dropbear SSH
│   ├── SwapManager.kt                스왑 메모리
│   └── SessionManager.kt             상태 머신
├── ipc/
│   ├── IpcServer.kt                   LocalSocket 서버
│   └── AlertBroadcaster.kt           Monitor로 Broadcast
├── monitor/
│   ├── ResourceMonitor.kt            CPU/RAM
│   └── NetworkMonitor.kt             WiFi IP
├── manufacturer/
│   ├── ManufacturerDetector.kt       제조사 감지
│   └── ManufacturerOptimizationHelper.kt  딥링크
└── util/
    ├── SpecChecker.kt                 사양 검사
    └── BatteryMonitor.kt             온도 (50°C 자동정지)
```

## Development Workflow

### Before writing ANY code:
1. Search this skill's references/ directory first
2. Search official Android documentation (developer.android.com)
3. Check existing code in pocket-server/ and pocket-monitor/ before writing new code
4. Search UserLand source for PRoot/Dropbear patterns (github.com/CypherpunkArmory/UserLAnd)
5. Search Termux source code for keep-alive patterns (github.com/termux/termux-app)
6. Only then use internal knowledge as fallback

### Key implementation areas and their references:

| Task | Reference File |
|------|---------------|
| PRoot/Dropbear integration | [userland-fork-guide.md](references/userland-fork-guide.md) |
| 7-layer keep-alive system | [android-keepalive.md](references/android-keepalive.md) |
| Battery/temperature, swap | [android-battery.md](references/android-battery.md) |
| Manufacturer deep links | [manufacturer-deeplinks.md](references/manufacturer-deeplinks.md) |
| WiFi IP, SSH, tunneling | [networking.md](references/networking.md) |
| Signing keystore, app signing | [signing-keystore.md](references/signing-keystore.md) |
| Build, ADB, device testing | [device-testing.md](references/device-testing.md) |
| Deployment, release, R2, Firebase | [deployment-guide.md](references/deployment-guide.md) |
| Service Store, PicoClaw, OpenClaw | [service-store-guide.md](references/service-store-guide.md) |

## IPC Protocol v1.0 (LocalSocket)

### 연결
- Socket name: `pocketserver_ipc` (abstract namespace)
- 인증: 양 앱 동일 서명 키 → 패키지 서명 검증 (토큰 불필요)
- Engine의 IpcServer가 `LocalServerSocket`으로 리스닝
- Monitor의 EngineConnector가 `LocalSocket`으로 연결

### 프로토콜 (JSON, newline-delimited)

```
1. 핸드셰이크 (최초 연결 시)
   Monitor → Engine:  {"cmd":"handshake","protocol_version":"1.0"}
   Engine → Monitor:  {"ok":true,"protocol_version":"1.0","engine_version":"1.0.0"}

2. 상태 조회 (2초 간격 폴링)
   Monitor → Engine:  {"cmd":"status"}
   Engine → Monitor:  {"state":"running","cpu":12,"ram":62,"temp":38,
                        "uptime":123456,"ip":"192.168.0.15","port":2022,
                        "user":"pocketserver","disk_used":5200,"disk_total":23000}

3. 서버 제어
   Monitor → Engine:  {"cmd":"start"}
   Engine → Monitor:  {"ok":true}
   Monitor → Engine:  {"cmd":"stop"}
   Engine → Monitor:  {"ok":true}
   Monitor → Engine:  {"cmd":"restart"}
   Engine → Monitor:  {"ok":true}

4. 에러 응답
   Engine → Monitor:  {"ok":false,"error":"server_already_running"}
   Engine → Monitor:  {"ok":false,"error":"install_not_complete"}
```

### 재연결 전략
- Monitor는 2초 간격으로 Engine에 폴링
- 연결 실패 시 exponential backoff: 2s → 4s → 8s → 최대 30s
- 10회 연속 실패 시 "Engine 연결 불가" UI 표시
- Engine 재시작 감지 시 자동 재연결 (handshake 재수행)

### Broadcast (긴급 알림, 보조 채널)
```
Engine → Monitor:
  Action:   kr.co.palank.pocketserver.ALERT
  Package:  kr.co.palank.pocketmonitor
  Extras:
    type = "temp_warning" | "temp_critical" | "server_crash" | "server_restarted"
    temp = 46.0 (float, 온도 관련 알림 시)
    message = "서버가 50°C로 자동 정지되었습니다" (선택)
```

## Ad Strategy (Monitor App Only)

| Ad Type | Trigger | Frequency |
|---------|---------|-----------|
| Banner | Dashboard bottom | Always visible |
| App Open Ad (진입) | App launch → dashboard | Every launch |

- **App Open Ad** 사용 (Interstitial 아님) — AdMob 공식 권장 형식
- 앱 이탈 시 광고 **없음** (AdMob 정책: 사용자 액션 없는 전면광고 금지)
- Engine app has ZERO ads

## Engine Additional Features

- **Firebase Crashlytics**: 크래시 자동 수집 (사이드로드 앱 디버깅 필수)
- **자동 업데이트 체크**: 앱 실행 시 Firebase Hosting의 version.json 확인 → 배너 표시

## Service Store (AI 비서 원클릭 설치) — Phase 5

### 개요
Engine 앱 내에서 AI 에이전트(PicoClaw, OpenClaw)를 원클릭 설치하는 기능.
서버 설치 완료 후 선택적으로 제공. "마켓플레이스"가 아닌 opinionated 추천 방식.

### 지원 서비스

| 서비스 | 타입 | 크기 | RAM | 설치 시간 | 인증 | 우선순위 |
|--------|------|------|-----|-----------|------|----------|
| PicoClaw | Go single binary | ~15MB | <10MB | 10초 | Gemini API Key / ChatGPT 구독 | **1순위** |
| OpenClaw | Node.js 앱 | ~500MB | 2GB+ | 5-15분 | Gemini API Key / ChatGPT 구독 | 2순위 |

### PicoClaw 상세
- 출처: https://picoclaw.ai/docs, https://github.com/sipeed/picoclaw
- Go 네이티브 ARM64 바이너리 — 의존성 제로
- 설치: `curl -L -o /usr/local/bin/picoclaw <release_url> && chmod +x`
- Config: `~/.picoclaw/config.json`
- 채널: Telegram, Discord, QQ 지원
- 구형폰(3GB RAM)에서 완벽 동작
- `picoclaw onboard`는 CLI 플래그 미지원 → config.json 직접 생성 방식 사용
- `allow_from: []` (빈 배열 = 전체 허용, `["*"]` 와일드카드 미지원 — exact match만 수행)
- model 형식: `provider/model` (예: `groq/llama-3.3-70b-versatile`, `gemini/gemini-2.5-flash`)
- 필수 기본값: max_tokens=8192, temperature=0.7, max_tool_iterations=20
- Groq tool_use_failed: Groq API 상류 간헐적 버그, 우리 코드로 수정 불가 (출처: https://community.groq.com/t/tool-use-failed-on-llama4-models/427)
- **codex-cli 서브프로세스 프로바이더** (v0.1.2+, PR #80): ChatGPT 구독 인증으로 사용 가능 [실험]
  - Config: `"provider": "codex-cli"`, `"model": "codex-cli"` (agents.defaults에 설정)
  - 동작: PicoClaw가 내부적으로 `codex exec --json` 서브프로세스 실행
  - 인증: `~/.codex/auth.json` 파일 필요 (Codex CLI OAuth 인증 후 생성됨)
  - 의존성: Node.js + Codex CLI (`npm install -g @openai/codex`) 설치 필요
  - providers 섹션은 빈 객체 `{}` (codex-cli는 자체 인증 사용)

### OpenClaw on PRoot
- 출처: https://docs.openclaw.ai
- Node.js 22.12+ 필수 (apt install)
- npm install 시 ARM64에서 5-15분 소요
- **Bionic Bypass 필수**: PRoot 내 os.networkInterfaces() 크래시 방지
  - 참고: https://sagartamang.com/blog/openclaw-on-android-termux
- RAM 2-4GB 필요 — 구형폰 3GB에서는 불안정
- 4GB+ RAM 기기 전용 "고급" 옵션으로 배치
- onboard 개선 플래그: `--non-interactive --accept-risk --skip-channels --skip-health --skip-daemon`
  - `--skip-channels`: 채널은 `config set`으로 별도 처리
  - `--skip-health`: PRoot에서 health check 실패 방지
  - `--skip-daemon`: PRoot에서 systemd 불가
  - `--accept-risk`: non-interactive 모드 필수 플래그 (2026.02 추가됨)
- 성공 판단: exit code 대신 config 파일 존재+크기 확인 (onboard가 config 작성 후 daemon 단계에서 exit 1 반환할 수 있음)
- `reserveTokensFloor: 4000` 필수 (Groq Llama 모델의 context overflow 방지)
- **openai-codex OAuth 프로바이더**: ChatGPT 구독 인증으로 사용 가능 [실험]
  - 명령: `openclaw models auth login --provider openai-codex`
  - **주의**: headless 환경(PRoot)에서는 xdg-open 대신 stdout에 OAuth URL 출력
  - 반드시 `execWithStreaming()`으로 실행하여 실시간 URL 감지 필요
  - 일반 `exec()`의 `readText()`는 프로세스 종료까지 블로킹 → URL이 삼켜짐
  - 인증 성공 후 `openclaw.json`에 openai-codex 프로바이더가 추가됨

### Gemini API Key
- 출처: https://ai.google.dev/gemini-api/docs/api-key
- 형식: `AIzaSy` 접두사 + 총 39자 (영숫자 + `_` + `-`)
- 발급 URL: `https://aistudio.google.com/app/apikey`
- 검증: `GET https://generativelanguage.googleapis.com/v1beta/models?key={KEY}` → 200 OK
- 만료 없음 (OAuth refresh 불필요)
- Kotlin 검증: `key.startsWith("AIzaSy") && key.length == 39`

### Gemini 무료 티어 한도 (2026.02 기준)
- 출처: https://ai.google.dev/gemini-api/docs/rate-limits

| 모델 | RPM | RPD | TPM |
|------|-----|-----|-----|
| Gemini 2.5 Pro | 5 | 100 | 250,000 |
| Gemini 2.5 Flash | 10 | 250 | 250,000 |
| Gemini 2.5 Flash-Lite | 15 | 1,000 | 250,000 |

### Antigravity 경고
- 출처: https://docs.openclaw.ai/concepts/model-providers
- "Antigravity CLI"는 존재하지 않음. Antigravity = Google DeepMind AI IDE
- OpenClaw의 `google-antigravity-auth` 플러그인 = Antigravity IDE 할당량 빌려쓰기
- **사용 금지**: Google ToS 위반 위험, 계정 밴 보고, 주기적 API 버전 차단
- 대안: Gemini API Key 직접 발급 (안정적, 만료 없음)

### ChatGPT 구독 인증 (OAuth) — PicoClaw & OpenClaw 공통 [실험]

ChatGPT Plus/Pro 구독자($20+/월)가 추가 API 비용 없이 OpenAI 모델 사용 가능.
ServiceCatalog의 `chatgpt` 프로바이더 (`isOAuth = true`, `supportedServiceIds = emptyList()` = 양쪽 지원).

#### 서비스별 OAuth 메커니즘

| | PicoClaw | OpenClaw |
|---|---------|----------|
| 프로바이더명 | `codex-cli` (서브프로세스) | `openai-codex` (OAuth) |
| 인증 명령 | `codex auth login` | `openclaw models auth login --provider openai-codex` |
| 인증 파일 | `~/.codex/auth.json` | `~/.openclaw/openclaw.json` 내 프로바이더 |
| 의존성 | Node.js + Codex CLI | 없음 (OpenClaw 자체 지원) |
| Config model 값 | `"codex-cli"` | `"openai-codex/gpt-5.3-codex"` 등 |

#### ProotManager.execWithStreaming() 패턴 [실험]

OAuth 인증 명령이 stdout에 URL을 출력하므로, 실시간으로 읽어 Android 브라우저를 열어야 함.
일반 `exec()`는 `readText()`가 프로세스 종료까지 블로킹하여 URL이 삼켜짐.

```kotlin
// ProotManager.kt에 추가된 메서드
suspend fun execWithStreaming(
    vararg command: String,
    onLine: ((String) -> Unit)? = null,
    timeoutMs: Long = 180_000,
): ExecResult
```

- daemon 스레드로 stdout을 line-by-line 읽기
- `onLine` 콜백에서 OAuth URL 패턴 감지 → `context.startActivity(Intent.ACTION_VIEW)`
- `Process.waitFor(timeout, TimeUnit.MILLISECONDS)`로 타임아웃 지원
- OAuth 인증은 최대 3분(180초) 타임아웃 설정

#### OAuth URL 감지 패턴

```kotlin
private fun extractOAuthUrl(line: String): String? {
    val match = Regex("(https://\\S+)").find(line) ?: return null
    val url = match.groupValues[1].trimEnd(',', '.', ')', ']', '"', '\'')
    if (url.contains("auth.openai.com") || url.contains("login") || url.contains("authorize")) {
        return url
    }
    return null
}
```

#### PicoClaw ChatGPT 인증 흐름
```
1. Node.js 설치 확인 (codex CLI 의존성)
2. Codex CLI 설치 (`npm install -g @openai/codex`)
3. `codex auth login` — execWithStreaming으로 실행
4. stdout에서 OAuth URL 감지 → Android 브라우저 열기
5. 사용자가 ChatGPT 로그인 완료 → ~/.codex/auth.json 생성
6. PicoClaw config: provider="codex-cli", model="codex-cli"
```

#### OpenClaw ChatGPT 인증 흐름
```
1. `openclaw models auth login --provider openai-codex` — execWithStreaming으로 실행
2. stdout에서 OAuth URL 감지 → Android 브라우저 열기
3. 사용자가 ChatGPT 로그인 완료
4. OpenClaw config에 openai-codex 프로바이더 자동 추가
5. `openclaw config set model openai-codex/<model>` 로 모델 설정
```

### Auth Flow (복사-붙여넣기 방식) — API Key 인증
```
1. "API 키 받기" 탭 → Intent(ACTION_VIEW, "https://aistudio.google.com/app/apikey")
2. 사용자가 Google AI Studio에서 키 생성/복사
3. PocketEngine에 붙여넣기 → 형식 검증 (AIzaSy + 39자)
4. PRoot 파일시스템에 config 주입:
   - PicoClaw: /data/data/.../ubuntu-fs/root/.picoclaw/config.json
   - OpenClaw: /data/data/.../ubuntu-fs/root/.openclaw/openclaw.json
5. "BotFather 열기" → Intent(ACTION_VIEW, "https://t.me/BotFather")
6. Telegram 봇 토큰 붙여넣기
```

### 재설정 시 기존 값 프리필
- `readCurrentConfig()` 구현: 기존 config에서 api_key, telegram_token을 읽어 UI에 프리필
- PicoClaw: `config.json` → `providers.gemini.api_key`, `channels.telegram.token`
- OpenClaw: `openclaw.json` → 해당 필드
- 사용자가 "재설정" 시 기존 값이 입력 필드에 미리 표시되어 편의성 향상

### 경쟁 포지셔닝
- GoClaw (OpenClaw Cloud): **$39/월** — PocketEngine = **$0**
- openclaw-termux: 터미널 필수, keep-alive 없음
- ClawPhone: 음성통화 앱 (서버 운영 아님)

### Engine 신규 파일 (Phase 5)
```
catalog/
├── ServiceCatalog.kt        서비스 정의 + InputField/ProviderDef 스키마
├── PicoClawInstaller.kt     PicoClaw 설치/시작/중지 + codex-cli OAuth
└── OpenClawInstaller.kt     OpenClaw 설치/시작/중지 + openai-codex OAuth
ui/servicestore/
├── ServiceStoreScreen.kt    서비스 목록
├── ServiceSetupScreen.kt    API키/봇토큰 입력 위저드 + OAuth UI
└── ServiceStoreViewModel.kt 상태 관리 + isOAuthFlow 추적
bridge/
└── BrowserBridge.kt         FileObserver 기반 PRoot→Android 브라우저 열기 (보조)
service/
└── ServiceManager.kt        서비스 프로세스 관리
```

## User Flow

```
1. Play Store → PocketMonitor 설치 (독립 디바이스 모니터로 사용 가능)
2. 설정 > 서버 연동 > [자세히 보기] → 브라우저 → Firebase Hosting
3. Engine APK 다운로드 + 설치 (출처 알 수 없는 앱 허용)
4. Engine 실행 → 셋업 마법사 → 서버 설치 (원클릭)
5. Engine 닫기 (백그라운드 서버 계속 실행)
6. PocketMonitor에서 서버 자동 감지 → 상태 표시 + 제어
7. 매일 일일 리포트 푸시 → Monitor 열기 → 광고 노출
```

## Critical Rules

1. **Monitor app = Play Store safe**: NO PRoot, rootfs, SSH, keep-alive code. Ever.
2. **Engine app = sideload only**: NO ads, NO AdMob SDK.
3. **IPC = LocalSocket**: NO `<queries>` tag in Monitor manifest. No manifest traces.
4. **Single distro**: Ubuntu 24.04 LTS. No selection UI.
5. **SSH port 2022**: Dropbear, not OpenSSH.
6. **7-layer keep-alive**: Mandatory in Engine app.
7. **Temperature auto-stop 50C**: Non-negotiable.
8. **Manufacturer deep links**: Auto-detect in Engine app.
9. **Ad placement**: Monitor only. Banner + interstitial (open/exit).
10. **No IAP**: All features free. User acquisition first.
11. **Never download Engine from Monitor**: Always open browser to Firebase Hosting.
12. **Design**: Apple-style minimalism, Material 3, blue accent, green/yellow/red status.
13. **Version sync**: 양 앱 versionCode/versionName 항상 동일. Monitor가 Play Store에 등록되어 있으므로 단독 버전 변경 금지.
14. **Service Store = Engine only**: AI 비서 설치 기능은 Engine 앱에만 존재. Monitor에 Service Store 코드 없음.
15. **Antigravity OAuth 사용 금지**: Google ToS 위반 위험. Gemini API Key 직접 발급 방식만 사용.
16. **PicoClaw allow_from은 반드시 빈 배열 `[]`**: `["*"]`는 와일드카드로 동작하지 않음. exact match만 수행하므로 빈 배열이 "전체 허용"의 유일한 방법.
17. **Groq tool_use_failed는 상류 API 버그**: config 수정으로 해결 불가. Llama 모델의 간헐적 tool call 실패이며 Groq 측에서 수정해야 함.
18. **OAuth 인증 명령은 반드시 `execWithStreaming()` 사용**: `exec()`의 `readText()`는 프로세스 종료까지 블로킹하여 OAuth URL이 삼켜짐. `codex auth login`과 `openclaw models auth login` 모두 해당.
19. **Kotlin 블록 주석 내 `/*` 금지**: Kotlin은 중첩 블록 주석을 지원하므로 KDoc `/** */` 내에 `/*`가 있으면 컴파일 오류 발생. 예: `openai-codex/*` → `openai-codex/[model]`로 이스케이프.
20. **ChatGPT 구독 = ChatGPT Plus/Pro 필요**: OAuth 인증은 무료 ChatGPT 계정으로는 불가. 사용자에게 "$20/월 이상 구독 필요" 안내 필수.

## Signing Keystore (Shared Between Both Apps)

Both apps (PocketMonitor and PocketServer Engine) MUST be signed with the SAME keystore. This is required for LocalSocket IPC package signature verification -- if the signatures don't match, the Engine will reject connections from the Monitor.

### File Locations

| File | Path | Committed to Git |
|------|------|-----------------|
| Keystore (JKS) | `keystore/pocketserver-release.jks` | NO (gitignored) |
| Properties | `keystore/keystore.properties` | NO (gitignored) |

### keystore.properties Format

```properties
storePassword=<password>
keyPassword=<password>
keyAlias=pocketserver
storeFile=../keystore/pocketserver-release.jks
```

### Usage in build.gradle

Both `pocket-server/app/build.gradle` and `pocket-monitor/app/build.gradle` load the same `keystore.properties` via:
```groovy
def keystorePropertiesFile = rootProject.file("../keystore/keystore.properties")
```
The signing config is safely wrapped in an `if (keystorePropertiesFile.exists())` check so builds succeed even without the keystore (e.g., CI debug builds).

### IMPORTANT: Backup

The keystore file is gitignored and NEVER committed. If lost, you cannot update either app on users' devices (signature mismatch). Back up the keystore externally:
- Google Drive
- USB drive
- Password manager (1Password, Bitwarden)
- Multiple locations recommended

### Keystore Details

- Algorithm: RSA 2048-bit
- Validity: 10,000 days (~27 years)
- Alias: `pocketserver`
- DN: `CN=PocketServer, OU=Palank, O=Palank, L=Seoul, ST=Seoul, C=KR`

## Current Status

**Engine (pocket-server/)**: Phase 1-3.5 완료. versionCode 3, versionName "1.1.1"
**Monitor (pocket-monitor/)**: Phase 2-3.5 완료, Play Store 등록 완료. versionCode 3, versionName "1.1.1"
**IMPORTANT**: 양 앱 버전 동일 유지 필수. Monitor가 Play Store에 이미 등록되어 있으므로 버전 변경 시 양쪽 동시 업데이트.
**Next**: Phase 5 — Service Store (AI 비서 원클릭 설치)
