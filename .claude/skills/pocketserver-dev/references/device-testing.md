# Device Testing Guide

> PocketServer 2-App 시스템의 실기기 빌드/설치/테스트 워크플로우

## Development Environment

### Android Studio Location
```
D:\Androidstudio\
D:\Androidstudio\jbr\           ← JAVA_HOME (bundled JBR)
D:\Androidstudio\jbr\bin\java.exe
```

### Android SDK Location
```
C:\Users\jayeo\AppData\Local\Android\Sdk\
C:\Users\jayeo\AppData\Local\Android\Sdk\platform-tools\adb.exe   ← ADB
C:\Users\jayeo\AppData\Local\Android\Sdk\build-tools\
C:\Users\jayeo\AppData\Local\Android\Sdk\platforms\
```

### Shell Environment Setup (Bash on Windows)

JAVA_HOME이 시스템 PATH에 없으므로 Gradle 빌드 전 반드시 설정:

```bash
export JAVA_HOME="D:/Androidstudio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

ADB alias (매번 전체 경로 쓰지 않기 위해):
```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
```

---

## Version Synchronization

Engine과 Monitor는 **반드시 versionCode/versionName을 동일하게** 유지해야 합니다.
IPC 프로토콜 호환성과 사용자 혼란 방지를 위한 규칙입니다.

### 버전 위치

| App | File | Path |
|-----|------|------|
| Engine | `pocket-server/app/build.gradle` | `defaultConfig { versionCode / versionName }` |
| Monitor | `pocket-monitor/app/build.gradle` | `defaultConfig { versionCode / versionName }` |

### 버전 변경 시 체크리스트

1. **양쪽 build.gradle** 모두 `versionCode`와 `versionName` 변경
2. Engine의 `version.json` (Firebase Hosting) 업데이트: `firebase-hosting/public/version.json`
3. Engine의 UpdateChecker가 참조하는 버전과 일치 확인
4. 커밋 메시지에 버전 번호 명시: `chore: bump version to 1.1 (versionCode 2)`

### 현재 버전

```
Engine:  versionCode=1, versionName="1.0.0"
Monitor: versionCode=1, versionName="1.0.0"
version.json: "1.0.0"
```

---

## Build Commands

### Engine (pocket-server) 빌드

```bash
export JAVA_HOME="D:/Androidstudio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "D:/010 Web Applicaton/android_linux/pocket-server"

# Debug APK
./gradlew.bat assembleDebug

# Release APK (keystore 필요)
./gradlew.bat assembleRelease

# APK 위치
# Debug:   app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

### Monitor (pocket-monitor) 빌드

```bash
export JAVA_HOME="D:/Androidstudio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "D:/010 Web Applicaton/android_linux/pocket-monitor"

# Debug APK
./gradlew.bat assembleDebug

# Release APK (keystore 필요)
./gradlew.bat assembleRelease

# APK 위치
# Debug:   app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

### 양쪽 동시 빌드 (병렬)

```bash
export JAVA_HOME="D:/Androidstudio/jbr" && export PATH="$JAVA_HOME/bin:$PATH"

# 순차 빌드 (같은 Gradle daemon 공유 시 병렬 불가)
cd "D:/010 Web Applicaton/android_linux/pocket-server" && ./gradlew.bat assembleDebug
cd "D:/010 Web Applicaton/android_linux/pocket-monitor" && ./gradlew.bat assembleDebug
```

---

## ADB Device Testing Workflow

### 1. 디바이스 연결 확인

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" devices
# Expected: ce0717174b8044200c  device   ← Galaxy S8
```

### 2. 앱 데이터 초기화 + 설치

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# Engine 앱 데이터 초기화 (rootfs, prefs 포함 전체 삭제)
"$ADB" shell pm clear kr.co.palank.pocketserver

# Monitor 앱 데이터 초기화
"$ADB" shell pm clear kr.co.palank.pocketmonitor

# Engine APK 설치 (덮어쓰기)
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-server/app/build/outputs/apk/debug/app-debug.apk"

