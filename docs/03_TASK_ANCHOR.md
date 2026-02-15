# PocketServer — Development Task Anchor

> 다음 세션 시작 시 이 파일을 읽고 작업을 이어갈 것.
> 최종 갱신: 2026-02-15 (Phase 4 완료, 프로덕션 출시 대기)

## 현재 상태 요약

- **아키텍처**: 2-App (PocketMonitor + PocketEngine)
- **프로젝트 진행률**: ~98% (Phase 1 ~ Phase 4 완료, 프로덕션 출시만 남음)
- **Engine 상태**: Phase 1 완료 + UI 통일 — versionCode 3, versionName "1.1.1", 방패+톱니바퀴 아이콘
- **Monitor 상태**: Phase 2~4 완료 — Play Store 테스트 배포 완료, 프로덕션 AdMob ID 적용 완료, versionCode 3, versionName "1.1.1"
- **배포 상태**: Phase 3 완료 — Firebase Hosting + Cloudflare R2 배포 완료
- **포지셔닝**: "무인 디바이스 가디언" — 24/7 가동 디바이스 헬스 관리 앱 (CCTV, 베이비모니터, 스마트홈 허브, 서버 등)
- **다음 작업**: Play Store 프로덕션 출시 → Phase 5 (Service Store)
- **GitHub**: https://github.com/naegeon/pocket-server.git (main)
- **Firebase**: `pocket-server-palank` (Crashlytics + Hosting)
- **Cloudflare R2**: `pocketserver-apk` 버킷 (Engine APK 호스팅)
- **서명 키스토어**: `keystore/pocketserver-release.jks` (양 앱 동일 키, gitignored)

## 배포 URL

| 리소스 | URL |
|--------|-----|
| Engine 다운로드 페이지 | https://pocket-server-palank.web.app |
| Engine APK (R2) | https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk |
| version.json (Hosting) | https://pocket-server-palank.web.app/version.json |
| version.json (R2) | https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/version.json |
| 개인정보 처리방침 (한국어) | https://pocket-server-palank.web.app/privacy-ko.html |
| 개인정보 처리방침 (English) | https://pocket-server-palank.web.app/privacy-en.html |

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
├── pocket-monitor/                ← Monitor 앱 (Phase 2 완료)
│   └── app/src/main/java/kr/co/palank/pocketmonitor/
├── firebase-hosting/public/       ← 다운로드 페이지 + 개인정보 처리방침
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

### T1.3 Firebase Crashlytics 통합 ✅
- [x] Engine build.gradle에 Firebase Crashlytics SDK 추가
- [x] google-services.json 설정 (Firebase 프로젝트: `pocket-server-palank`)
- [x] 크래시 로그 자동 수집 확인 (FirebaseInitProvider 자동 초기화)

### T1.4 자동 업데이트 체크 ✅
- [x] Firebase Hosting에 version.json 배치: `firebase-hosting/public/version.json`
- [x] `util/UpdateChecker.kt`: 앱 실행 시 version.json 조회 → 현재 버전과 비교
- [x] 업데이트 가능 시 상단 배너 표시 → 탭하면 브라우저로 다운로드 페이지

### T1.5 빌드 검증 ✅
- [x] Engine APK 빌드 성공 확인 (debug: 14.4MB)
- [x] 기존 서버 기능 (PRoot → Ubuntu → Dropbear) 정상 동작 확인 (S8 실기기 검증 완료)
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
- [x] AndroidManifest.xml에 AdMob APPLICATION_ID 메타데이터 (프로덕션 ID 적용 완료)
- [x] 프로덕션 AdMob 광고 ID 적용 완료 (ca-app-pub-8839719247481278)

### T2.7 빌드 및 Play Store 준비 ✅
- [x] Monitor APK 빌드 성공 확인 (debug: 16MB)
- [x] Play Store 리스팅 준비 (스크린샷 10개, 리스팅 텍스트 한/영, 기능 그래픽)
- [x] 개인정보 처리방침 작성 (한국어 + 영어, Firebase Hosting 배포 완료)

---

## Phase 3: Firebase Hosting + Cloudflare R2 — "Engine 배포" ✅

