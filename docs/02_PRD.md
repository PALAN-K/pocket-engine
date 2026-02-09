# PocketServer - Product Requirements Document (PRD)

> 버전: 2.0
> 작성일: 2026-02-09 (v2.0 전면 개편)
> 상태: MVP 재정의 — 2-App 아키텍처

---

## 1. 제품 개요

### 1.1 제품 구성
PocketServer는 **2개의 독립 앱**으로 구성됩니다.

| 앱 | 배포 채널 | 역할 |
|----|----------|------|
| **PocketMonitor** | Google Play Store | 디바이스 헬스 모니터링 + 서버 상태 대시보드 + AdMob 광고 |
| **PocketServer Engine** | 사이드로드 (Firebase Hosting) | 원클릭 리눅스 서버 설치 + 24/7 백그라운드 가동 |

### 1.2 한 줄 설명
구형 스마트폰을 나만의 리눅스 서버로 변환하는 원클릭 Android 앱 시스템

### 1.3 태그라인
> "서랍 속 스마트폰이 나만의 서버가 됩니다"

### 1.4 왜 2개 앱인가? (Play Store 정책 대응)
Google Play Store는 다음을 금지합니다:
- 런타임에 실행 가능한 코드 다운로드 (rootfs)
- PRoot 등 샌드박스 탈출 바이너리 번들
- 무기한 Wake Lock + 7계층 Keep-Alive
- specialUse FGS로 리눅스 서버 운영

**해결책**: 정책 위반 코드를 Engine 앱(사이드로드)에 격리하고, Monitor 앱(Play Store)은 순수 모니터링 + 광고 수익화 전담.

**선례**: AndroNix (Play Store에서 스크립트 생성, 실행은 Termux 사이드로드)

### 1.5 목표
- 비개발자도 구형 스마트폰을 리눅스 서버로 만들 수 있게 한다
- 서버가 24시간 안정적으로 동작하도록 보장한다
- 매일 안전 점검 리포트를 통해 사용자 참여를 유지한다
- 사용자 확보가 최우선 — 전 기능 무료, 유료 기능 없음

### 1.6 핵심 가치
| 가치 | 설명 |
|------|------|
| 간편함 | Engine 앱에서 버튼 하나로 서버 구축 완료 |
| 경제성 | 구형폰 활용, 추가 하드웨어 비용 $0, 앱 완전 무료 |
| 안정성 | 화면 꺼져도 서버 유지, 재부팅 시 자동 시작 |
| 안전성 | 매일 서버 리포트 + 온도 경고로 화재 위험 사전 차단 |
| 범용성 | OpenClaw, n8n, Dify, 바이브코딩 웹앱 등 어떤 서비스든 설치 가능 |

### 1.7 베이스 프로젝트
- **Greenfield 프로젝트** (처음부터 자체 개발, UserLand는 아키텍처 참고용)
- 참고 프로젝트: UserLand (MIT 라이선스) - github.com/CypherpunkArmory/UserLAnd
- 프로젝트 위치:
  - Monitor: `pocket-monitor/` (패키지: `kr.co.palank.pocketmonitor`)
  - Engine: `pocket-server/` (패키지: `kr.co.palank.pocketserver`)

---

## 2. 사용자 정의

### 2.1 타겟 페르소나

**페르소나 A: AI Agent 입문자**
- 30대, 비개발 직장인
- ChatGPT는 잘 쓰지만 터미널은 처음
- OpenClaw를 써보고 싶지만 설치 방법을 모름
- Galaxy S9이 서랍에 방치 중
- VPS에 월 $5 쓰기는 아까움
- APK 사이드로드 정도는 할 수 있음

**페르소나 B: 바이브코더**
- 20대~30대, 개발에 관심 있는 비전공자
- AI로 웹앱을 만들었지만 배포할 서버가 없음
- Vercel/Netlify 무료 티어는 제한적
- 구형폰을 개인 서버로 쓰고 싶음
- 사이드로드 경험 있음

### 2.2 사용자 전제 조건
- APK 사이드로드(출처를 알 수 없는 앱 설치) 가능
- 기본적인 WiFi 연결 능력
- SSH 클라이언트 설치 의지 (PC에서 접속 시)

### 2.3 최소 하드웨어 사양

| 항목 | 최소 | 권장 |
|------|------|------|
| Android 버전 | 8.0 (Oreo) | 10.0+ |
| RAM | 3GB | 4GB+ |
| 저장공간 | 8GB 여유 | 16GB+ 여유 |
| CPU | ARM64 (64비트) | - |
| 네트워크 | WiFi 연결 | 5GHz WiFi |

---

