# Dropbear 설치 디버깅 가이드

> 최종 갱신: 2026-02-10
> 상태: Strategy 2 (Java .deb extraction) — Python zstd 디코딩 실패, 디버깅 필요
> 파일: `pocket-server/app/src/main/java/kr/co/palank/pocketserver/linux/InstallManager.kt`

---

## 1. 현재 상태 요약

### 설치 파이프라인 (전체 흐름)

```
Step 1: PRoot 바이너리 추출 (assets → files/proot/)     ✅ 동작
Step 2: rootfs 다운로드 (80MB, ~30초)                    ✅ 동작
Step 3: rootfs 압축 해제 (~35초)                         ✅ 동작
Step 4: 시스템 설정 (DNS, locale, timezone, APT, user)   ✅ 동작
Step 5: 스왑 메모리 설정 (2GB, ~9초)                     ✅ 동작
Step 6: Dropbear SSH 설치                                ❌ 실패 중
  ├─ Strategy 1: apt-get install dropbear                ❌ dpkg ENOSYS (Samsung)
  └─ Strategy 2: Java .deb 추출 → PRoot+Python zstd     ❌ zstd 디코딩 실패
Step 7: 설치 검증                                        (미도달)
```

### 핵심 문제

Ubuntu 24.04 (Noble) 의 .deb 패키지들은 `data.tar.zst` (Zstandard 압축)를 사용한다.
Android 환경에서 zstd를 디코딩할 방법을 아직 찾지 못했다.

---

## 2. 시도한 zstd 디코딩 방법들

### 방법 1: zstd-jni (❌ 실패)
- 라이브러리: `com.github.luben:zstd-jni`
- 결과: `UnsatisfiedLinkError` — desktop JVM용 JNI 네이티브 라이브러리, Android NDK 미지원
- 비고: Android에서 절대 사용 불가

### 방법 2: aircompressor (❌ 실패)
- 라이브러리: `io.airlift:aircompressor:0.27`
- 결과: `NoSuchFieldError: No static field ARRAY_BYTE_BASE_OFFSET of type I in class Lsun/misc/Unsafe;`
- 비고: 순수 Java이나 `sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET` 사용 — Android에 해당 필드 없음
- build.gradle에 아직 의존성 남아 있음 (제거해도 됨)

### 방법 3: PRoot + Python3 + ctypes + libzstd.so (❌ 실패 — 현재 상태)
- 원리: rootfs에 이미 존재하는 `python3`와 `libzstd.so.1.5.5`를 PRoot으로 실행
- 구현: `zst_extract.py` 스크립트를 rootfs `/tmp`에 작성, PRoot으로 실행
- 결과: `ERROR: cannot determine decompressed size`
- 실패 위치: `ZSTD_getFrameContentSize()` 가 에러 값 반환 (>= 0xFFFFFFFFFFFFFFFE)

### 방법 4: 미검토 — Jammy (22.04) 패키지 사용
- Ubuntu 22.04 (Jammy) 패키지는 `data.tar.xz`를 사용할 수 있음 (zst 아닌 xz)
- 현재 `fallbackUrl`이 Jammy URL이지만, 실제로 xz인지 zst인지 확인 필요
- **만약 Jammy 패키지가 data.tar.xz라면 zstd 문제 자체를 우회 가능**

### 방법 5: 미검토 — 스트리밍 zstd 디코딩
- `ZSTD_getFrameContentSize()` 대신 `ZSTD_decompressStream()` 사용
- 프레임 헤더에 크기가 없는 경우에도 동작
- Python 스크립트 수정 필요

### 방법 6: 미검토 — Android NDK zstd 빌드
- zstd C 라이브러리를 Android NDK로 크로스 컴파일하여 JNI 래퍼 생성
- 가장 확실하지만 복잡도 높음

---

## 3. 현재 실패 지점 상세 분석

### 로그 (2026-02-10 16:10~16:13, Galaxy S8)

