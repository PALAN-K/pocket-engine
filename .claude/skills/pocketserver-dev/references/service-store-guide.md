# Service Store Implementation Guide

> PocketEngine에 AI 비서 원클릭 설치 기능을 추가하기 위한 상세 구현 가이드
> 출처: 2026-02-15 세션 분석 결과

## 1. 아키텍처 개요

```
SetupWizardScreen (기존)
  └─ 서버 설치 완료 → "AI 비서 설치" 카드 표시 (선택)
                         ↓
ServiceStoreScreen (신규)
  ├─ PicoClaw [설치] (1순위, 기본 추천)
  └─ OpenClaw [설치] (2순위, 고급)
                         ↓
ServiceSetupScreen (신규)
  ├─ Step 1: 서비스 설치 (자동)
  ├─ Step 2: API 키 입력 (Gemini)
  ├─ Step 3: 봇 토큰 입력 (Telegram)
  └─ Step 4: 완료 → 서비스 시작
```

## 2. PicoClaw 설치 스크립트

```bash
#!/bin/bash
# PicoClaw ARM64 설치 — PRoot Ubuntu 24.04 내에서 실행
set -e

PICOCLAW_VERSION="latest"
PICOCLAW_URL="https://github.com/sipeed/picoclaw/releases/latest/download/picoclaw-linux-arm64"
INSTALL_DIR="/usr/local/bin"
CONFIG_DIR="/root/.picoclaw"

echo "[1/3] PicoClaw 다운로드 중..."
curl -L -o "${INSTALL_DIR}/picoclaw" "${PICOCLAW_URL}"
chmod +x "${INSTALL_DIR}/picoclaw"

echo "[2/3] 설정 디렉토리 생성..."
mkdir -p "${CONFIG_DIR}"
mkdir -p "${CONFIG_DIR}/workspace"

echo "[3/3] PicoClaw 설치 완료"
picoclaw --version
```

## 3. PicoClaw config.json 템플릿

```json
{
  "agents": {
    "defaults": {
      "workspace": "~/.picoclaw/workspace",
      "model": "gemini-2.5-flash",
      "max_tokens": 8192,
      "temperature": 0.7,
      "max_tool_iterations": 20
    }
  },
  "providers": {
    "gemini": {
      "api_key": "${GEMINI_API_KEY}"
    }
  },
  "channels": {
    "telegram": {
      "enabled": true,
      "token": "${TELEGRAM_BOT_TOKEN}",
      "allowFrom": ["${TELEGRAM_USER_ID}"]
    }
  },
  "tools": {}
}
```

- 출처: https://picoclaw.ai/docs
- `${...}` 플레이스홀더는 PocketEngine Kotlin 코드에서 사용자 입력값으로 교체

## 4. OpenClaw 설치 스크립트

```bash
#!/bin/bash
# OpenClaw 설치 — PRoot Ubuntu 24.04 내에서 실행
# 주의: 4GB+ RAM 기기 권장
set -e

echo "[1/6] Node.js 22 설치 중..."
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt-get install -y nodejs

echo "[2/6] 빌드 도구 설치 중..."
apt-get install -y git build-essential

echo "[3/6] OpenClaw 설치 중..."
npm install -g openclaw@latest

echo "[4/6] Bionic Bypass 적용 중..."
# PRoot 내 os.networkInterfaces() 크래시 방지
# 참고: https://sagartamang.com/blog/openclaw-on-android-termux
cat > /usr/local/lib/node_modules/openclaw/bionic-bypass.js << 'BYPASS'
const os = require('os');
const origNetworkInterfaces = os.networkInterfaces;
os.networkInterfaces = function() {
  try {
    return origNetworkInterfaces.call(this);
  } catch (e) {
    return {};
  }
};
BYPASS

# openclaw 실행 스크립트에 --require 플래그 추가
OPENCLAW_BIN=$(which openclaw)
OPENCLAW_REAL="${OPENCLAW_BIN}.real"
mv "${OPENCLAW_BIN}" "${OPENCLAW_REAL}"
cat > "${OPENCLAW_BIN}" << 'WRAPPER'
#!/bin/bash
node --require /usr/local/lib/node_modules/openclaw/bionic-bypass.js "$(dirname "$(readlink -f "$0")")/openclaw.real" "$@"
WRAPPER
chmod +x "${OPENCLAW_BIN}"

echo "[5/6] OpenClaw 설정 디렉토리 생성..."
mkdir -p /root/.openclaw

echo "[6/6] OpenClaw 설치 완료"
openclaw --version
```

## 5. Gemini API Key 검증

### 클라이언트 사이드 (Kotlin)