## 3. 기능 요구사항 — App A: PocketMonitor (Play Store)

> PocketMonitor는 **독립적 가치가 있는 디바이스 헬스 모니터**입니다.
> Engine 없이도 디바이스 모니터링 앱으로 동작합니다.
> Engine 감지 시 서버 상태 + 제어 기능이 추가됩니다.

### 3.1 독립 기능 (Engine 없이도 동작)

#### FM-001: 디바이스 헬스 대시보드
- CPU 사용률 실시간 표시
- RAM 사용량 (사용 중 / 전체)
- 저장공간 사용량
- 배터리 잔량 + 온도
- 24시간 히스토리 그래프 (온도, CPU, RAM)

#### FM-002: 온도 모니터링 + 알림
- Android BatteryManager API 실시간 온도 모니터링
- 경고 임계값:
  - 40C: 정상 (초록)
  - 45C: 주의 (노랑) + 푸시 알림
  - 50C: 위험 (빨강) + 긴급 푸시 알림
- 온도 이력 그래프 (최근 24시간)

#### FM-003: 일일 서버 리포트 (푸시 알림)
- 매일 오전 9시 정기 리포트 발송:
  ```
  📊 PocketMonitor 일일 리포트
  가동시간: 24h | 평균 온도: 38°C | CPU: 12% | RAM: 62%
  ```
- 푸시 알림 탭 → 앱 열기 → 대시보드 확인 → 광고 노출
- 서버 운영 중 화재 위험 예방을 위한 **필수 안전 점검**

#### FM-004: 주간 요약 리포트
- 매주 일요일 주간 요약:
  ```
  📈 이번 주 서버 리포트
  가동률: 99.8% | 최고 온도: 43°C | 평균 CPU: 15%
  ```

### 3.2 서버 연동 기능 (Engine 감지 시 활성화)

#### FM-010: Engine 앱 감지
- LocalSocket으로 Engine 앱 존재 여부 자동 감지
- Engine 미설치 시: 디바이스 모니터링만 표시
- Engine 설치 시: 서버 상태 + 제어 UI 추가 표시

#### FM-011: 서버 상태 대시보드
- Engine으로부터 IPC로 수신하는 정보:
  - 서버 상태 (Running / Stopped / Error)
  - 가동 시간 (uptime)
  - SSH 접속 정보 (IP:포트, 사용자명)
- 클립보드 복사 버튼 (SSH 접속 명령어)

#### FM-012: 서버 제어
- 서비스 시작 / 중지 / 재시작 버튼
- Engine 앱으로 IPC 명령 전송
- UI 표현은 범용적: "서비스 시작", "서비스 중지" (Linux/Server 직접 언급 최소화)

#### FM-013: Engine 미설치 안내
- 설정 > 서버 연동 메뉴
- "서버 기능을 사용하려면 PocketServer Engine이 필요합니다"
- [자세히 보기] 버튼 → 브라우저에서 Firebase Hosting 페이지 열기
- **앱 내에서 직접 다운로드하지 않음** (브라우저로 이동만)

### 3.3 광고 (AdMob)

#### FM-020: 배너 광고
- 대시보드 하단에 배너 광고 1개 상시 표시
- 대시보드 화면에서만 표시 (설정/온보딩 화면에는 미표시)

#### FM-021: App Open Ad (앱 실행 시 전면 광고)
- **앱 실행 시**: 스플래시 후 대시보드 진입 전 App Open Ad 1회 (AdMob 공식 권장 형식)
- **앱 이탈 시 광고 없음**: AdMob 정책상 사용자 액션 없는 전면광고 금지
- 서버 운영에 절대 영향 없음 (광고는 Monitor 앱에만 존재)

---

## 4. 기능 요구사항 — App B: PocketServer Engine (사이드로드)

> Engine은 **셋업 마법사 + 백그라운드 서버 엔진**입니다.
> 최초 설치 시 1회 사용 후, 이후에는 백그라운드에서만 동작합니다.
> 광고 없음. 유료 기능 없음.

### 4.1 원클릭 서버 설치

#### FE-001: 사양 검사
- 앱 시작 시 기기의 RAM, 저장공간, CPU 아키텍처, Android 버전 자동 검사
- 최소 사양 미달 시 구체적 사유와 함께 설치 불가 안내
- 사양 충족 시 "서버 설치" 버튼 활성화

