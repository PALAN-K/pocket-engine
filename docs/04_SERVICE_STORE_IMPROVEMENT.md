# Service Store 개선 분석 보고서

> 작성일: 2026-02-18
> 상태: 분석 완료, 구현 검증 대기

---

## 1. 현재 문제점 요약

### 1.1 게이트웨이 중단 시 재실행 불가
- 현재 상태: PicoClaw/OpenClaw 게이트웨이가 중단되면 사용자가 재시작할 UI가 없음
- ServiceStoreScreen에 "중지" 버튼만 존재, "재시작" 버튼 없음
- ServiceManager에 헬스체크 루프 없음 — 크래시 자동 감지 불가
- IPC에 개별 서비스 제어 명령 미구현 (`service_start`, `service_stop` 없음)

### 1.2 설치 후 요약 정보 없음
- 서비스 설치 완료 후 `onComplete`가 `"setup"` 화면으로 복귀 (버그)
- 설치된 서비스의 상태/접속정보 요약 화면 부재
- SetupWizardScreen의 CompletedPhase에서 "AI 비서 설치하기" 버튼이 설치 후에도 동일하게 표시
- 서비스별 맞춤 안내 없음 (PicoClaw vs OpenClaw 구분 없는 제네릭 메시지)

### 1.3 AI 모델 변경 불가
- PicoClawInstaller: `gemini-2.5-flash-lite` 하드코딩 (configure() 메서드)
- OpenClawInstaller: `google/gemini-2.5-flash-lite` 하드코딩 (Tier 2 fallback)
- ServiceCatalog의 InputField에 모델 선택 필드 없음
- Provider 변경 UI 없음 (Gemini → Groq 전환 불가)

### 1.4 Google Gemini 무료 티어 제한 (발견사항)
- 공식 문서의 1,000 RPD(Flash-Lite)는 현재 적용되지 않음
- 실측: **모든 모델 20 RPD** (네이티브/OpenAI 호환 엔드포인트 동일)
- 네이티브 `generateContent`와 OpenAI 호환 `/v1beta/openai/` 동일 할당량 공유 확인
- 래퍼/프록시 방식으로 우회 불가

---

## 2. 대안 Provider 분석

### 2.1 Provider 비교표

| Provider | API Base URL | 무료 RPD | 도구 호출 | PicoClaw 지원 | OpenClaw 지원 | 지속 무료 |
|----------|-------------|----------|-----------|:------------:|:------------:|:---------:|
| Gemini (현재) | generativelanguage...googleapis.com | 20* | O | O (네이티브) | O (내장) | O |
| **Groq** | api.groq.com/openai/v1 | **1,000** | **O (전모델)** | **O (stable)** | **O (내장)** | **O** |
| Cerebras | api.cerebras.ai/v1 | 14,400 | △ (2모델) | X | O (내장) | O |
| OpenRouter | openrouter.ai/api/v1 | 50 | △ (모델별) | O (testing) | O (내장) | △ |
| SambaNova | api.sambanova.ai/v1 | 40 | △ | X | X | O |
| Mistral | api.mistral.ai/v1 | 미공개 | O | X | O (내장) | O |
| Together AI | api.together.xyz/v1 | 크레딧제 | O | X | X | **X** |

*Gemini 20 RPD = 2026-02-18 실측 기준

### 2.2 1순위 권장: Groq

**선정 이유:**
1. PicoClaw/OpenClaw 양쪽 모두 네이티브 지원 (별도 프록시 불필요)
2. 1,000 RPD — Gemini 대비 50배 (20→1,000)
3. 전 모델 도구 호출(tool calling) 지원 — PicoClaw 13개 도구 완전 호환
4. OpenAI 호환 API — `api_base` 변경만으로 전환
5. 신용카드 불필요
6. Whisper 음성 인식 무료 포함 (Telegram 음성 메시지 자동 변환)
7. 추론 속도 300+ tok/s (최고 수준)

**Groq API Key:**
- 형식: `gsk_` 접두사, 40~60자
- 발급: https://console.groq.com/keys
- 검증: `key.startsWith("gsk_") && key.length in 40..60`
- 온라인 검증: `GET https://api.groq.com/openai/v1/models` (Authorization: Bearer)

**Groq 무료 모델:**