```kotlin
object GeminiKeyValidator {
    /** 형식 검증 (오프라인, 즉시) */
    fun isValidFormat(key: String): Boolean =
        key.startsWith("AIzaSy") && key.length == 39

    /** 서버 사이드 유효성 검증 (온라인, PRoot 내 curl) */
    fun buildValidationCommand(key: String): String =
        "curl -s -o /dev/null -w '%{http_code}' " +
        "'https://generativelanguage.googleapis.com/v1beta/models?key=$key'"
    // 200 = 유효, 400/403 = 무효
}
```

### 키 형식
- 접두사: `AIzaSy`
- 총 길이: 39자
- 문자: 영숫자 + `_` + `-`
- 출처: https://ai.google.dev/gemini-api/docs/api-key

## 6. Telegram 봇 토큰 검증

```kotlin
object TelegramTokenValidator {
    /** 형식 검증: "123456789:ABCdefGHI..." */
    fun isValidFormat(token: String): Boolean {
        val parts = token.split(":")
        return parts.size == 2 &&
            parts[0].all { it.isDigit() } &&
            parts[0].length in 8..12 &&
            parts[1].length >= 20
    }

    /** BotFather 딥링크 */
    const val BOTFATHER_URL = "https://t.me/BotFather"

    /** User ID 확인 봇 */
    const val USERINFOBOT_URL = "https://t.me/userinfobot"
}
```

## 7. PRoot 파일시스템 경로

PocketEngine의 PRoot rootfs는 앱 internal storage에 위치:

```
Android 실제 경로:
/data/data/kr.co.palank.pocketserver/files/ubuntu-fs/

PRoot 내부 경로 (매핑):
/root/.picoclaw/config.json     ← PicoClaw 설정
/root/.openclaw/openclaw.json   ← OpenClaw 설정
/usr/local/bin/picoclaw         ← PicoClaw 바이너리
/var/log/picoclaw.log           ← PicoClaw 로그
```

Kotlin에서 직접 쓰기 가능:
```kotlin
val rootfsPath = File(context.filesDir, "ubuntu-fs")
val picoClawConfig = File(rootfsPath, "root/.picoclaw/config.json")
picoClawConfig.parentFile?.mkdirs()
picoClawConfig.writeText(configJson)
```

## 8. 서비스 프로세스 관리

### PicoClaw 시작/중지
```bash
# 시작 (백그라운드 데몬)
nohup picoclaw gateway > /var/log/picoclaw.log 2>&1 &
echo $! > /var/run/picoclaw.pid

# 중지
kill $(cat /var/run/picoclaw.pid) 2>/dev/null
rm -f /var/run/picoclaw.pid

# 상태 확인
if [ -f /var/run/picoclaw.pid ] && kill -0 $(cat /var/run/picoclaw.pid) 2>/dev/null; then
    echo "running"
else
    echo "stopped"
fi
```

### 자동 재시작 (서버 부팅 시)
ServerForegroundService가 PRoot을 시작한 후, 설치된 서비스도 자동 시작:
```bash
# /root/.pocketengine/autostart.sh (PRoot 부팅 시 실행)
if [ -x /usr/local/bin/picoclaw ] && [ -f /root/.picoclaw/config.json ]; then
    nohup picoclaw gateway > /var/log/picoclaw.log 2>&1 &
    echo $! > /var/run/picoclaw.pid
fi
```

## 9. IPC 확장 (서비스 상태 조회)

기존 IPC Protocol v1.0에 서비스 상태 명령 추가:

```json
// Monitor → Engine
{"cmd":"service_status"}

// Engine → Monitor
{
  "services": [
    {
      "id": "picoclaw",
      "name": "PicoClaw",
      "status": "running",
      "pid": 12345,
      "uptime": 3600
    }
  ]
}
```

## 10. 경쟁사 가격 비교 (2026.02 기준)

| 솔루션 | 월 비용 | 연 비용 | 비고 |
|--------|---------|---------|------|
| PocketEngine + PicoClaw | $0 | $0 | 구형폰 + 무료 Gemini API |
| GoClaw (OpenClaw Cloud) | $39 | $468 | 클라우드 호스팅 |
| VPS + OpenClaw 셀프호스트 | $5 | $60 | 터미널 지식 필요 |
| Raspberry Pi + OpenClaw | $50 초기 + $1 | $62 | 별도 하드웨어 구매 |

출처: https://www.getopenclaw.ai/pricing (GoClaw), https://www.digitalocean.com/pricing (VPS)

## 11. Gemini 무료 티어 주의사항

- 2025.12에 무료 할당량 50-80% 삭감 (Gemini 2.5 Pro: 500→100 RPD)
- 일일 쿼터는 태평양 표준시 자정 리셋
- 프로젝트 단위 적용 (API 키 단위가 아님)
- 개인 AI 비서 용도로는 250 RPD (Flash) 충분
- 출처: https://ai.google.dev/gemini-api/docs/rate-limits