#### FE-002: 리눅스 설치
- PRoot 기반 Ubuntu 24.04 LTS (ARM64) 자동 설치
- 배포판 선택 없음 (Ubuntu 24.04 LTS 고정)
- 설치 진행률 프로그레스바 표시 (단계별)
- 설치 단계:
  1. PRoot 환경 초기화 (앱 번들 assets 활용)
  2. Ubuntu 24.04 LTS rootfs 다운로드 및 압축 해제
  3. 기본 시스템 설정 (locale, timezone)
  4. 사용자 계정 생성 (자동 비밀번호 생성)
  5. 스왑 메모리 설정 (2GB)
  6. Dropbear SSH 서버 설정 (포트 2022)

#### FE-003: 스왑 메모리 설정
- RAM 기반 자동 스왑 크기: 2GB (고정)
- 스왑 파일 생성 및 활성화 자동 처리

#### FE-004: SSH 서버 설정 (Dropbear)
- PRoot 내 apt로 설치하는 경량 Dropbear SSH 서버
- 기본 SSH 포트: 2022
- 비밀번호 인증 활성화 (자동 생성 12자리)
- 서비스 자동 시작 설정

#### FE-005: 설치 완료 화면
- SSH 접속 정보 표시 (IP, 포트, 사용자, 비밀번호)
- 클립보드 복사 버튼
- "PocketMonitor를 설치하면 매일 서버 상태를 확인할 수 있습니다" 안내
- Play Store PocketMonitor 링크 제공

### 4.2 백그라운드 상시 가동 (7계층 시스템)

#### FE-010: Layer 1 - Foreground Service
- 서버 시작 시 Foreground Service 활성화
- 상태바 알림: "PocketServer 실행 중 | 38°C"

#### FE-011: Layer 2 & 3 - Wake Lock & WiFi Lock
- Partial Wake Lock (CPU 유지)
- WiFi Lock WIFI_MODE_FULL_HIGH_PERF (네트워크 유지)

#### FE-012: Layer 4 - 배터리 최적화 제외
- 최초 실행 시 배터리 최적화 제외 요청

#### FE-013: Layer 5 - 자동 재시작
- START_STICKY + onTaskRemoved 재시작

#### FE-014: Layer 6 - 부팅 시 자동 시작
- BOOT_COMPLETED BroadcastReceiver

#### FE-015: Layer 7 - 제조사별 최적화
- 제조사 감지 + 딥링크 안내 (Samsung/Xiaomi/Huawei/OPPO)

### 4.3 IPC 제공 (Monitor 앱 연동)

#### FE-020: LocalSocket IPC 서버
- `LocalServerSocket("pocketserver_ipc")` 상시 리스닝
- Monitor 앱의 상태 조회 요청에 응답
- Monitor 앱의 제어 명령(시작/중지/재시작) 수신 및 실행

#### FE-021: Broadcast 알림 발송
- 온도 45°C 초과 시 Monitor 앱으로 Broadcast
- 서버 비정상 종료 시 Monitor 앱으로 Broadcast
- Monitor 앱이 이를 수신하여 푸시 알림 생성

### 4.4 서버 제어

#### FE-030: 서버 시작/중지
- PRoot 리눅스 부팅 → Dropbear SSH 서버 시작
- 중지 시 프로세스 정리 종료

#### FE-031: 서버 초기화
- 리눅스 환경 전체 삭제 + 재설치
- 2차 확인 다이얼로그 필수

### 4.5 안전 기능

#### FE-040: 온도 자동 정지
- 50°C 도달 시 서버 자동 일시정지
- 45°C 이하 복귀 시 자동 재시작
- Monitor 앱으로 긴급 알림 Broadcast

#### FE-041: 자동 업데이트 체크
- 앱 실행 시 Firebase Hosting의 버전 파일(version.json) 확인
- 최신 버전이 있으면 "업데이트 가능" 배너 표시
- 배너 탭 → 브라우저에서 Firebase Hosting 다운로드 페이지 열기
- 강제 업데이트 아님 (사용자 선택)

#### FE-042: 크래시 리포팅 (Firebase Crashlytics)
- Firebase Crashlytics SDK 통합
- 비식별화된 크래시 로그 자동 수집
- PRoot/Dropbear 관련 크래시 추적
- 사이드로드 앱이므로 Play Console 크래시 리포트 불가 → Crashlytics 필수

#### FE-043: 안전 권장사항 (설치 완료 시 표시)
- 스마트플러그 연동 권장 (70~80% 충전 제한)
- 폰 케이스 제거 권고
- 금속판/타일 위 배치 권고
- 월 1회 배터리 팽창 점검 안내

---

## 5. IPC (앱 간 통신) 아키텍처

### 5.1 통신 방식