| 모델 | RPD | TPM | 특징 |
|------|-----|-----|------|
| llama-3.3-70b-versatile | 1,000 | 12,000 | 범용, 고품질 |
| llama-3.1-8b-instant | 14,400 | 6,000 | 초경량, 빠른 응답 |
| llama-4-scout-17b | 1,000 | 30,000 | 최신 Llama 4 |
| qwen/qwen3-32b | 1,000 | 6,000 | 중국어 지원 |
| moonshotai/kimi-k2-instruct | 1,000 | 10,000 | 다국어 |

**PicoClaw Groq 설정:**
```json
{
  "agents": {
    "defaults": {
      "model": "llama-3.3-70b-versatile"
    }
  },
  "providers": {
    "groq": {
      "api_key": "gsk_...",
      "api_base": ""
    }
  },
  "channels": {
    "telegram": {
      "enabled": true,
      "token": "...",
      "allow_from": ["*"]
    }
  },
  "tools": {}
}
```

### 2.3 멀티 Provider 전략 (일일 무료 예산)

| Provider | RPD | 역할 |
|----------|-----|------|
| Groq (1순위) | 1,000 | 주 사용 |
| Gemini (2순위) | 20~250 | 예비 |
| **합계** | **~1,250** | 시간당 ~52회 |

---

## 3. 개선 방안 설계

### 3.1 개선 A: 서비스 상태 대시보드 + 재시작 버튼

**현재 흐름:**
```
서비스 설치 완료 → CompletedStep → "완료" → setup 화면 (버그)
→ ServiceStoreScreen에서만 상태 확인 가능 → "중지" 버튼만 존재
```

**개선 흐름:**
```
서비스 설치 완료 → CompletedStep (서비스별 요약 포함) → "완료" → service_store
→ ServiceStoreScreen에 상태 카드 표시:
  - 서비스명 + 상태 (Running/Stopped/Error)
  - 사용 중인 모델명 + Provider
  - "재시작" / "중지" / "설정 변경" 버튼
  - 로그 보기 (마지막 10줄)
```

**구현 항목:**
1. `MainActivity.kt`: `onComplete`에서 `currentScreen = "service_store"` (버그 수정)
2. `ServiceStoreScreen.kt`: 설치된 서비스 카드 개선
   - 상태 dot (초록/빨강) + 모델명 표시
   - "재시작" 버튼 추가
   - "설정 변경" 버튼 추가 (모델/API키/토큰 변경 화면으로)
3. `ServiceManager.kt`: `restartService(serviceId)` 메서드 추가
4. `SetupWizardScreen.kt`: CompletedPhase에서 설치 상태 반영
   - 설치됨: "PicoClaw 실행 중" + "서비스 관리" 버튼
   - 미설치: 기존 "AI 비서 설치하기" 버튼

### 3.2 개선 B: 설치 후 서비스 요약 화면

**ServiceSetupScreen.kt CompletedStep 개선:**

```
┌─────────────────────────────────┐
│  PicoClaw 설치 완료!             │
│                                  │
│  ┌─ 서비스 정보 ─────────────┐  │
│  │ 모델: llama-3.3-70b       │  │
│  │ Provider: Groq             │  │
│  │ 상태: 실행 중              │  │
│  └───────────────────────────┘  │
│                                  │
│  Telegram에서 봇에게             │
│  메시지를 보내보세요             │
│                                  │
│  ┌─ 참고 ────────────────────┐  │
│  │ - 무료 일일 1,000회 사용   │  │
│  │ - 음성 메시지도 지원       │  │
│  └───────────────────────────┘  │
│                                  │
│  [서비스 관리]      [완료]       │
└─────────────────────────────────┘
```

### 3.3 개선 C: AI 모델 / Provider 선택 UI

**ServiceSetupScreen의 API_KEY_INPUT 단계 개선:**

현재: Gemini API Key + Telegram 토큰 입력만
개선: Provider 선택 → 해당 Provider의 API Key 입력 → 모델 선택 → Telegram 토큰

