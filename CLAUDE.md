# PocketServer - Project Constitution

## Identity
- **2-App Architecture**: PocketMonitor (Play Store) + PocketServer Engine (Sideload)
- PocketMonitor: 디바이스 헬스 모니터링 + 서버 상태 대시보드 + AdMob 광고
- PocketServer Engine: 원클릭 Linux SSH 서버 (Ubuntu 24.04, Dropbear SSH port 2022)
- Engine은 Firebase Hosting에서 APK 사이드로드 배포
- 전 기능 무료, IAP 없음, 사용자 확보 최우선
- Target: 바이브코더/AI Agent 입문자, Apple-style minimal UI

## Absolute Rules
1. NEVER write code from internal knowledge first. ALWAYS search in this order:
   a. `.claude/skills/pocketserver-dev/` (skill + references)
   b. Official docs (developer.android.com, UserLand/Termux GitHub)
   c. Open source examples and Stack Overflow
   d. Internal knowledge (last resort only)
2. NEVER run subagents in parallel. ALL Task tool calls must be sequential (one at a time).
3. NEVER add VNC, desktop environments, or multiple distro selection.
4. NEVER skip the 7-layer keep-alive system in Engine app. All layers are mandatory.
5. NEVER place ads in Engine app. Ads are Monitor app only.
6. NEVER let Monitor app contain PRoot, rootfs, SSH, or keep-alive code.
7. Temperature auto-stop at 50C is non-negotiable (Engine app).
8. NEVER download Engine APK from within Monitor app. Always open browser.
9. IPC uses LocalSocket (no manifest traces). NO <queries> tag in Monitor app.

## Architecture
```
PocketMonitor (Play Store)     PocketServer Engine (Sideload)
├── DeviceMonitor              ├── PRoot + Ubuntu 24.04
├── EngineConnector (IPC)  ←→  ├── IpcServer (LocalSocket)
├── AlertReceiver          ←   ├── AlertBroadcaster
├── DailyReportScheduler       ├── Dropbear SSH :2022
├── AdManager (AdMob)          ├── 7-Layer Keep-Alive
└── Dashboard UI               └── SetupWizard UI
```

## Tech Stack
- Language: Kotlin + Jetpack Compose (both apps)
- Min SDK: 26 (Android 8.0), Target SDK: 34+
- Architecture: MVVM (ViewModel + StateFlow)
- IPC: LocalSocket + BroadcastReceiver
- Ads: Google AdMob (Monitor only) — banner + App Open Ad (진입 시만, 이탈 시 없음)
- Server: PRoot + Dropbear SSH (Engine only)
- Distribution: Firebase Hosting (Engine APK)

## Key References
- PRD v2.0: @docs/02_PRD.md
- Brainstorming: @docs/01_BRAINSTORMING.md
- Task Anchor: @docs/03_TASK_ANCHOR.md
- Skill: @.claude/skills/pocketserver-dev/SKILL.md
- UserLand source: github.com/CypherpunkArmory/UserLAnd
- Termux keep-alive: github.com/termux/termux-app (TermuxService.java)
- Manufacturer kill: dontkillmyapp.com

## Code Style
- Kotlin idioms, coroutines for async, StateFlow for UI state
- Composable functions prefixed with screen name (e.g., DashboardScreen)
- No unnecessary comments, no over-engineering
- Test on real devices (not just emulator) — especially keep-alive and IPC

## Projects
- **Monitor**: `pocket-monitor/` (패키지: `kr.co.palank.pocketmonitor`)
- **Engine**: `pocket-server/` (패키지: `kr.co.palank.pocketserver`)

## Current Status
- **Progress**: ~15% (기존 Engine 코드 재활용 가능, Monitor 신규 개발 필요)
- **Next**: Phase 1 — Engine 프로젝트 정리 + IPC 서버 추가
- **Task Anchor**: @docs/03_TASK_ANCHOR.md ← 세션 시작 시 반드시 읽을 것
- **GitHub**: https://github.com/naegeon/pocket-server.git

## Build
- JAVA_HOME: `D:\Androidstudio\jbr` — Android Studio 내장 JBR 사용
- 빌드: `export JAVA_HOME="/d/Androidstudio/jbr" && cd pocket-server && ./gradlew assembleDebug`
- 실기기 설치: `adb install -r pocket-server/app/build/outputs/apk/debug/app-debug.apk` (ADB: `C:\Users\jayeo\AppData\Local\Android\Sdk\platform-tools`)

## Git
- Branch: feature/*, fix/*, refactor/*
- Commits: imperative mood, concise, reference PRD FM/FE numbers
- Korean comments OK, code/variables in English