```
PocketMonitor (Play Store)          PocketServer Engine (Sideload)
┌──────────────────────┐            ┌──────────────────────┐
│                      │            │                      │
│  StatusPoller ───────┼─LocalSocket┼──► IpcServer         │
│  (2초 간격 폴링)      │            │    (상태 응답)        │
│                      │            │                      │
│  CommandSender ──────┼─LocalSocket┼──► CommandHandler    │
│  (시작/중지/재시작)    │            │    (명령 실행)        │
│                      │            │                      │
│  AlertReceiver ◄─────┼─Broadcast──┼──  AlertBroadcaster │
│  (온도/크래시 알림)    │            │    (이벤트 발생 시)   │
│                      │            │                      │
└──────────────────────┘            └──────────────────────┘
```

### 5.2 왜 LocalSocket인가?
- **매니페스트에 흔적 없음**: `<queries>` 태그 불필요 → Play Store 심사에서 Engine 앱 참조 미노출
- 양방향 통신 가능
- Android 표준 API
- Broadcast는 긴급 알림용 보조 채널

### 5.3 보안
- 양 앱이 같은 개발자 키로 서명
- LocalSocket 연결 시 토큰 기반 인증 (Engine 최초 실행 시 랜덤 토큰 생성, SharedPreferences 저장)
- Monitor는 토큰을 사용자에게 1회 입력받거나, ContentProvider(signature 보호)로 교환

---

## 6. UI/UX 요구사항

### 6.1 디자인 원칙
- **Apple 스타일 미니멀리즘**: 깔끔하고 직관적인 인터페이스
- **정보 밀도 최소화**: 한 화면에 핵심 정보만 표시
- **색상 시스템**:
  - 배경: 화이트/다크그레이 (다크모드 지원)
  - 액센트: 블루 계열
  - 상태: 초록(정상), 노랑(주의), 빨강(위험)
- **광고 배치 원칙**: Monitor 앱 대시보드 하단 배너, 앱 실행/이탈 시 전면 광고

### 6.2 PocketMonitor 화면 구성

#### 화면 M1: 메인 대시보드 (Engine 연동 시)
```
+-----------------------------------+
|  PocketMonitor           [설정]   |
|                                   |
|  ┌─ 서버 상태 ──────────────────┐ |
|  │  ● 서비스 실행 중              │ |
|  │  가동시간: 3일 14시간          │ |
|  └────────────────────────────┘ |
|                                   |
|  ┌────────┐  ┌────────┐         |
|  │ CPU    │  │ 메모리  │         |
|  │  12%   │  │ 2.1/4GB│         |
|  │  [그래프]│  │ [그래프]│         |
|  └────────┘  └────────┘         |
|  ┌────────┐  ┌────────┐         |
|  │ 저장공간│  │ 온도   │         |
|  │ 5.2/23G│  │ 38°C   │         |
|  │ [==..] │  │ ● 정상  │         |
|  └────────┘  └────────┘         |
|                                   |
|  ┌─ 24시간 온도 히스토리 ────────┐ |
|  │  [온도 그래프 차트]            │ |
|  └────────────────────────────┘ |
|                                   |
|  SSH 접속 정보                     |
|  192.168.0.15:2022         [복사] |
|                                   |
|  ┌──────┐  ┌──────┐  ┌──────┐  |
|  │ 시작  │  │ 중지  │  │재시작 │  |
|  └──────┘  └──────┘  └──────┘  |
|                                   |
|  ┌───────────────────────────┐  |
|  │      [AdMob Banner]       │  |
|  └───────────────────────────┘  |
+-----------------------------------+
```

#### 화면 M2: 메인 대시보드 (Engine 미연동)
```
+-----------------------------------+
|  PocketMonitor           [설정]   |
|                                   |
|  ┌─ 디바이스 상태 ─────────────┐  |
|  │  CPU: 5%  RAM: 1.8/4GB     │  |
|  │  온도: 32°C  저장: 12/64GB  │  |
|  └────────────────────────────┘ |
|                                   |
|  ┌─ 24시간 온도 히스토리 ────────┐ |
|  │  [온도 그래프 차트]            │ |
|  └────────────────────────────┘ |
|                                   |
|  ┌─ 서버 연동 ─────────────────┐ |
|  │  서버 엔진이 감지되지 않았습니다│ |
|  │  서버 기능을 사용하려면        │ |
|  │  PocketServer Engine이       │ |
|  │  필요합니다                   │ |
|  │                              │ |
|  │  [자세히 보기]                │ |
|  └────────────────────────────┘ |
|                                   |
|  ┌───────────────────────────┐  |
|  │      [AdMob Banner]       │  |
|  └───────────────────────────┘  |
+-----------------------------------+
```