### T3.1 Firebase 프로젝트 설정 ✅
- [x] Firebase 프로젝트 생성 (`pocket-server-palank`)
- [x] Firebase Hosting 설정 (firebase.json + .firebaserc)
- [ ] 커스텀 도메인 설정 (선택, 미진행)
- [x] `firebase deploy --only hosting` 배포 완료

### T3.2 배포 웹페이지 ✅
- [x] Engine APK 다운로드 페이지 HTML (`firebase-hosting/public/index.html`)
  - 다운로드 버튼 → Cloudflare R2 APK URL로 연결
  - 설치 가이드 (출처를 알 수 없는 앱 허용 방법)
  - FAQ
- [x] Engine release APK 빌드 (11MB) + Cloudflare R2 업로드
  - Firebase Spark 무료 플랜에서 APK(실행 파일) 업로드 불가 → Cloudflare R2로 대체
  - R2 버킷: `pocketserver-apk` (퍼블릭 접근 활성화)
  - APK URL: `https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk`
- [x] 배포 URL 확정: `pocket-server-palank.web.app` (다운로드 페이지) + R2 (APK)
- [x] 개인정보 처리방침 배포 (privacy-ko.html + privacy-en.html)

---

## Phase 3.5: UI 개선 + Play Store 포지셔닝 ✅

> "무인 디바이스 가디언" 포지셔닝 적용 + Apple 스타일 UI 전면 개선

### T3.5.1 Monitor 대시보드 UI 개선 ✅
- [x] 디바이스 건강 점수 게이지 (0-100, 270° 원형 아크, animateFloatAsState)
  - 등급: 우수(90+)/양호(70+)/주의(50+)/위험(50 미만)
  - 가중치: 온도 40%, CPU 20%, RAM 20%, 저장공간 10%, 배터리 10%
- [x] 메트릭 카드에 Material 아이콘 + 원형 프로그레스 링 (CPU/메모리)
- [x] 카드 좌측 상태색 accent bar (4dp, 초록/노랑/빨강)
- [x] 배터리 상세 카드 (잔량, 전압, 충전상태, 충전타입, 온도)
- [x] 히스토리 차트 탭 전환 (온도/CPU/메모리 FilterChip)
- [x] 차트 그라데이션 fill + 경고선 (45°C/50°C 점선)
- [x] DashboardHeader에 상태 dot (초록/노랑/빨강)
- [x] DeviceMonitor에 isCharging, chargingType, voltage 필드 추가

### T3.5.2 설정 화면 개선 ✅
- [x] DataStore 영속화 연동 (SettingsDataStore.kt 신규 생성)
- [x] 알림 토글 → DataStore에서 Flow로 읽기/쓰기 (앱 재시작 후 유지)
- [x] 오픈소스 라이선스 AlertDialog (7개 라이브러리 목록)
- [x] 개인정보 처리방침 링크 (브라우저로 열기)
- [x] "서버 연동" → "확장 기능"으로 변경 (중립적 문구)
- [x] Engine 미연결 시: "PocketEngine 확장 도구" + "자세히 보기" (대시보드에서 제거)

### T3.5.3 Play Store 포지셔닝 ✅
- [x] 대시보드에서 Engine 미연동 카드 완전 제거 → 순수 디바이스 모니터링 앱으로 보임
- [x] Engine 안내는 설정 > 확장 기능에만 배치
- [x] "무인 디바이스 가디언" 콘셉트: CCTV, 베이비모니터, 스마트홈 허브, 서버 등 24/7 가동 디바이스 대상

### T3.5.4 디자인 통일 ✅
- [x] Monitor 앱 아이콘: 방패 + 하트비트 펄스 (파란 배경)
- [x] Engine 앱 아이콘: 방패 + 톱니바퀴 (파란 배경, 동일 모티프)
- [x] Engine "PocketServer" → "PocketEngine" 리네이밍
- [x] Engine 사양 검사 Row를 Card로 래핑 (16dp radius, 2dp elevation)
- [x] Engine 모든 카드 elevation 0dp → 2dp (Monitor와 통일)

