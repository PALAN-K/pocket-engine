# PocketServer — Development Task Anchor

> 다음 세션 시작 시 이 파일을 읽고 작업을 이어갈 것.
> 최종 갱신: 2026-02-09 (2-App 아키텍처 피벗)

## 현재 상태 요약

- **아키텍처**: 2-App (PocketMonitor + PocketServer Engine)
- **프로젝트 진행률**: ~65% (Phase 1 + Phase 2 완료)
- **Engine 상태**: Phase 1 완료 — IPC 서버 + 업데이트 체크 + AdMob 제거 + 빌드 성공 (14.4MB debug APK)
- **Monitor 상태**: Phase 2 완료 — 대시보드 + IPC + 알림 + AdMob + 빌드 성공 (16MB debug APK)
- **다음 작업**: Phase 3 — T3.1 Firebase Hosting 배포 + Phase 4 통합 테스트
- **GitHub**: https://github.com/naegeon/pocket-server.git (main)
- **Firebase**: `pocket-server-palank` (Crashlytics + Hosting)
- **서명 키스토어**: `keystore/pocketserver-release.jks` (양 앱 동일 키, gitignored)

## 아키텍처 개요

```
Google Play Store                    사이드로드 (Firebase Hosting)
┌──────────────────┐                ┌──────────────────┐
│  PocketMonitor    │                │  PocketServer     │
│  (kr.co.palank.   │  LocalSocket   │  Engine           │
│   pocketmonitor)  │◄──────────────►│  (kr.co.palank.   │
│                   │  Broadcast     │   pocketserver)   │
│  • 디바이스 모니터 │◄──────────────│                   │
│  • 서버 상태 표시  │                │  • PRoot + Ubuntu │
│  • 서버 제어 버튼  │                │  • Dropbear SSH   │
│  • AdMob 광고     │                │  • 7계층 Keep-Alive│
│  • 푸시 알림      │                │  • IPC 서버       │
│  • 광고 없음 불가  │                │  • 광고 없음       │
└──────────────────┘                └──────────────────┘
```

## 프로젝트 디렉토리 구조

```
android_linux/                     ← git root
├── pocket-server/                 ← Engine 앱 (기존 코드 재활용 + IPC 추가)
│   ├── app/google-services.json   ← Firebase 설정 (커밋됨)
│   └── app/src/main/java/kr/co/palank/pocketserver/
├── pocket-monitor/                ← Monitor 앱 (신규 생성 예정)
│   └── app/src/main/java/kr/co/palank/pocketmonitor/
├── firebase-hosting/public/       ← Engine APK 다운로드 페이지
├── keystore/                      ← 서명 키스토어 (gitignored)
├── docs/
│   ├── 01_BRAINSTORMING.md
│   ├── 02_PRD.md                  ← v2.0 (2-App 아키텍처)
│   └── 03_TASK_ANCHOR.md          ← 이 파일
├── firebase.json                  ← Firebase Hosting 설정
├── .firebaserc                    ← Firebase 프로젝트 연결
└── CLAUDE.md
```

## 기존 코드 재활용 현황 (Engine 앱)

기존 `pocket-server/`의 25개 파일 중 **Engine으로 재활용 가능한 파일**:

| 파일 | 재활용 | 변경 필요 |
|------|:------:|----------|
| ProotBinaryManager.kt | O | 그대로 사용 |
| ProotManager.kt | O | 그대로 사용 |
| InstallManager.kt | O | 그대로 사용 |
| DropbearManager.kt | O | 그대로 사용 |
| SessionManager.kt | O | 그대로 사용 |
| SwapManager.kt | O | 그대로 사용 |
| ServerForegroundService.kt | O | 그대로 사용 |
| WatchdogWorker.kt | O | 그대로 사용 |
| BootReceiver.kt | O | 그대로 사용 |
| ResourceMonitor.kt | O | 그대로 사용 |
| NetworkMonitor.kt | O | 그대로 사용 |
| BatteryMonitor.kt | O | 그대로 사용 |
| SpecChecker.kt | O | 그대로 사용 |
| ManufacturerDetector.kt | O | 그대로 사용 |
| ManufacturerOptimizationHelper.kt | O | 그대로 사용 |
| OptimizationGuide.kt | O | 그대로 사용 |
| MainActivity.kt | **수정** | 셋업 마법사만 남기고 대시보드 제거 |
| OnboardingScreen.kt | **수정** | 셋업 마법사로 변경 (SetupWizardScreen) |
| DashboardScreen.kt | **삭제** | Monitor 앱으로 이동 |
| DashboardViewModel.kt | **삭제** | Monitor 앱으로 이동 |
| SettingsScreen.kt | **삭제** | Monitor 앱으로 이동 |
| AdManager.kt | **삭제** | Monitor 앱으로 이동 |
| Color.kt / Theme.kt | **복사** | 양쪽 앱에서 공유 (복사) |

