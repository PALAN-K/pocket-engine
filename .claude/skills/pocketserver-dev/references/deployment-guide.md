# PocketServer Deployment Guide

## Prerequisites
- JAVA_HOME: `D:\Androidstudio\jbr`
- ADB: `C:\Users\jayeo\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Firebase CLI: `firebase` (installed globally via npm)
- Wrangler CLI: `npx wrangler` (Cloudflare Workers CLI)
- Keystore: `keystore/pocketserver-release.jks` (shared signing key, gitignored)

## Version Sync Rule
Both apps MUST have matching `versionCode` and `versionName`. Update in:
1. `pocket-server/app/build.gradle` (Engine)
2. `pocket-monitor/app/build.gradle` (Monitor)
3. `firebase-hosting/public/version.json` (Engine auto-update check)

## Release Steps

### Step 1: Version Bump
- Increment `versionCode` (integer, must always increase)
- Update `versionName` (semver string: "X.Y.Z")
- Update `version.json` with new version + changelog

### Step 2: Build Release APKs
```bash
# Engine
export JAVA_HOME="/d/Androidstudio/jbr"
cd pocket-server && ./gradlew assembleRelease

# Monitor
cd pocket-monitor && ./gradlew assembleRelease
```

Output paths:
- Engine: `pocket-server/app/build/outputs/apk/release/app-release.apk`
- Monitor: `pocket-monitor/app/build/outputs/apk/release/app-release.apk`

### Step 3: Upload Engine APK to Cloudflare R2
```bash
npx wrangler r2 object put pocketserver-apk/pocketserver-engine.apk \
  --file="pocket-server/app/build/outputs/apk/release/app-release.apk" \
  --content-type="application/vnd.android.package-archive" \
  --remote
```

Verify: https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk

### Step 4: Deploy Firebase Hosting (version.json + download page)
```bash
firebase deploy --only hosting
```

Deploys to: https://pocket-server-palank.web.app
- version.json (Engine auto-update check)
- index.html (download page)
- privacy-ko.html, privacy-en.html

### Step 5: Test on Device
```bash
# Via ADB
adb install -r pocket-server/app/build/outputs/apk/release/app-release.apk
adb install -r pocket-monitor/app/build/outputs/apk/release/app-release.apk
```

**CRITICAL: ADB install 안전 규칙**

1. **`adb install` 도중 절대 프로세스를 강제 종료(kill/TaskStop)하지 말 것.**
   설치 도중 중단하면 기존 앱 데이터(`/data/data/kr.co.palank.pocketserver/files/`)가
   손상되거나 초기화될 수 있다. Engine의 경우 rootfs(Ubuntu), 서비스 config(PicoClaw/OpenClaw),
   SSH 키 등이 모두 이 경로에 있으므로 복구 불가.

2. **타임아웃을 충분히 설정할 것 (최소 5분, 300000ms).**
   APK 크기(Engine ~14MB)와 디바이스 성능(Galaxy S8 등 구형폰)에 따라
   `adb install`이 1~3분 소요될 수 있다. 2분 타임아웃은 부족할 수 있음.

3. **`adb install -r`은 서명 동일 시 앱 데이터를 보존한다.**
   `-r` 플래그 = replace(덮어쓰기). 동일 서명이면 기존 데이터 유지.
   서명 불일치 시 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 에러로 거부됨(데이터 손실 없음).

4. **`adb uninstall`은 앱 데이터를 완전 삭제한다.**
   rootfs, config, SSH 키 등 모든 사용자 데이터가 날아간다.
   서명 불일치 해결 외에는 사용 금지. 실행 전 사용자 확인 필수.

NOTE: If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the existing app was signed with a different key. Uninstall first:
```bash
adb uninstall kr.co.palank.pocketserver
adb uninstall kr.co.palank.pocketmonitor
```

### Step 6: Monitor APK → Google Play Console
- Upload to Play Console: https://play.google.com/console
- Track: Internal testing → Closed testing → Production
- NOT YET SUBMITTED (as of v1.1.0)

## Deployment URLs

| Resource | URL |
|----------|-----|
| Engine download page | https://pocket-server-palank.web.app |
| Engine APK (R2) | https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/pocketserver-engine.apk |
| version.json (Hosting) | https://pocket-server-palank.web.app/version.json |
| Privacy Policy (KR) | https://pocket-server-palank.web.app/privacy-ko.html |
| Privacy Policy (EN) | https://pocket-server-palank.web.app/privacy-en.html |

## Cloudflare R2 Details
- Bucket: `pocketserver-apk`
- Public access: enabled
- Public URL prefix: `https://pub-832ccadf097e4bf687650db1e57df66b.r2.dev/`

## Firebase Project
- Project ID: `pocket-server-palank`
- Services: Hosting + Crashlytics
- Config file: `firebase.json` + `.firebaserc`

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-02-09 | Initial release |
| 1.1.0 | 2026-02-12 | Fix: networkInterfaces crash (libnetstub.so + net-fix.js global) |