```
16:11:59.390 D ProotManager: exec: /bin/sh -c rm -f /etc/resolv.conf && ...     ← 시스템 설정 시작
16:12:00.119 I InstallManager: System configured, user 'pocketserver' created    ← 시스템 설정 완료
16:12:00.437 D SwapManager: Executing: dd if=/dev/zero of=/swapfile ...          ← 스왑 시작
16:12:10.563 I SwapManager: Swap memory setup complete (2048MB)                  ← 스왑 완료
16:12:10.566 D ProotManager: exec: /bin/sh -c apt-get update -qq                ← Strategy 1 시작
16:12:40.115 D ProotManager: exec result (code=100): ... E: Sub-process returned an error code
16:12:40.121 D ProotManager: exec: /bin/sh -c ... apt-get install ... dropbear  ← apt-get install
16:12:59.155 D ProotManager: exec result (code=100): ... E: Sub-process /usr/bin/dpkg returned an error code (100)
                                                                                 ← Strategy 1 실패 (dpkg ENOSYS)
16:12:59.155 W InstallManager: apt-get failed, falling back to Java .deb extraction (Strategy 2)
16:13:00.871 I InstallManager: Downloaded libtommath1: 59068 bytes from ...      ← .deb 다운로드 성공
16:13:00.893 I InstallManager: Extracted data.tar.zst (56689 bytes)              ← ar 파싱 성공
16:13:00.901 D ProotManager: exec: /bin/sh -c python3 /tmp/zst_extract.py /tmp/data.tar.zst  ← Python 실행
16:13:02.353 D ProotManager: exec result (code=1):                               ← ❌ Python 실패
16:13:02.353 D ProotManager: ERROR: cannot determine decompressed size           ← ZSTD_getFrameContentSize 에러
16:13:02.355 E InstallManager: Failed to extract libtommath1: zstd 추출 실패 (Python)
16:13:02.364 E InstallManager: Installation failed                               ← 설치 중단
```

### 실패 원인 가설

| # | 가설 | 가능성 | 확인 방법 |
|---|------|--------|----------|
| 1 | data.tar.zst 파일이 손상/절단됨 | 중간 | Python에서 파일 크기 + 첫 4바이트(매직넘버) 출력 |
| 2 | PRoot 파일시스템 매핑 문제 | 중간 | `ls -la /tmp/data.tar.zst` 실행, 실제 크기 확인 |
| 3 | zstd 프레임에 content size 헤더 없음 | 높음 | 스트리밍 API로 전환 (ZSTD_decompressStream) |
| 4 | ctypes 타입 정의 불일치 (c_void_p vs 실제 char*) | 낮음 | `buf`를 `bytes` 그대로 전달해보기 |
| 5 | libzstd.so.1 로딩 실패 또는 버전 문제 | 낮음 | `lib = ctypes.CDLL()` 전후로 에러 출력 |

### 가장 유력한 원인: 가설 3

dpkg/apt가 생성하는 zstd 프레임은 **content size를 헤더에 포함하지 않는 경우가 많다**.
zstd CLI로 `--no-content-size` 옵션이 기본이 아니지만, 파이프라인으로 압축할 때 크기 정보가 빠질 수 있다.

`ZSTD_getFrameContentSize()`의 반환값:
- `ZSTD_CONTENTSIZE_UNKNOWN` = 0xFFFFFFFFFFFFFFFE → 크기 정보 없음
- `ZSTD_CONTENTSIZE_ERROR` = 0xFFFFFFFFFFFFFFFF → 프레임 파싱 에러

→ **스트리밍 디코딩 (ZSTD_decompressStream)으로 전환하면 해결될 가능성이 높다.**

---

## 4. 디버깅 방법

### 4.1 로그 확인 (ADB Logcat)

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# logcat 초기화 + 필터링 실행
"$ADB" logcat -c
"$ADB" logcat -s "InstallManager:*" "ProotManager:*" "ProotBinaryManager:*" "SwapManager:*" "DropbearManager:*"

# 앱 실행 (다른 터미널에서)
"$ADB" shell am start -n kr.co.palank.pocketserver/.MainActivity

# 폰에서 "서버 설치 시작하기" 탭 → logcat에서 실시간 확인
```

### 4.2 로그를 파일로 저장

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 로그를 파일로 저장 (Ctrl+C로 중단)
"$ADB" logcat -s "InstallManager:*" "ProotManager:*" "ProotBinaryManager:*" "SwapManager:*" > install_log.txt

# 저장된 로그 확인
cat install_log.txt
```

### 4.3 rootfs 내부 파일 확인

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# python3 존재 확인
"$ADB" shell run-as kr.co.palank.pocketserver ls -la files/ubuntu/usr/bin/python3

# libzstd.so 존재 확인
"$ADB" shell run-as kr.co.palank.pocketserver ls -la files/ubuntu/usr/lib/aarch64-linux-gnu/libzstd*

# /tmp 디렉토리 확인
"$ADB" shell run-as kr.co.palank.pocketserver ls -la files/ubuntu/tmp/