#### 화면 M3: 설정
```
+-----------------------------------+
|  ← 설정                           |
|                                   |
|  알림 설정                         |
|  ├ 일일 리포트          [ON/OFF]  |
|  ├ 온도 경고 알림        [ON/OFF]  |
|  └ 주간 요약            [ON/OFF]  |
|                                   |
|  서버 연동                         |
|  ├ 연결 상태: 연결됨 / 미연결      |
|  └ [서버 엔진 설치 안내]           |
|                                   |
|  앱 정보                           |
|  ├ 버전: 1.0                      |
|  └ 오픈소스 라이선스               |
+-----------------------------------+
```

### 6.3 PocketServer Engine 화면 구성

#### 화면 E1: 셋업 마법사 — 사양 검사
```
+-----------------------------------+
|  PocketServer Engine              |
|                                   |
|  서버 설치를 시작합니다             |
|                                   |
|  기기 사양:                        |
|  [v] Android 11                   |
|  [v] RAM 4GB                     |
|  [v] 저장공간 23GB 여유           |
|  [v] ARM64 프로세서               |
|                                   |
|  +---------------------------+   |
|  |   서버 설치 시작하기         |   |
|  +---------------------------+   |
|                                   |
+-----------------------------------+
```

#### 화면 E2: 셋업 마법사 — 설치 진행
```
+-----------------------------------+
|  서버를 설치하고 있습니다           |
|                                   |
|  [==============....]  67%       |
|                                   |
|  [v] PRoot 환경 초기화 완료       |
|  [v] Ubuntu 24.04 설치 완료      |
|  [v] 스왑 메모리 설정 완료        |
|  [~] Dropbear SSH 설정 중...     |
|  [ ] 네트워크 설정                |
|                                   |
|  예상 소요시간: 약 3분             |
+-----------------------------------+
```

#### 화면 E3: 셋업 마법사 — 설치 완료
```
+-----------------------------------+
|  ✓ 서버 준비 완료!                 |
|                                   |
|  ┌─ SSH 접속 정보 ──────────┐    |
|  │ 주소: 192.168.0.15       │    |
|  │ 포트: 2022              │    |
|  │ 사용자: pocketserver    │    |
|  │ 비밀번호: ********      │    |
|  │ [복사]  [비밀번호 보기]   │    |
|  └──────────────────────────┘    |
|                                   |
|  ssh pocketserver@               |
|  192.168.0.15 -p 2022     [복사] |
|                                   |
|  ┌──────────────────────────┐    |
|  │ PocketMonitor를 설치하면  │    |
|  │ 매일 서버 상태를 확인하고  │    |
|  │ 안전하게 관리할 수 있습니다│    |
|  │                          │    |
|  │ [Play Store에서 설치]     │    |
|  └──────────────────────────┘    |
|                                   |
|  ┌──────────────────────────┐    |
|  │  이 앱은 이제 닫아도 됩니다 │    |
|  │  서버는 백그라운드에서       │    |
|  │  계속 실행됩니다            │    |
|  └──────────────────────────┘    |
+-----------------------------------+
```

#### 화면 E4: 제조사별 최적화
```
+-----------------------------------+
|  백그라운드 실행 설정               |
|                                   |
|  Samsung Galaxy 기기가             |
|  감지되었습니다                     |
|                                   |
|  서버가 안정적으로 동작하려면        |
|  아래 설정이 필요합니다:            |
|                                   |
|  [1] 배터리 최적화 해제            |
|      [설정 열기]                   |
|                                   |
|  [2] "절대 절전 안 함"에 추가      |
|      [설정 열기]                   |
|                                   |
|  (!) 이 설정을 하지 않으면         |
|  화면이 꺼질 때 서버가              |
|  중지될 수 있습니다                |
+-----------------------------------+
```

---

## 7. 기술 아키텍처

### 7.1 기술 스택

| 계층 | 기술 | 비고 |
|------|------|------|
| 언어 | Kotlin 1.9.0 | 양쪽 앱 공통 |
| UI | Jetpack Compose | 양쪽 앱 공통 |
| 빌드 | Gradle 8.5, AGP 8.2.2, Java 17 | 공통 |
| minSdk | 26 (Android 8.0) | 공통 |
| targetSdk | 34 | 공통 |
| 상태 관리 | ViewModel + StateFlow | 공통 |
| 광고 | Google AdMob | Monitor 앱만 |
| IPC | LocalSocket + BroadcastReceiver | 앱 간 통신 |
| 리눅스 컨테이너 | PRoot (바이너리 번들) | Engine 앱만 |
| 리눅스 배포판 | Ubuntu 24.04 LTS ARM64 | Engine 앱만 |
| SSH 서버 | Dropbear (포트 2022) | Engine 앱만 |
| 백그라운드 | Foreground Service + 7계층 | Engine 앱만 |
| 배포 | Firebase Hosting | Engine APK 배포 |

