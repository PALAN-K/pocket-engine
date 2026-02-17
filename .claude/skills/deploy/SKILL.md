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

## R2 상세
- 버킷: `pocketserver-apk` (퍼블릭)
- Wrangler 인증 만료 시: `npx wrangler login`

## 시나리오별 최소 체크리스트

**A. Engine만 (버전 유지)**: assembleRelease → R2 `--remote` → curl 확인

**B. Engine만 (버전 업)**: 3곳 버전 수정 → assembleRelease → R2 `--remote` → `firebase deploy --only hosting`

**C. 양쪽 (버전 업)**: 3곳 버전 수정 → 양쪽 assembleRelease → R2 `--remote` → Play Console 업로드 → `firebase deploy --only hosting`