```
┌─────────────────────────────────┐
│  AI 모델 설정                    │
│                                  │
│  Provider 선택:                  │
│  ┌────────────────────────────┐ │
│  │ ○ Groq (권장, 1000회/일)   │ │
│  │ ○ Google Gemini (20회/일)  │ │
│  └────────────────────────────┘ │
│                                  │
│  Groq API Key:                   │
│  ┌────────────────────────────┐ │
│  │ gsk_...                    │ │
│  └────────────────────────────┘ │
│  [API 키 발급받기 ->]            │
│                                  │
│  모델:                           │
│  ┌────────────────────────────┐ │
│  │ llama-3.3-70b-versatile   │ │
│  └────────────────────────────┘ │
│                                  │
│  Telegram 봇 토큰:              │
│  ┌────────────────────────────┐ │
│  │ 1234567:ABC...             │ │
│  └────────────────────────────┘ │
│  [BotFather 열기 ->]             │
│                                  │
│  [다음]                          │
└─────────────────────────────────┘
```

**구현 항목:**
1. `ServiceCatalog.kt`: Provider 선택 + 모델 선택 InputField 추가
   - 새 InputType: `PROVIDER_SELECT`, `MODEL_SELECT`
   - Provider별 모델 목록, API 키 형식, 발급 URL 정의
2. `ServiceSetupScreen.kt`: Provider/모델 선택 UI 구현
3. `PicoClawInstaller.kt`: `configure()`에 provider/model 파라미터 추가
4. `OpenClawInstaller.kt`: `configure()`에 provider/model 파라미터 추가

### 3.4 개선 D: 설정 변경 (재설정) 기능

서비스 설치 후 API 키, 모델, 봇 토큰을 변경하는 기능:

```
ServiceStoreScreen → "설정 변경" 탭
→ 기존 값 로드 (config.json에서)
→ 수정 → 저장 → 서비스 재시작
```

**구현 항목:**
1. `ServiceInstaller` 인터페이스에 `reconfigure(inputs: Map<String, String>)` 추가
2. PicoClaw/OpenClaw Installer에 재설정 로직 구현
3. ServiceStoreScreen에 "설정 변경" 버튼 + 재설정 UI

---

## 4. 구현 우선순위

| 우선순위 | 개선 | 난이도 | 영향도 | 예상 작업량 |
|---------|------|--------|--------|------------|
| P0 | 네비게이션 버그 수정 (setup→service_store) | 낮음 | 높음 | 1줄 수정 |
| P1 | Provider 선택 + 모델 선택 UI (개선 C) | 높음 | 높음 | ServiceCatalog + SetupScreen + Installers |
| P2 | 재시작 버튼 + 상태 카드 (개선 A) | 중간 | 높음 | ServiceStoreScreen + ServiceManager |
| P3 | 서비스 요약 화면 (개선 B) | 중간 | 중간 | CompletedStep 개선 |
| P4 | 설정 변경 기능 (개선 D) | 높음 | 중간 | 재설정 로직 + UI |

---

## 5. 영향 받는 파일

| 파일 | 변경 유형 | 개선 항목 |
|------|----------|----------|
| `MainActivity.kt` | 수정 (1줄) | P0: 네비게이션 버그 |
| `ServiceCatalog.kt` | 대폭 수정 | P1: Provider/모델 정의 추가 |
| `ServiceSetupScreen.kt` | 대폭 수정 | P1: Provider 선택 UI, P3: 요약 화면 |
| `ServiceStoreScreen.kt` | 수정 | P2: 재시작 버튼, 상태 카드 |
| `ServiceStoreViewModel.kt` | 수정 | P2: 재시작/재설정 액션 |
| `ServiceManager.kt` | 수정 | P2: restartService() 추가 |
| `PicoClawInstaller.kt` | 수정 | P1: provider/model 파라미터, P4: reconfigure |
| `OpenClawInstaller.kt` | 수정 | P1: provider/model 파라미터, P4: reconfigure |
| `ServiceInstaller.kt` | 수정 | P4: reconfigure() 인터페이스 추가 |
| `IpcServer.kt` | 수정 (선택) | P2: 개별 서비스 제어 명령 추가 |

---

## 6. Groq API Key 검증 스펙

```
형식: gsk_ 접두사, 총 40~60자 (영숫자 + _ + -)
정규식: ^gsk_[a-zA-Z0-9_-]{36,56}$
발급 URL: https://console.groq.com/keys
온라인 검증: GET https://api.groq.com/openai/v1/models (Bearer 인증, 200=유효)
```

---

## 7. Gemini API Key 검증 스펙 (기존 유지)

```
형식: AIzaSy 접두사, 총 39자
정규식: ^AIzaSy[a-zA-Z0-9_-]{33}$
발급 URL: https://aistudio.google.com/app/apikey
온라인 검증: GET https://generativelanguage.googleapis.com/v1beta/models?key={KEY} (200=유효)
```