# zst_extract.py 내용 확인
"$ADB" shell run-as kr.co.palank.pocketserver cat files/ubuntu/tmp/zst_extract.py

# data.tar.zst 파일 크기 확인 (설치 중에만 존재)
"$ADB" shell run-as kr.co.palank.pocketserver ls -la files/ubuntu/tmp/data.tar.zst
```

### 4.4 클린 설치 테스트 (데이터 초기화)

```bash
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 앱 데이터 완전 삭제 (rootfs, prefs 포함)
"$ADB" shell pm clear kr.co.palank.pocketserver

# 최신 APK 설치
"$ADB" install -r "D:/010 Web Applicaton/android_linux/pocket-server/app/build/outputs/apk/debug/app-debug.apk"

# logcat 준비
"$ADB" logcat -c

# 앱 실행
"$ADB" shell am start -n kr.co.palank.pocketserver/.MainActivity

# logcat 모니터링
"$ADB" logcat -s "InstallManager:*" "ProotManager:*" "ProotBinaryManager:*" "SwapManager:*"
```

### 4.5 빌드 명령

```bash
export JAVA_HOME="D:/Androidstudio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "D:/010 Web Applicaton/android_linux/pocket-server"
./gradlew.bat assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. 수정 방안 (다음 세션에서 시도할 것)

### 방안 A: Python 스트리밍 zstd (권장, 가장 유력)

`ZSTD_getFrameContentSize` 대신 `ZSTD_decompressStream`을 사용하는 스트리밍 방식으로 전환.
프레임에 content size가 없어도 동작한다.

`zst_extract.py` 수정 방향:

```python
import ctypes, tarfile, io, sys

lib = ctypes.CDLL('libzstd.so.1')

# 스트리밍 API
lib.ZSTD_createDStream.restype = ctypes.c_void_p
lib.ZSTD_freeDStream.argtypes = [ctypes.c_void_p]
lib.ZSTD_initDStream.argtypes = [ctypes.c_void_p]
lib.ZSTD_initDStream.restype = ctypes.c_size_t
lib.ZSTD_decompressStream.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
lib.ZSTD_decompressStream.restype = ctypes.c_size_t
lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
lib.ZSTD_isError.restype = ctypes.c_uint
lib.ZSTD_DStreamOutSize.restype = ctypes.c_size_t

class ZSTD_inBuffer(ctypes.Structure):
    _fields_ = [("src", ctypes.c_void_p), ("size", ctypes.c_size_t), ("pos", ctypes.c_size_t)]

class ZSTD_outBuffer(ctypes.Structure):
    _fields_ = [("dst", ctypes.c_void_p), ("size", ctypes.c_size_t), ("pos", ctypes.c_size_t)]

src_path = sys.argv[1]
with open(src_path, 'rb') as f:
    compressed = f.read()

print('DEBUG: file size = {} bytes, magic = {}'.format(
    len(compressed), compressed[:4].hex() if len(compressed) >= 4 else 'too short'), file=sys.stderr)

dstream = lib.ZSTD_createDStream()
lib.ZSTD_initDStream(dstream)

out_size = lib.ZSTD_DStreamOutSize()
out_buf_data = ctypes.create_string_buffer(out_size)

in_buf_data = ctypes.create_string_buffer(compressed)
in_buf = ZSTD_inBuffer(ctypes.cast(in_buf_data, ctypes.c_void_p), len(compressed), 0)

result_chunks = []
while in_buf.pos < in_buf.size:
    out_buf = ZSTD_outBuffer(ctypes.cast(out_buf_data, ctypes.c_void_p), out_size, 0)
    ret = lib.ZSTD_decompressStream(dstream, ctypes.byref(out_buf), ctypes.byref(in_buf))
    if lib.ZSTD_isError(ret):
        lib.ZSTD_freeDStream(dstream)
        print('ERROR: decompressStream failed (ret={})'.format(ret), file=sys.stderr)
        sys.exit(1)
    if out_buf.pos > 0:
        result_chunks.append(ctypes.string_at(out_buf.dst, out_buf.pos))

lib.ZSTD_freeDStream(dstream)

decompressed = b''.join(result_chunks)
print('DEBUG: decompressed {} -> {} bytes'.format(len(compressed), len(decompressed)), file=sys.stderr)

with tarfile.open(fileobj=io.BytesIO(decompressed)) as tf:
    tf.extractall('/')

print('OK: {} -> {} bytes'.format(len(compressed), len(decompressed)))
```