### 7.2 앱 구조 — PocketMonitor

```
kr.co.palank.pocketmonitor/
├── MainActivity.kt                    메인 액티비티
├── ui/
│   ├── dashboard/
│   │   ├── DashboardScreen.kt         메인 대시보드
│   │   └── DashboardViewModel.kt      상태 관리
│   ├── settings/
│   │   └── SettingsScreen.kt          설정 화면
│   └── theme/
│       ├── Color.kt                   컬러 시스템
│       └── Theme.kt                   Material3 테마
├── ipc/
│   ├── EngineConnector.kt             LocalSocket 클라이언트
│   └── AlertReceiver.kt              Broadcast 수신기
├── monitor/
│   ├── DeviceMonitor.kt               디바이스 상태 (CPU/RAM/온도)
│   └── HistoryTracker.kt              24시간 히스토리 기록
├── notification/
│   ├── DailyReportScheduler.kt        일일 리포트 스케줄러
│   ├── WeeklyReportScheduler.kt       주간 리포트 스케줄러
│   └── AlertNotifier.kt              온도/이벤트 알림
├── ad/
│   └── AdManager.kt                   AdMob 배너 + 전면 광고
└── util/
    └── BatteryMonitor.kt              배터리 온도 모니터링
```

### 7.3 앱 구조 — PocketServer Engine

```
kr.co.palank.pocketserver/
├── MainActivity.kt                    셋업 마법사 호스트
├── ui/
│   ├── setup/
│   │   └── SetupWizardScreen.kt       설치 마법사 (사양검사→설치→완료)
│   └── optimization/
│       └── OptimizationGuide.kt       제조사별 딥링크 안내
├── service/
│   ├── ServerForegroundService.kt     Foreground Service (7계층)
│   └── WatchdogWorker.kt             15분 주기 헬스체크
├── receiver/
│   └── BootReceiver.kt               BOOT_COMPLETED 자동시작
├── linux/
│   ├── ProotBinaryManager.kt         PRoot 바이너리 추출
│   ├── ProotManager.kt               PRoot 프로세스 관리
│   ├── InstallManager.kt             Ubuntu 설치 프로세스
│   ├── DropbearManager.kt            Dropbear SSH 관리
│   ├── SwapManager.kt                스왑 메모리 관리
│   └── SessionManager.kt             통합 상태 머신
├── ipc/
│   ├── IpcServer.kt                   LocalSocket 서버
│   └── AlertBroadcaster.kt           Monitor로 알림 Broadcast
├── monitor/
│   ├── ResourceMonitor.kt            CPU/RAM 모니터링
│   └── NetworkMonitor.kt             WiFi IP 감지
├── manufacturer/
│   ├── ManufacturerDetector.kt       제조사 감지
│   └── ManufacturerOptimizationHelper.kt  딥링크 헬퍼
└── util/
    ├── SpecChecker.kt                 사양 검사
    └── BatteryMonitor.kt             온도 모니터링 (50°C 자동정지)
```

### 7.4 프로세스 아키텍처

```
Android OS
├── PocketMonitor (Play Store 앱)
│   ├── UI Layer (대시보드 + AdMob)
│   ├── DeviceMonitor (CPU/RAM/온도 자체 모니터링)
│   ├── EngineConnector (LocalSocket → Engine IPC)
│   ├── AlertReceiver (Broadcast 수신)
│   ├── DailyReportScheduler (WorkManager, 매일 9시)
│   └── WeeklyReportScheduler (WorkManager, 매주 일요일)
│
├── PocketServer Engine (사이드로드 앱)
│   ├── ServerForegroundService (7계층 Keep-Alive)
│   │   ├── Partial Wake Lock
│   │   ├── WiFi Lock
│   │   └── PRoot 프로세스
│   │       ├── Ubuntu 24.04 LTS
│   │       ├── Dropbear SSH (포트 2022)
│   │       └── 사용자 서비스 (OpenClaw, n8n 등)
│   ├── IpcServer (LocalSocket → Monitor 앱 응답)
│   ├── AlertBroadcaster (온도/크래시 → Monitor 앱)
│   ├── BootReceiver (BOOT_COMPLETED)
│   └── WatchdogWorker (15분 주기)
```

### 7.5 설치 및 사용 플로우