# Monitor APK 설치 (덮어쓰기)
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-monitor/app/build/outputs/apk/debug/app-debug.apk"
```

### 3. 앱 실행

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# Engine 앱 실행
"$ADB" shell am start -n kr.co.palank.pocketserver/.MainActivity

# Monitor 앱 실행
"$ADB" shell am start -n kr.co.palank.pocketmonitor/.MainActivity
```

### 4. Logcat 모니터링

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# logcat 초기화
"$ADB" logcat -c

# Engine 핵심 태그만 필터링
"$ADB" logcat -s "InstallManager:*" "ProotManager:*" "ProotBinaryManager:*" "SwapManager:*" "DropbearManager:*" "SessionManager:*" "ServerForegroundService:*" "IpcServer:*" "AlertBroadcaster:*" "WatchdogWorker:*" "BatteryMonitor:*" "ResourceMonitor:*" "NetworkMonitor:*"

# Monitor 핵심 태그만 필터링
"$ADB" logcat -s "DashboardViewModel:*" "EngineConnector:*" "AlertReceiver:*" "DeviceMonitor:*" "AdManager:*" "DailyReportScheduler:*" "AlertNotifier:*"

# 전체 앱 로그 (패키지 기반)
"$ADB" logcat --pid=$("$ADB" shell pidof kr.co.palank.pocketserver)
"$ADB" logcat --pid=$("$ADB" shell pidof kr.co.palank.pocketmonitor)

# 크래시 로그만
"$ADB" logcat -s "AndroidRuntime:E" "FATAL:*"
```

### 5. 앱 강제 종료

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell am force-stop kr.co.palank.pocketserver
"$ADB" shell am force-stop kr.co.palank.pocketmonitor
```

### 6. 앱 제거

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" uninstall kr.co.palank.pocketserver
"$ADB" uninstall kr.co.palank.pocketmonitor
```

---

## Testing Scenarios

### Scenario 1: Engine 클린 설치 테스트

가장 빈번한 테스트. rootfs 다운로드 → PRoot 설정 → Dropbear SSH 전체 파이프라인 검증.

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 1. 데이터 완전 초기화
"$ADB" shell pm clear kr.co.palank.pocketserver

# 2. 최신 APK 설치
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-server/app/build/outputs/apk/debug/app-debug.apk"

# 3. logcat 준비
"$ADB" logcat -c

# 4. 앱 실행
"$ADB" shell am start -n kr.co.palank.pocketserver/.MainActivity

# 5. logcat 모니터링 (백그라운드)
"$ADB" logcat -s "InstallManager:*" "ProotManager:*" "ProotBinaryManager:*" "SwapManager:*" "DropbearManager:*"

# 6. 폰에서 "서버 설치 시작하기" 버튼 탭
# 7. 로그 확인:
#    - PRoot 바이너리 추출 (proot + loader + loader32)
#    - rootfs 다운로드 (~80MB)
#    - rootfs 압축 해제
#    - DNS/APT/locale/timezone 설정
#    - 사용자 생성 (pocketserver)
#    - 스왑 메모리 2GB 생성
#    - Dropbear SSH 설치 (apt-get 또는 Java-side .deb 추출)
#    - 설치 검증
```

**예상 소요시간**: 3~5분 (네트워크 속도에 따라)

**성공 기준**:
- `InstallManager: Installation completed successfully` 로그 출력
- 폰 화면에 SSH 접속 정보 표시 (IP, 포트 2022, 사용자명, 비밀번호)

**흔한 실패 원인**:
- 네트워크 불안정 → rootfs 다운로드 실패 (타임아웃)
- PRoot ENOSYS → Samsung 커널에서 fork/exec 실패 → Java-side .deb 추출로 폴백
- 저장공간 부족 → rootfs + swap = ~4GB 필요

### Scenario 2: IPC 통신 테스트