### 방안 B: Jammy (22.04) 패키지 사용

Noble (24.04) 패키지가 `data.tar.zst`를 사용하기 때문에 문제가 발생한다.
Jammy (22.04) 패키지는 `data.tar.xz`를 사용할 수 있다.

확인 방법:
1. 브라우저에서 Jammy fallback URL들을 다운로드
2. `.deb` 파일을 `ar` 으로 열어서 `data.tar.*` 확장자 확인
3. `data.tar.xz`라면 Java의 `XZInputStream`으로 바로 처리 가능 → zstd 문제 완전 우회

fallback URL들:
- `https://ports.ubuntu.com/ubuntu-ports/pool/main/libt/libtommath/libtommath1_1.2.0-3_arm64.deb`
- `https://ports.ubuntu.com/ubuntu-ports/pool/universe/libt/libtomcrypt/libtomcrypt1_1.18.2-5_arm64.deb`
- `https://ports.ubuntu.com/ubuntu-ports/pool/universe/d/dropbear/dropbear-bin_2020.81-5_arm64.deb`

참고: rootfs는 Noble (24.04)인데 Jammy (22.04) 패키지를 설치해도 동작할 가능성이 높다 (libc 호환).

### 방안 C: 디버그 정보 추가 후 재실행

현재 Python 스크립트에 디버그 출력을 추가하여 정확한 실패 원인 파악:

```python
# 파일 크기, 매직 넘버, ZSTD_getFrameContentSize 반환값을 stderr로 출력
print(f'DEBUG: file={src_path}, size={len(src)}, magic={src[:4].hex()}', file=sys.stderr)
print(f'DEBUG: ZSTD_getFrameContentSize returned: {dst_size} (hex: {hex(dst_size)})', file=sys.stderr)
```

zstd 매직 넘버: `28 B5 2F FD` (0xFD2FB528 little-endian)
→ 매직 넘버가 다르면 파일 자체가 손상되거나 잘못된 데이터

### 방안 D: Android NDK zstd

가장 확실하지만 복잡도 높음:
1. zstd C 소스코드 다운로드 (github.com/facebook/zstd)
2. Android NDK로 arm64-v8a 타겟 크로스 컴파일
3. JNI 래퍼 작성
4. `.so` 파일을 app의 `jniLibs/arm64-v8a/`에 배치

---

## 6. 확인된 작동 URL

| 리소스 | URL | 상태 |
|--------|-----|------|
| rootfs | `https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz` | ✅ 80MB 다운로드 성공 |
| libtommath1 (Noble) | `https://ports.ubuntu.com/ubuntu-ports/pool/main/libt/libtommath/libtommath1_1.2.0-6build3_arm64.deb` | ✅ 59068 bytes 다운로드 성공 |
| libtomcrypt1 (Noble) | `https://ports.ubuntu.com/ubuntu-ports/pool/universe/libt/libtomcrypt/libtomcrypt1_1.18.2+dfsg-7build1_arm64.deb` | 미테스트 (첫 패키지에서 실패하여 미도달) |
| dropbear-bin (Noble) | `https://ports.ubuntu.com/ubuntu-ports/pool/universe/d/dropbear/dropbear-bin_2022.83-4_arm64.deb` | 미테스트 |
| libtommath1 (Jammy) | `https://ports.ubuntu.com/ubuntu-ports/pool/main/libt/libtommath/libtommath1_1.2.0-3_arm64.deb` | 미테스트 |
| libtomcrypt1 (Jammy) | `https://ports.ubuntu.com/ubuntu-ports/pool/universe/libt/libtomcrypt/libtomcrypt1_1.18.2-5_arm64.deb` | 미테스트 |
| dropbear-bin (Jammy) | `https://ports.ubuntu.com/ubuntu-ports/pool/universe/d/dropbear/dropbear-bin_2020.81-5_arm64.deb` | 미테스트 |

URL 규칙: `https://ports.ubuntu.com/ubuntu-ports/pool/` (`/ubuntu-ports/` 포함 필수)

---

## 7. rootfs에 존재하는 유용한 도구들

Galaxy S8 테스트에서 확인됨:

| 경로 (rootfs 기준) | 설명 |
|---------------------|------|
| `usr/bin/python3` | Python 3 인터프리터 |
| `usr/lib/aarch64-linux-gnu/libzstd.so.1.5.5` | zstd 공유 라이브러리 |
| `usr/bin/tar` | GNU tar (zstd 지원 여부 미확인) |
| `usr/bin/zstd` | zstd CLI 도구 (존재 여부 미확인) |