```
[사용자 여정]

1. Play Store에서 "PocketMonitor" 발견 + 설치
   → 디바이스 헬스 대시보드로 독립 사용 가능

2. 설정 > 서버 연동 > [자세히 보기] 클릭
   → 브라우저에서 Firebase Hosting 페이지 열기

3. Engine APK 다운로드 + 설치
   → "출처를 알 수 없는 앱" 허용 필요

4. Engine 앱 실행 → 셋업 마법사
   → 사양 검사 → 서버 설치 (원클릭) → 완료
   → 제조사별 최적화 안내

5. Engine 앱 닫기 (백그라운드에서 서버 계속 실행)

6. PocketMonitor에서 서버 상태 자동 감지
   → 대시보드에 서버 정보 표시
   → 매일 안전 점검 리포트 수신
   → 온도 경고 알림 수신
```

---

## 8. 수익 모델

### 8.1 기본 원칙
- **전 기능 무료**: 유료 기능 없음, IAP 없음, 구독 없음
- **사용자 확보 최우선**: 진입 장벽을 최소화
- **광고는 Monitor 앱에만**: Engine 앱에 광고 없음
- **서버 운영에 절대 영향 없음**: 광고 코드는 Engine 앱에 존재하지 않음

### 8.2 AdMob 광고 배치 (Monitor 앱)

| 광고 유형 | 위치 | 빈도 |
|-----------|------|------|
| 배너 광고 | 대시보드 하단 | 상시 1개 |
| App Open Ad (앱 진입) | 스플래시 → 대시보드 전환 시 | 매 실행 시 1회 |

### 8.3 광고 제한 규칙
- 광고는 PocketMonitor 앱에만 존재
- PocketServer Engine에는 광고 코드 자체가 없음
- 설정 화면, 온보딩 화면에는 광고 미표시
- 서버 운영에 광고가 영향을 주지 않음 (별도 앱이므로 원천적 불가)

### 8.4 수익 시뮬레이션

**광고 노출 경로**:
- 일일 리포트 푸시 → 앱 열기 → App Open Ad(진입) + 배너 = **2회 노출/일**
- 온도 경고 푸시 → 앱 열기 → App Open Ad + 배너 = **추가 2회/이벤트**
- 주간 요약 → 추가 2회/주

| 활성 사용자 | 일일 노출 (배너+App Open) | 월 노출 | 예상 월 수익 |
|-----------|--------------------------|---------|------------|
| 1,000명 | 2,000 | 60,000 | **$90~150** |
| 10,000명 | 20,000 | 600,000 | **$900~1,500** |
| 50,000명 | 100,000 | 3,000,000 | **$4,500~7,500** |
| 100,000명 | 200,000 | 6,000,000 | **$9,000~15,000** |

> App Open Ad eCPM은 배너보다 5~10배 높음 ($8~15). 실제 수익은 배너 단독보다 크게 높음.
> 가정: DAU = MAU의 30%, 하루 평균 앱 오픈 1회 기준.

---

## 9. 배포 전략

### 9.1 PocketMonitor 배포
- **Google Play Store** 정식 등록
- 카테고리: 도구 (Tools) > 디바이스 모니터링
- 설명: "디바이스 헬스 모니터링 + 온도 관리 앱"
- 스크린샷: 디바이스 모니터링 기능 중심 (서버 기능은 부수적)
- Play Store 정책 100% 준수 (PRoot/rootfs/SSH 코드 없음)

### 9.2 PocketServer Engine 배포
- **Firebase Hosting** 웹페이지에서 APK 직접 다운로드
- URL: pocketserver.web.app (또는 커스텀 도메인)
- 웹페이지 내용:
  - Engine APK 다운로드 버튼
  - 설치 가이드 (출처를 알 수 없는 앱 허용 방법)
  - FAQ
  - version.json (자동 업데이트 체크용)
- 추가 배포 채널 (미래): F-Droid, GitHub Releases
- **비용 주의**: Firebase Hosting 무료 티어 = 10GB 저장소, 360MB/일 대역폭
  - Engine APK ~50-80MB 예상
  - 하루 4~7명 다운로드 가능 (무료 범위)
  - 초과 시 $0.18/GB → 스케일 시 비용 모니터링 필요
  - 대안: 대량 다운로드 시 GitHub Releases로 마이그레이션 검토

### 9.3 상호 연결
- Monitor → Engine: 설정 > 서버 연동 > [자세히 보기] → 브라우저로 Firebase Hosting 페이지 이동
- Engine → Monitor: 설치 완료 화면에서 [Play Store에서 설치] → Play Store PocketMonitor 페이지 이동
- **어떤 앱에서도 다른 앱을 직접 다운로드하지 않음** (항상 외부 이동)

---

## 10. 보안 요구사항

### 10.1 SSH 보안 (Engine 앱)
- Dropbear SSH 서버 (경량)
- 기본 비밀번호 자동 생성 (영문+숫자 12자리)
- root 직접 로그인 비활성화
- SSH 포트 2022