Engine + Monitor 양쪽 설치 후 LocalSocket IPC 연동 검증.

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 1. 양쪽 앱 설치 (Engine이 먼저 설치 + 서버 시작 상태여야 함)
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-server/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-monitor/app/build/outputs/apk/debug/app-debug.apk"

# 2. Engine 실행 → 서버 시작
"$ADB" shell am start -n kr.co.palank.pocketserver/.MainActivity

# 3. Monitor 실행
"$ADB" shell am start -n kr.co.palank.pocketmonitor/.MainActivity

# 4. IPC 로그 확인
"$ADB" logcat -s "IpcServer:*" "EngineConnector:*"
```

**성공 기준**:
- Monitor 대시보드에 서버 상태 "서비스 실행 중" 표시
- SSH 접속 정보 (IP:2022) 표시
- 시작/중지/재시작 버튼 동작

### Scenario 3: SSH 접속 테스트

Engine 서버 설치 완료 후 PC에서 SSH 접속 검증.

```bash
# 폰의 WiFi IP 확인
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell ip addr show wlan0 | grep "inet "

# PC에서 SSH 접속 (같은 WiFi)
ssh pocketserver@<PHONE_IP> -p 2022
# 비밀번호: 앱 화면에 표시된 12자리 비밀번호
```

### Scenario 4: Keep-Alive 테스트

서버가 화면 꺼짐/앱 스와이프 후에도 살아있는지 검증.

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 1. 서버 시작 확인
"$ADB" logcat -s "ServerForegroundService:*" | head -20

# 2. 화면 끄기
"$ADB" shell input keyevent KEYCODE_POWER

# 3. 30초 대기 후 서비스 상태 확인
sleep 30
"$ADB" shell dumpsys activity services kr.co.palank.pocketserver

# 4. Wake Lock 상태 확인
"$ADB" shell dumpsys power | grep -i "wake lock"
```

### Scenario 5: 온도 경고 / 자동정지 테스트

배터리 온도 모니터링 + 50°C 자동정지 동작 검증. (실환경에서 50°C 도달은 위험하므로 로그 기반 확인)

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 현재 배터리 온도 확인
"$ADB" shell dumpsys battery | grep temperature
# temperature: 280  ← 28.0°C (10으로 나눈 값)

# BatteryMonitor 로그 확인
"$ADB" logcat -s "BatteryMonitor:*"
```

---

## Debugging Tips

### PRoot "Function not implemented" (ENOSYS) 문제

Samsung 커널에서 PRoot의 ptrace 기반 syscall 인터셉트가 일부 동작하지 않음:
- `mkdir -p` → 서브프로세스 fork 실패
- `dpkg -i` → dpkg-split 서브프로세스 실행 실패
- `dpkg-deb -x` → chdir 실패

**해결**: Java 파일시스템 직접 조작으로 우회 (InstallManager.kt의 Strategy 2)

### rootfs 내부 파일 직접 확인

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# rootfs 디렉토리 확인 (run-as로 접근)
"$ADB" shell run-as kr.co.palank.pocketserver ls files/ubuntu/
"$ADB" shell run-as kr.co.palank.pocketserver ls files/ubuntu/usr/sbin/ | grep dropbear
"$ADB" shell run-as kr.co.palank.pocketserver ls files/ubuntu/etc/dropbear/
"$ADB" shell run-as kr.co.palank.pocketserver cat files/ubuntu/etc/resolv.conf
```

### SharedPreferences 확인

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell run-as kr.co.palank.pocketserver cat shared_prefs/pocketserver_prefs.xml
```

### 저장공간 사용량 확인

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell du -sh /data/data/kr.co.palank.pocketserver/files/
"$ADB" shell df /data
```

---

## Test Device

| Item | Value |
|------|-------|
| Device | Samsung Galaxy S8 |
| Device ID | ce0717174b8044200c |
| Android | 9 (Pie) |
| CPU | Exynos 8895 (ARM64) |
| RAM | 4GB |
| Known Issues | PRoot ENOSYS (fork/exec subprocess), getcwd() warning |