---

## 8. 엣지케이스 및 위험성 검증 결과

> 검증일: 2026-02-18
> 검증 방법: 소스 코드 직접 확인 + 웹 문서 조사

### 8.1 검증 결과 요약

| 항목 | 결과 | 비고 |
|------|------|------|
| 네비게이션 버그 (MainActivity:143) | ✅ 확인됨 | `"setup"` → `"service_store"` 1줄 수정 |
| PicoClaw 모델 하드코딩 | ✅ 확인됨 | PicoClawInstaller:65 `gemini-2.5-flash-lite` |
| OpenClaw 모델 하드코딩 | ✅ 확인됨 | OpenClawInstaller:210 `google/gemini-2.5-flash-lite` |
| PicoClaw Groq 네이티브 지원 | ✅ 확인됨 | picoclaw.ai/docs에서 `groq` provider 공식 지원 |
| P0 수정 1줄 변경 | ✅ 확인됨 | 부작용 없음 (ErrorStep도 service_store로 가는 것이 더 적절) |

### 8.2 분석 문서가 누락한 추가 하드코딩 (🔴 Critical)

원래 분석에서 식별된 하드코딩 외에 **5개 추가 발견**:

| # | 파일 | 위치 | 하드코딩 값 | 문제 |
|---|------|------|------------|------|
| 1 | PicoClawInstaller.kt | :69 | provider 키 `"gemini"` | Groq 전환 시 `"groq"`로 변경 필요 |
| 2 | OpenClawInstaller.kt | :163 | `--auth-choice gemini-api-key` | Groq는 다른 auth-choice 플래그 필요 |
| 3 | OpenClawInstaller.kt | :148-150 | `.env`에 `GEMINI_API_KEY` | Groq는 `GROQ_API_KEY` 환경변수 필요 |
| 4 | ServiceCatalog.kt | :46-52 | `id="gemini_api_key"`, `hint="AIzaSy..."` | 입력필드 전체가 Gemini 전용 |
| 5 | ServiceCatalog.kt | :75-81 | 동일 (OpenClaw도) | 양 서비스 모두 Gemini 전용 입력 |

**영향**: Provider 선택 UI(개선 C)의 범위가 문서 추정보다 **상당히 큼**. ServiceCatalog의 InputField 스키마 자체를 재설계해야 함.

### 8.3 동시성 위험 (🔴 Critical)

**문제**: ServiceManager의 `startService()`, `stopService()`에 동시성 보호 없음.
- `scope.launch(Dispatchers.IO)` 로 각각 독립 코루틴 실행
- 재시작 버튼 빠르게 10회 탭 → 10개 코루틴 동시 실행
- `picoClawProcess`는 `@Volatile`이나 synchronized 아님
- 결과: **고아 PRoot 프로세스** 생성 가능 (정리 불가)

**필수 대응**: P2(재시작 버튼) 구현 전 `Mutex` 또는 상태 가드 추가 필수.

### 8.4 OpenClaw Provider 전환 위험 (🔴 Critical)

**문제**: OpenClaw의 `openclaw onboard --non-interactive` 명령이 `--auth-choice gemini-api-key`로 하드코딩.
- Groq로 전환 시 onboard CLI의 auth-choice 옵션이 다름
- `.env` 파일의 환경변수명 변경 필요 (`GEMINI_API_KEY` → `GROQ_API_KEY`)
- onboard가 이미 생성한 내부 상태와 충돌 가능
- **Provider 전환 시 `~/.openclaw/` 디렉토리 전체 초기화 필요할 수 있음**

### 8.5 Telegram `allow_from: ["*"]` 보안 위험 (⚠️ Warning)

**현재 상태**: 양 서비스 모두 `allow_from: ["*"]` (누구나 봇에게 메시지 가능)
**Groq 적용 시 위험 증가**: 1,000 RPD 할당량을 외부인이 소진 가능
- Gemini 20 RPD → 외부인 영향 제한적
- Groq 1,000 RPD → 외부인이 하루 1,000회 요청 가능 (심각)
**권장**: 사용자 Telegram ID를 `allow_from`에 설정하도록 UI 안내 추가

### 8.6 기존 사용자 호환성 (⚠️ Warning)

