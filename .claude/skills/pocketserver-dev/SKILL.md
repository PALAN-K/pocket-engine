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

**Engine (pocket-server/)**: ~80% code exists from previous phases. Needs IPC server addition + UI trim (remove dashboard/ads).
**Monitor (pocket-monitor/)**: 0% — new project to be created.
**Next**: Phase 1 (Engine cleanup + IPC) → Phase 2 (Monitor new dev) → Phase 3 (Firebase Hosting) → Phase 4 (Integration test + launch)