### T3.5.5 빌드 검증 ✅
- [x] Monitor APK 빌드 성공 + 에뮬레이터 정상 동작 확인
- [x] Engine APK 빌드 성공 + 에뮬레이터 정상 동작 확인
- [x] 스크린샷 확인: 대시보드, 설정, Engine 셋업 마법사

---

## Phase 4: 통합 테스트 + 출시 ✅ (프로덕션 출시만 남음)

### T4.1 실기기 통합 테스트 ✅
- [x] Engine 설치 → Monitor 자동 감지 → IPC 통신 확인
- [x] 푸시 알림 (일일 리포트, 온도 경고) 동작 확인
- [x] 광고 노출 확인 (배너 + App Open Ad)
- [x] 설정 토글 영속화 확인 (변경 → 앱 종료 → 재실행 → 유지)
- [x] 실기기 테스트 (Galaxy S8, Lenovo Tab 등)
- [x] Engine 서버 설치 → Dropbear SSH 접속 확인

### T4.2 Play Store 등록 준비 ✅
- [x] Play Store 리스팅 작성
  - [x] 앱 이름, 카테고리, 짧은 설명, 긴 설명
  - [x] 스크린샷 준비 (폰 + 태블릿, 10개 PNG)
  - [x] 기능 그래픽 (1024x500)
  - [x] 앱 아이콘 (512x512 PNG)
- [x] 개인정보 처리방침 URL 등록: `https://pocket-server-palank.web.app/privacy-ko.html`
- [x] 데이터 안전 섹션 작성 (수집 데이터, 사용 목적)
- [x] 콘텐츠 등급 설문 작성
- [x] Play Store 테스트 트랙 배포 완료

### T4.3 출시 (프로덕션 출시만 남음)
- [x] 프로덕션 AdMob 광고 단위 ID 적용 완료
  - [x] Monitor: AdManager.kt 내 배너 + App Open Ad ID (ca-app-pub-8839719247481278)
  - [x] Monitor: AndroidManifest.xml 내 APPLICATION_ID
- [x] Release APK 빌드 (서명 키스토어 사용)
  - [x] Monitor release APK
  - [x] Engine release APK (Cloudflare R2 업로드 완료)
- [ ] **Play Store 프로덕션 출시** (테스트 → 프로덕션 트랙 승격)
- [x] Firebase Hosting 재배포 (Engine 다운로드 페이지 최신화)

---

## Phase 5: Service Store — AI 비서 원클릭 설치 (예정)

> Engine 앱 내에서 PicoClaw/OpenClaw를 원클릭 설치하는 기능. 추후 서비스 확장 시 진행.
> 상세 설계: SKILL.md > "Service Store" 섹션 + references/service-store-guide.md

### T5.1 Service Store UI
- [ ] `ui/servicestore/ServiceStoreScreen.kt`: 서비스 목록 화면
- [ ] `ui/servicestore/ServiceSetupScreen.kt`: API 키 / 봇 토큰 입력 위저드
- [ ] `ui/servicestore/ServiceStoreViewModel.kt`: 상태 관리
- [ ] 서버 설치 완료 후 "AI 비서 설치" 카드 표시

### T5.2 PicoClaw Installer (1순위)
- [ ] `catalog/PicoClawInstaller.kt`: Go 바이너리 다운로드 + config 주입
- [ ] Gemini API Key 입력 + 형식 검증 (AIzaSy + 39자)
- [ ] Telegram 봇 토큰 입력 + 형식 검증
- [ ] PRoot 내 자동 시작 설정 (autostart.sh)

### T5.3 OpenClaw Installer (2순위, 4GB+ RAM 전용)
- [ ] `catalog/OpenClawInstaller.kt`: Node.js + npm install + Bionic Bypass
- [ ] RAM 사양 검사 (4GB 미만 시 경고)
- [ ] 설치 시간 안내 (5-15분)

### T5.4 서비스 프로세스 관리
- [ ] `service/ServiceManager.kt`: 서비스 시작/중지/상태 관리
- [ ] IPC 확장: `{"cmd":"service_status"}` 명령 추가
- [ ] Monitor 대시보드에 서비스 상태 표시

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