### 10.2 IPC 보안
- 양 앱 동일 개발자 서명
- LocalSocket 통신 시 토큰 기반 인증
- Broadcast에 signature 레벨 퍼미션 적용

### 10.3 네트워크 보안
- SSH는 같은 WiFi 내 로컬 IP로만 접속 (MVP)
- 외부 접근 필요 시 사용자가 직접 터널링 설정

---

## 11. 지원 언어

### MVP
- 한국어 (기본)
- 영어

---

## 12. 성공 지표 (KPI)

| 지표 | 목표 (출시 3개월) | 설명 |
|------|-----------------|------|
| Monitor 다운로드 수 | 10,000+ | Play Store 무료 앱 |
| Engine 다운로드 수 | 5,000+ | 사이드로드 (Monitor의 50% 전환율) |
| 서버 설치 완료율 | 85%+ | Engine 설치 후 서버 구축 완료 비율 |
| DAU (Monitor) | 3,000+ | 매일 리포트 확인하는 사용자 |
| 광고 수익 (월) | $1,000+ | AdMob 배너 + 전면 광고 |
| Monitor 앱 평점 | 4.5+ | Google Play 평점 |
| 크래시율 | < 1% | 양쪽 앱 모두 |
| 7일 리텐션 | 70%+ | Monitor 앱 기준 |

---

## 13. 출시 계획

### Phase 1: Engine 앱 MVP (4주)
- PRoot + Dropbear 기반 원클릭 Ubuntu 24.04 설치
- 7계층 Keep-Alive 시스템
- IPC 서버 (LocalSocket)
- 셋업 마법사 UI
- Firebase Hosting 배포

### Phase 2: Monitor 앱 MVP (3주)
- 디바이스 헬스 대시보드
- Engine IPC 연동
- 일일/주간 리포트 푸시 알림
- AdMob 배너 + 전면 광고
- Google Play Store 등록

### Phase 3: 안정화 (2주)
- 실기기 테스트 (5~10대)
- 클로즈드 베타 (50명)
- 버그 수정 + 성능 최적화

### Phase 4: 정식 출시
- Play Store 정식 출시 (Monitor)
- Firebase Hosting 공개 (Engine)
- 마케팅 시작

---

## 14. 기술적 제약사항

| 제약사항 | 영향 | 대응 |
|---------|------|------|
| PRoot 성능 오버헤드 (~30%) | 서비스 응답 속도 | 경량 서비스 위주 사용 권장 |
| 1024 이하 포트 사용 불가 | SSH 포트 22 불가 | Dropbear 포트 2022 사용 |
| Docker 사용 불가 (PRoot) | 컨테이너 서비스 불가 | 네이티브 설치 방식 |
| 사이드로드 필요 (Engine) | 사용자 진입 장벽 | 설치 가이드 제공, 간단한 절차 |
| 2-앱 통신 복잡도 | 개발/디버깅 | LocalSocket 표준 프로토콜 사용 |

---

## 부록 A: 용어 정리

| 용어 | 설명 |
|------|------|
| PocketMonitor | Play Store 배포 앱 — 디바이스/서버 모니터링 + 광고 |
| PocketServer Engine | 사이드로드 앱 — 리눅스 서버 설치/운영 엔진 |
| IPC | Inter-Process Communication, 앱 간 통신 |
| LocalSocket | Android의 Unix 도메인 소켓, 앱 간 로컬 통신 |
| PRoot | 루팅 없이 리눅스를 실행하는 사용자 공간 도구 |
| Dropbear | 경량 SSH 서버/클라이언트 |
| 사이드로드 | Play Store 외부에서 APK를 직접 설치하는 방식 |
| Firebase Hosting | Google의 정적 웹 호스팅 서비스 (Engine APK 배포용) |

## 부록 B: 기존 1-앱 구조 대비 변경점

| 항목 | 기존 (1-앱) | 변경 (2-앱) |
|------|-----------|------------|
| 배포 | Play Store 1개 | Play Store (Monitor) + 사이드로드 (Engine) |
| 광고 위치 | 서버 앱 내 대시보드 | Monitor 앱 대시보드 (서버와 완전 분리) |
| Play Store 정책 | 위반 8건 (등록 불가) | Monitor 100% 준수, Engine은 정책 적용 안 됨 |
| 수익 모델 | 배너 + 설치 완료 전면 | 배너 + 실행 시 전면 + 이탈 시 전면 |
| 사용자 경험 | 원클릭 (1앱) | 2단계 (Monitor 설치 → Engine 사이드로드) |
| IAP | 미래 계획 | 없음 (전 기능 무료) |