**확인 필요**: `tar --zstd` 옵션이 rootfs의 tar에서 지원되는지 → 지원되면 Python 스크립트 없이 `tar --zstd -xf /tmp/data.tar.zst -C /` 로 한 줄로 해결 가능

```bash
# rootfs의 tar가 zstd를 지원하는지 확인
ADB="C:/Users/jayeo/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell run-as kr.co.palank.pocketserver ls files/ubuntu/usr/bin/zstd
"$ADB" shell run-as kr.co.palank.pocketserver ls files/ubuntu/usr/bin/zstdcat
```

만약 `zstd` 또는 `zstdcat`이 존재하면, PRoot에서 직접:
```
zstdcat /tmp/data.tar.zst | tar -xf - -C /
```
또는:
```
tar --zstd -xf /tmp/data.tar.zst -C /
```

이 방법이 Python ctypes보다 훨씬 간단하고 안정적이다.

---

## 8. 테스트 디바이스 정보

| 항목 | 값 |
|------|-------|
| Device | Samsung Galaxy S8 |
| Device ID | ce0717174b8044200c |
| Android | 9 (Pie) |
| CPU | Exynos 8895 (ARM64) |
| RAM | 4GB |
| Known Issues | PRoot ENOSYS (fork/exec/chdir), `getcwd() failed: Function not implemented` 경고 (무해) |

---

## 9. 다음 세션 작업 순서 (권장)

```
1. 이 문서 읽기
2. rootfs에 zstd/zstdcat/tar --zstd 지원 여부 확인 (ADB로)
   → 있으면: Python 스크립트 대신 tar --zstd 또는 zstdcat | tar 사용 (가장 간단)
   → 없으면: 방안 A (스트리밍 Python) 또는 방안 B (Jammy xz 패키지) 시도
3. InstallManager.kt 수정
4. 빌드 (gradlew.bat assembleDebug)
5. 클린 설치 테스트 (pm clear → install → logcat)
6. 성공 시: 전체 파이프라인 검증 (SSH 접속까지)
```

---

## 10. InstallManager.kt 구조 요약

```
InstallManager.kt (644줄)
├── InstallState (sealed class) — Idle/Progress/Completed/Error
├── DebPackage (data class) — name, primaryUrl, fallbackUrl
├── companion object
│   ├── ROOTFS_URL — Ubuntu 24.04 minimal ARM64 rootfs
│   ├── PORTS_BASE — "https://ports.ubuntu.com/ubuntu-ports/pool"
│   └── DROPBEAR_DEBS — 3개 패키지 (libtommath1, libtomcrypt1, dropbear-bin)
├── startInstallation() — 전체 설치 파이프라인 오케스트레이터
├── downloadRootfs() — HTTP 다운로드 + 진행률 보고
├── extractRootfs() — tar.xz 압축 해제 (symlink 처리 포함)
├── configureSystem() — DNS, locale, timezone, APT sandbox, useradd
├── setupSwap() — SwapManager 호출 (2GB dd)
├── installDropbear() — Strategy 1 (apt-get) → Strategy 2 (Java .deb) 폴백
│   ├── tryAptGetInstall() — PRoot 내 apt-get install dropbear
│   └── tryJavaDebExtract() — Java ArArchiveInputStream + Python zstd
│       ├── downloadDebPackage() — HTTP에서 .deb 다운로드
│       ├── extractDataTarFromDeb() — ar 아카이브에서 data.tar.* 추출
│       ├── ensureZstExtractScript() — Python zstd 스크립트 /tmp에 작성
│       ├── extractZstViaProot() — PRoot으로 Python 실행 ← ❌ 여기서 실패
│       └── extractDataTarToRootfs() — xz/gz data.tar 직접 풀기 (Java)
├── configureDropbear() — 포트 2022 설정, 호스트 키 생성
├── verifyInstallation() — bash echo, dropbear binary, user id 확인
└── reset() — rootfs 삭제 + prefs 초기화
```

### 현재 build.gradle 의존성 (관련)

```groovy
implementation "org.apache.commons:commons-compress:1.26.0"  // ar/tar 파싱 ✅ 동작
implementation "org.tukaani:xz:1.9"                          // xz 디코딩 ✅ 동작
implementation "io.airlift:aircompressor:0.27"               // zstd ❌ Android 불가, 제거 가능
```
