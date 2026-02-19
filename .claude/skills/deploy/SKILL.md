---
name: deploy
description: "PocketServer 릴리즈 빌드 및 배포. Engine APK를 Cloudflare R2에 업로드하고, Firebase Hosting에 다운로드 페이지/version.json을 배포하고, Monitor APK를 Play Console에 업로드하는 절차. Use when: 배포, 릴리즈, R2 업로드, Firebase deploy, Play Store 업로드, APK 빌드, 버전 업 요청 시."
---

# 배포 절차

## 빌드

```bash
export JAVA_HOME="/d/Androidstudio/jbr"
cd pocket-server && ./gradlew assembleRelease   # Engine
cd pocket-monitor && ./gradlew assembleRelease  # Monitor
```

- Engine APK: `pocket-server/app/build/outputs/apk/release/app-release.apk`
- Monitor APK: `pocket-monitor/app/build/outputs/apk/release/app-release.apk`

## Engine → Cloudflare R2

```bash
npx wrangler r2 object put pocketserver-apk/pocketserver-engine.apk \
  --file="pocket-server/app/build/outputs/apk/release/app-release.apk" \
  --content-type="application/vnd.android.package-archive" \
  --remote
```

**`--remote` 필수.** 기본값이 local이므로 빠뜨리면 실제 R2에 올라가지 않음.

확인: `curl -sI "https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk" | head -5` → 200 OK

## Firebase Hosting (version.json/다운로드 페이지 변경 시만)

```bash
firebase deploy --only hosting
```

대상: `firebase-hosting/public/` (index.html, version.json, privacy-*.html)

## Monitor → Play Console (Play Store 업데이트 시만)

Play Console에서 수동 업로드. `pocket-monitor/app/build/outputs/apk/release/app-release.apk`

## 실기기 테스트

```bash
adb install -r pocket-server/app/build/outputs/apk/release/app-release.apk
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → `adb uninstall kr.co.palank.pocketserver` 후 재설치 (디버그↔릴리즈 전환 시 발생).

## 주의사항

### 버전 동기화
양 앱은 동일 versionCode/versionName 유지. 버전 변경 시 3곳 동시 수정:
1. `pocket-server/app/build.gradle`
2. `pocket-monitor/app/build.gradle`
3. `firebase-hosting/public/version.json`

동일 버전 핫픽스면 변경 불필요.

### 서명
양 앱 동일 키스토어(`keystore/pocketserver-release.jks`) 필수. 불일치 시 IPC 서명 검증 실패.

### Firebase Hosting에 APK 직접 업로드 불가
Spark 무료 플랜 제한. APK는 반드시 R2 경유.

## URL

| 리소스 | URL |
|--------|-----|
| Engine APK (R2) | `https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk` |
| 다운로드 페이지 | `https://pocket-server-palank.web.app` |
| version.json | `https://pocket-server-palank.web.app/version.json` |

## Engine → PALAN-K 공개 레포

Engine 앱은 PALAN-K 조직의 공개 레포에도 배포한다.

```bash
# remote 추가 (최초 1회)
git remote add palan-k https://github.com/PALAN-K/pocket-engine.git

# 푸시
git push palan-k main
```

- 레포: https://github.com/PALAN-K/pocket-engine.git (public)
- origin(`naegeon/pocket-server`)과 별도로 관리
- R2 업로드 후 커밋 → palan-k 레포에도 푸시

## R2 상세
- 버킷: `pocketserver-apk` (퍼블릭)
- Wrangler 인증 만료 시: `npx wrangler login`

## SSH 디버깅 (실기기 원격 접속)

Engine이 설치된 실기기(PRoot Ubuntu)에 SSH로 접속하여 로그 확인/서비스 디버깅.

### 사전 조건
- 기기가 USB 연결되어 있거나 같은 WiFi
- ADB 포트 포워딩 또는 WiFi IP로 접속

### ADB 포트 포워딩

```bash
adb forward tcp:12022 tcp:2022
```

### Node.js ssh2로 접속 (Windows 환경 권장)

Windows에서는 `sshpass`가 없고 `ssh` 비밀번호 파이프도 불가. Node.js `ssh2` 패키지 사용.

```bash
# ssh2가 없으면 설치
npm install ssh2
```

```javascript
// ssh-exec.js — 사용법: node ssh-exec.js "명령어"
const { Client } = require('ssh2');
const cmd = process.argv[2] || 'echo hello';
const conn = new Client();
conn.on('ready', () => {
  conn.exec(cmd, {}, (err, stream) => {
    let out = '';
    stream.on('data', (d) => { out += d.toString(); });
    stream.stderr.on('data', (d) => { out += d.toString(); });
    stream.on('close', () => { console.log(out); conn.end(); });
  });
}).on('error', (err) => console.error(err.message))
.connect({
  host: '127.0.0.1',
  port: 12022,
  username: 'root',
  password: 'SSH_PASSWORD_HERE',
  algorithms: {
    kex: ['curve25519-sha256', 'diffie-hellman-group14-sha256', 'diffie-hellman-group14-sha1'],
    serverHostKey: ['ssh-ed25519', 'ecdsa-sha2-nistp256', 'ssh-rsa'],
    cipher: ['aes128-ctr', 'aes192-ctr', 'aes256-ctr']
  }
});
```

### SSH 비밀번호 확인
앱 설치 완료 화면에서 확인하거나, SharedPreferences에서 추출:
```bash
adb shell run-as kr.co.palank.pocketserver cat shared_prefs/pocketserver_prefs.xml | grep ssh_password
```

### 주요 디버깅 명령

```bash
# OpenClaw 로그 확인
node ssh-exec.js "cat /tmp/openclaw.log"
node ssh-exec.js "ls -la /tmp/openclaw/"
node ssh-exec.js "cat /tmp/openclaw/openclaw-$(date +%Y-%m-%d).log | tail -50"

# OpenClaw 설정 확인
node ssh-exec.js "cat /root/.openclaw/openclaw.json"
node ssh-exec.js "cat /root/.openclaw/.env"

# 프로세스 상태 확인
node ssh-exec.js "ps aux | grep -E 'openclaw|picoclaw|node'"

# OpenClaw 수동 실행 (디버깅용)
node ssh-exec.js "source /root/.openclaw/.env && NODE_OPTIONS='--require /usr/local/lib/openclaw-bionic-bypass.js' openclaw gateway run"
```

## 시나리오별 최소 체크리스트

**A. Engine만 (버전 유지)**: assembleRelease → R2 `--remote` → curl 확인 → git commit + push palan-k

**B. Engine만 (버전 업)**: 3곳 버전 수정 → assembleRelease → R2 `--remote` → `firebase deploy --only hosting` → git commit + push palan-k

**C. 양쪽 (버전 업)**: 3곳 버전 수정 → 양쪽 assembleRelease → R2 `--remote` → Play Console 업로드 → `firebase deploy --only hosting` → git commit + push palan-k