**신규 추가 (Engine)**:
- `ipc/IpcServer.kt` — LocalSocket 서버
- `ipc/AlertBroadcaster.kt` — Monitor로 알림 Broadcast

---

## Phase 1: Engine 앱 재구성 — "기존 코드 + IPC 추가"

> 기존 pocket-server 코드를 Engine 앱으로 정리하고, IPC 서버를 추가한다.

### T1.1 Engine 프로젝트 정리 ✅
- [x] MainActivity.kt 수정: 대시보드 네비게이션 제거, 셋업 마법사만 유지
- [x] OnboardingScreen.kt → SetupWizardScreen.kt 리네임/수정
- [x] DashboardScreen/ViewModel/SettingsScreen/AdManager 제거
- [x] build.gradle에서 AdMob SDK 의존성 제거
- [x] AndroidManifest.xml에서 AdMob 메타데이터 제거
- [x] 설치 완료 화면에 "PocketMonitor 설치 안내" + 안전 권장사항 추가
- [x] minSdk 24 → 26 (PRD 기준으로 수정)

### T1.2 IPC 서버 구현 ✅
- [x] `ipc/IpcServer.kt`: LocalServerSocket("pocketserver_ipc")
  - 상태 조회 프로토콜: JSON `{"cmd":"status"}` → `{"state":"running","cpu":12,"ram":62,...}`
  - 명령 프로토콜: `{"cmd":"start"}`, `{"cmd":"stop"}`, `{"cmd":"restart"}`
  - 패키지 서명 기반 인증 (양 앱 동일 서명)
- [x] `ipc/AlertBroadcaster.kt`: 온도 초과 / 서버 크래시 시 Broadcast 발송
  - Action: `kr.co.palank.pocketserver.ALERT`
  - Package: `kr.co.palank.pocketmonitor`
- [x] ServerForegroundService에서 IpcServer 시작/종료 통합

### T1.3 Firebase Crashlytics 통합 ✅ (빌드 성공, 실기기 테스트 대기)
- [x] Engine build.gradle에 Firebase Crashlytics SDK 추가
- [x] google-services.json 설정 (Firebase 프로젝트: `pocket-server-palank`)
- [ ] 크래시 로그 자동 수집 확인 (실기기 테스트 시 확인)

### T1.4 자동 업데이트 체크 ✅
- [x] Firebase Hosting에 version.json 배치: `firebase-hosting/public/version.json`
- [x] `util/UpdateChecker.kt`: 앱 실행 시 version.json 조회 → 현재 버전과 비교
- [x] 업데이트 가능 시 상단 배너 표시 → 탭하면 브라우저로 다운로드 페이지

### T1.5 빌드 검증 ✅
- [x] Engine APK 빌드 성공 확인 (debug: 14.4MB)
- [ ] 기존 서버 기능 (PRoot → Ubuntu → Dropbear) 정상 동작 확인 (실기기 테스트 시 확인)
- [x] AdMob SDK가 완전히 제거되었는지 확인 (grep 검증 통과)

---

## Phase 2: Monitor 앱 신규 개발 — "Play Store 앱"

> 신규 pocket-monitor 프로젝트 생성, 디바이스 모니터링 + Engine IPC 연동

### T2.1 프로젝트 생성 ✅
- [x] `pocket-monitor/` Gradle 프로젝트 생성
  - 패키지: `kr.co.palank.pocketmonitor`
  - minSdk 26, targetSdk 34
  - Jetpack Compose + Material3
  - AdMob SDK 23.6.0 의존성
  - WorkManager 2.9.1, lifecycle-process 2.6.2

### T2.2 독립 디바이스 모니터링 ✅
- [x] `monitor/DeviceMonitor.kt`: CPU/RAM/온도/저장공간/배터리 자체 모니터링 (2초 폴링)
- [x] `monitor/HistoryTracker.kt`: 24시간 히스토리 기록 (인메모리 링버퍼, 분당 1회, 1440 포인트)
- [x] `util/BatteryMonitor.kt`: 배터리 온도 모니터링 + 임계치 정의 (40/45/50°C)

### T2.3 대시보드 UI ✅
- [x] `ui/dashboard/DashboardScreen.kt`: 메인 대시보드
  - CPU/RAM/온도/저장공간 카드 (상태색 표시)
  - 24시간 온도 히스토리 그래프 (Canvas 기반 라인 차트)
  - Engine 연동 시: 서버 상태 카드 + SSH 정보 + 시작/중지/재시작 버튼
  - Engine 미연동 시: "서버 엔진 필요" 안내 카드 + Firebase Hosting 링크
  - 하단 AdMob 배너 (Scaffold bottomBar)