**시나리오**: 이미 Gemini으로 PicoClaw 설치한 사용자가 앱 업데이트 후 재설정 UI 진입
- 재설정 UI가 Groq를 기본값으로 표시하면 → 저장 시 Gemini 설정 덮어쓰기
- **필수 대응**: 재설정 시 현재 config.json에서 기존 값을 읽어 UI에 프리필

### 8.7 버전 동기화 (⚠️ Warning)

- 현재: 양 앱 versionCode 3, versionName "1.1.1"
- SKILL.md Rule 13: 양 앱 버전 동일 유지 필수
- P0(버그 수정)만 단독 배포 시 → 버전 범프 불필요 (동일 코드에 대한 핫픽스)
- P1~P4 기능 추가 시 → versionCode 4, versionName "1.2.0" 필요
- **Monitor 앱도 동시 Play Store 제출 필요** (최소한 버전만 올린 빌드)

### 8.8 기타 엣지케이스

| 케이스 | 위험도 | 대응 |
|--------|--------|------|
| Groq 서비스 다운 시 설치 | 중간 | 온라인 API 키 검증 (Android 측에서, PRoot 아님) |
| 재시작 중 활성 대화 중단 | 낮음 | 확인 다이얼로그 추가 |
| config.json 수동 편집으로 손상 | 낮음 | 시작 실패 시 로그 파일 마지막 10줄 표시 |
| PicoClaw 모델명 형식 (`provider/model` vs `model`) | 중간 | PicoClaw 문서 확인: bare model name 사용 확인됨 |
| 혼합 Provider (PicoClaw=Groq, OpenClaw=Gemini) | 없음 | 독립 config, 충돌 없음 |

---

## 9. 최종 구현 전 필수 조치 사항

| 우선순위 | 조치 | 이유 |
|---------|------|------|
| **MUST** | ServiceManager Mutex 추가 | 재시작 버튼 전 동시성 보호 필수 |
| **MUST** | OpenClaw onboard auth-choice 분기 | Groq 전환 시 CLI 명령 다름 |
| **MUST** | ServiceCatalog InputField 재설계 | 현재 Gemini 전용 스키마 |
| **MUST** | 기존 사용자 config 프리필 | 재설정 시 기존 값 보존 |
| **SHOULD** | Telegram allow_from 경고 UI | Groq 1,000 RPD로 보안 위험 증가 |
| **SHOULD** | 재시작 확인 다이얼로그 | 활성 대화 중단 방지 |
| **SHOULD** | 버전 동기화 계획 수립 | 양 앱 1.2.0 동시 릴리스 |
| **NICE** | 온라인 API 키 검증 | UX 개선 (필수 아님) |
| **NICE** | 실패 시 로그 표시 | 디버깅 편의 |

---

## 10. 전체 위험도 평가: MEDIUM

**근거:**
- P0(네비게이션 버그)은 안전하게 즉시 수정 가능
- P1(Provider 선택)은 예상보다 범위가 크며 OpenClaw onboard 호환성 문제가 핵심 리스크
- P2(재시작 버튼)은 동시성 보호 없이 구현 시 고아 프로세스 위험
- 기존 사용자 호환성은 config 프리필로 해결 가능
- 보안(allow_from)은 경고 UI로 완화 가능

**권장 구현 순서:**
1. P0 즉시 수정 (1줄, 위험 없음)
2. ServiceManager Mutex 추가 (P2 전제조건)
3. ServiceCatalog 재설계 + Provider 선택 UI (P1)
4. 재시작 버튼 + 상태 카드 (P2)
5. 서비스 요약 화면 (P3)
6. 설정 변경 기능 (P4)

---

## 11. 최종 Q&A 분석 (2026-02-18)

> 베타 테스트 중 일반 사용자 관점. "원본 CLI에 GUI를 감싼 구조"로 판단하여 실용적으로 분석.

### Q1. Mutex 동시성 — 중복 실행 문제?

**답변: 맞지만, PicoClaw start()가 자체 pkill cleanup을 하므로 치명적이지 않다. UI 버튼 disable이면 충분.**

- `ServiceManager.kt`의 모든 메서드가 `scope.launch(Dispatchers.IO)`로 독립 코루틴 생성
- `PicoClawInstaller.kt:186` — `picoClawProcess`는 `@Volatile`이지만 `synchronized` 없음
- 그러나 `start()` 진입 시 `pkill -f 'picoclaw gateway'`로 기존 프로세스 정리 후 시작
- 두 번 탭 시: 불필요한 재시작이 1회 발생할 수 있으나 프로세스 2개가 동시에 뜨지는 않음
- **수정**: `startService()` 호출 직후 즉시 상태를 CONFIGURING으로 변경 → UI 버튼 disable

