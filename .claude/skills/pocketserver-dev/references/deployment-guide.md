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