- [x] `ui/dashboard/DashboardViewModel.kt`: 상태 관리 (AndroidViewModel)
- [x] `ui/settings/SettingsScreen.kt`: 알림 설정 토글, 서버 연동 상태, 앱 정보
- [x] `ui/theme/`: Color.kt + Theme.kt (Engine에서 복사, PocketMonitor 패키지로 변경)

### T2.4 Engine IPC 클라이언트 ✅
- [x] `ipc/EngineConnector.kt`: LocalSocket 클라이언트
  - 2초 간격 상태 폴링 (handshake → status)
  - 시작/중지/재시작 명령 전송
  - 연결 실패 시 exponential backoff (2s→4s→8s→최대 30s)
  - 10회 연속 실패 시 "Engine 미연동" 상태 표시
- [x] `ipc/AlertReceiver.kt`: BroadcastReceiver
  - Engine에서 온도 경고 / 크래시 알림 수신 (4가지 타입)
  - AlertNotifier 호출하여 푸시 알림 생성

### T2.5 푸시 알림 시스템 ✅
- [x] `notification/DailyReportScheduler.kt`: OneTimeWorkRequest 매일 오전 9시
  - 실행 후 다음날 9시로 재스케줄 (드리프트 방지)
  - 디바이스 온도/배터리 리포트 알림 생성
- [x] `notification/WeeklyReportScheduler.kt`: OneTimeWorkRequest 매주 일요일 9시
  - 실행 후 다음 일요일로 재스케줄
- [x] `notification/AlertNotifier.kt`: 온도 경고/위험/크래시/재시작 알림 (4종)

### T2.6 AdMob 통합 ✅
- [x] `ad/AdManager.kt`: AdMob 관리
  - BannerAd 컴포저블 (AndroidView 래퍼, 수명주기 관리)
  - AppOpenAdManager (Application.ActivityLifecycleCallbacks + ProcessLifecycleOwner)
  - 앱 포그라운드 진입 시 App Open Ad 표시 (4시간 만료 관리)
  - 앱 이탈 시 광고 없음 (AdMob 정책 준수)
- [x] `PocketMonitorApp.kt`: Application 클래스 (MobileAds 초기화 + AppOpenAdManager)
- [x] AndroidManifest.xml에 AdMob APPLICATION_ID 메타데이터 (테스트 ID)
- [x] 테스트 광고 ID 사용 → 출시 전 실제 ID 교체

### T2.7 빌드 및 Play Store 준비
- [x] Monitor APK 빌드 성공 확인 (debug: 16MB)
- [ ] Play Store 리스팅 준비 (스크린샷, 설명, 카테고리)
- [ ] 개인정보 처리방침 작성

---

## Phase 3: Firebase Hosting — "Engine 배포"

### T3.1 Firebase 프로젝트 설정
- [x] Firebase 프로젝트 생성 (`pocket-server-palank`)
- [x] Firebase Hosting 설정 (firebase.json + .firebaserc)
- [ ] 커스텀 도메인 설정 (선택)
- [ ] `firebase deploy --only hosting` 으로 첫 배포

### T3.2 배포 웹페이지
- [x] Engine APK 다운로드 페이지 HTML (`firebase-hosting/public/index.html`)
  - 다운로드 버튼
  - 설치 가이드 (출처를 알 수 없는 앱 허용 방법)
  - FAQ
- [ ] Engine APK 업로드 (`firebase-hosting/public/pocketserver-engine.apk`)
- [ ] 배포 URL 확정 (`pocket-server-palank.web.app`)

---

## Phase 4: 통합 테스트 + 출시

### T4.1 통합 테스트
- [ ] Engine 설치 → Monitor 자동 감지 → IPC 통신 확인
- [ ] 푸시 알림 (일일 리포트, 온도 경고) 동작 확인
- [ ] 광고 노출 확인 (배너 + 전면 진입/이탈)
- [ ] 실기기 5~10대 테스트

### T4.2 출시
- [ ] Monitor → Play Store 등록
- [ ] Engine → Firebase Hosting 공개
- [ ] 실제 AdMob ID 교체 (테스트 → 프로덕션)

---

## 작업 시작 가이드

```
1. 이 파일 읽기
2. PRD v2.0 (02_PRD.md) 읽기
3. SKILL.md 읽기
4. 기존 pocket-server/ 코드 확인
5. Phase 순서대로 작업
6. 완료된 태스크에 [x] 체크
```