### Q2. 서비스 스토어 = AI 비서 페이지?

**답변: 맞다. 동일한 화면.**

| 코드명 | 사용자 텍스트 | 위치 |
|--------|-------------|------|
| `"service_store"` | — | `MainActivity.kt:129` |
| `ServiceStoreScreen` | **"AI 비서"** | `ServiceStoreScreen.kt:76` |
| `onNavigateToServiceStore` | **"AI 비서 설치하기"** | `SetupWizardScreen.kt:708` |

### Q3. 모델 선택 UX — 어느 단계?

**답변: 기존 API_KEY_INPUT 단계 확장. 새 step 불필요.**

바이너리는 Provider와 무관 (같은 PicoClaw). 설치 후 입력 화면에서:
```
Provider 선택 (라디오) → 모델 드롭다운 → API Key → Telegram Bot Token
```

SetupStep enum 변경 없이 InputStep Composable 내부 확장.

### Q4. 상태 카드 위치

**답변: 엔진 메인페이지 (CompletedPhase). 기존 "AI 비서 설치하기" 카드를 상태 카드로 조건부 교체.**

- 미설치: 기존 "AI 비서 설치하기" 카드 (변경 없음)
- 설치됨: "PicoClaw 실행 중 | Groq llama-3.3-70b" + [서비스 관리] 버튼

### Q5. 상태 카드 + 서비스 요약 — 같은 화면?

**답변: 같은 카드에 합침. 별도 화면은 과잉.**

```
┌─ AI 비서 ──────────────────────────┐
│  PicoClaw             ● 실행 중     │
│  모델: gemini-2.5-flash-lite       │
│  채널: Telegram                    │
│  [재시작]  [중지]  [설정 변경]      │
└────────────────────────────────────┘
```

### Q6. 설정 변경 = .env 파일?

**답변: 아님. UI에서 편집 → 기존 파일 포맷 그대로 덮어쓰기 + 재시작.**

| 서비스 | 설정 파일 | 설정 변경 시 |
|--------|----------|-------------|
| PicoClaw | `config.json` | JSON 덮어쓰기 → 재시작 |
| OpenClaw | `.env` + `openclaw.json` | 양쪽 덮어쓰기 → 재시작 |

본질적으로 기존 `configure()` 메서드를 재호출하는 것과 같음.

---

## 12. 확정 구현 계획

### 구현 순서 (확정)

| 순서 | 작업 | 내용 | 난이도 |
|------|------|------|--------|
| 1 | P0: 네비게이션 버그 | `MainActivity.kt:143` — `"setup"` → `"service_store"` | 1줄 |
| 2 | P1: UI 버튼 debounce | 시작/중지 버튼 탭 시 즉시 disable | 낮음 |
| 3 | P2: Provider/모델 선택 | API_KEY_INPUT 확장 + ServiceCatalog + Installer 수정 | 중간 |
| 4 | P3: 상태 카드 + 요약 | CompletedPhase AI 비서 카드 조건부 교체 + 재시작/중지/설정변경 버튼 | 중간 |
| 5 | P4: 설정 변경 | config 읽기 → UI 프리필 → 덮어쓰기 → 재시작 | 중간 |

### 영향 파일

| 파일 | 변경 |
|------|------|
| `MainActivity.kt` | P0: 1줄 수정 |
| `ServiceStoreScreen.kt` | P1: 버튼 disable, P3: 상태 카드 |
| `ServiceStoreViewModel.kt` | P1: 즉시 상태 전환, P4: 재설정 |
| `ServiceSetupScreen.kt` | P2: Provider/모델 선택 UI |
| `ServiceCatalog.kt` | P2: Provider 정의, 모델 목록, 입력필드 |
| `PicoClawInstaller.kt` | P2: configure()에 provider/model 파라미터 |
| `OpenClawInstaller.kt` | P2: configure()에 provider/model 파라미터 |
| `SetupWizardScreen.kt` | P3: CompletedPhase AI 비서 카드 교체 |
| `ServiceManager.kt` | P3: restartService() 추가 |
