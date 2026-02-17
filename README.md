# PocketEngine

**Turn your old Android phone into a Linux server with one tap.**

PocketEngine converts unused smartphones (Galaxy S8, Pixel 3, etc.) into always-on Ubuntu servers — no root required. Just install the APK, tap "Install Server", and SSH in from your PC.

## What It Does

- **One-tap Ubuntu 24.04 LTS** installation via PRoot (no root needed)
- **Dropbear SSH server** on port 2022, auto-configured with generated credentials
- **7-layer keep-alive system** keeps the server running 24/7, even with the screen off
- **Thermal protection** auto-stops at 50°C to prevent overheating
- **Boot auto-start** — server comes back up after phone reboots
- **Manufacturer-specific optimization** guides for Samsung, Xiaomi, Huawei, OPPO

## Use Cases

- Run AI agents (OpenClaw, PicoClaw) on a dedicated device
- Host lightweight web apps and APIs
- Self-host n8n, Dify, LobeChat, or any Linux service
- Personal dev/test server at $0/month

## Requirements

| Item | Minimum | Recommended |
|------|---------|-------------|
| Android | 8.0 (Oreo) | 10.0+ |
| CPU | ARM64 (64-bit) | — |
| RAM | 3 GB | 4 GB+ |
| Storage | 8 GB free | 16 GB+ free |
| Network | WiFi | 5 GHz WiFi |

## Download

Get the latest APK from the [Releases](https://github.com/PALAN-K/pocket-engine/releases) page.

> PocketEngine is sideloaded (not on Google Play) because it uses PRoot and background services that violate Play Store policies. This is the same reason Termux and UserLand were removed from the Play Store.

## Building from Source

### Prerequisites

- Android Studio (Arctic Fox or later)
- JDK 17 (Android Studio's bundled JBR works)
- Android SDK with API 34

### Steps

```bash
git clone https://github.com/PALAN-K/pocket-engine.git
cd pocket-engine
```

**Firebase Crashlytics (optional):**

Crashlytics is used for crash reporting. To build without it, you can skip this step — the build will succeed without `google-services.json`, but crash reporting will be disabled.

To enable Crashlytics:
1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `kr.co.palank.pocketserver`
3. Download `google-services.json` and place it in `app/`

**Build:**

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

**Install on device:**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
kr.co.palank.pocketserver/
├── MainActivity.kt              Setup wizard host
├── ui/
│   ├── setup/
│   │   └── SetupWizardScreen.kt  One-tap install wizard
│   └── servicestore/             AI agent installer (Service Store)
├── service/
│   ├── ServerForegroundService.kt  7-layer keep-alive
│   ├── ServiceManager.kt          Service process management
│   └── WatchdogWorker.kt          15-min health check
├── linux/
│   ├── ProotBinaryManager.kt    PRoot binary extraction
│   ├── ProotManager.kt          PRoot process management
│   ├── InstallManager.kt        Ubuntu installation
│   ├── DropbearManager.kt       Dropbear SSH management
│   ├── SwapManager.kt           Swap memory management
│   └── SessionManager.kt        State machine
├── ipc/
│   ├── IpcServer.kt             LocalSocket server (for PocketMonitor)
│   └── AlertBroadcaster.kt      Broadcast alerts to PocketMonitor
├── catalog/
│   ├── ServiceCatalog.kt        Service definitions
│   ├── PicoClawInstaller.kt     PicoClaw one-tap installer
│   └── OpenClawInstaller.kt     OpenClaw one-tap installer
├── monitor/
│   ├── ResourceMonitor.kt       CPU/RAM monitoring
│   └── NetworkMonitor.kt        WiFi IP detection
├── manufacturer/
│   ├── ManufacturerDetector.kt   OEM detection
│   └── ManufacturerOptimizationHelper.kt  Deep link helper
└── util/
    ├── SpecChecker.kt            Hardware spec validation
    ├── BatteryMonitor.kt         Thermal monitoring (50°C auto-stop)
    └── UpdateChecker.kt          Version check
```

### 7-Layer Keep-Alive System

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| 1 | Foreground Service + Notification | Prevent OS kill |
| 2 | Partial Wake Lock | Keep CPU active with screen off |
| 3 | WiFi Lock | Maintain network with screen off |
| 4 | Battery Optimization Exemption | Bypass Doze mode |
| 5 | START_STICKY + onTaskRemoved | Auto-restart on termination |
| 6 | BOOT_COMPLETED Receiver | Auto-start on reboot |
| 7 | Manufacturer-specific deep links | Samsung/Xiaomi/Huawei/OPPO handling |

### IPC Protocol

PocketEngine exposes a LocalSocket server (`pocketserver_ipc`) for the companion [PocketMonitor](https://play.google.com/store/apps/details?id=kr.co.palank.pocketmonitor) app. The protocol uses newline-delimited JSON:

```json
// Status query
→ {"cmd":"status"}
← {"state":"running","cpu":12,"ram":62,"temp":38,"uptime":123456,
   "ip":"192.168.0.15","port":2022,"user":"pocketserver"}

// Server control
→ {"cmd":"start"}
← {"ok":true}
```

See [IPC Protocol documentation](docs/IPC_PROTOCOL.md) for the full spec.

## Companion App: PocketMonitor

[PocketMonitor](https://play.google.com/store/apps/details?id=kr.co.palank.pocketmonitor) is the companion monitoring app available on Google Play. It provides:

- Real-time device health dashboard (CPU, RAM, temperature, storage)
- Server status monitoring via IPC
- Daily safety reports via push notifications
- Temperature alerts

PocketMonitor is closed-source and not part of this repository.

## Credits

- [PRoot](https://proot-me.github.io/) — User-space implementation of chroot, mount --bind, and binfmt_misc
- [UserLand](https://github.com/CypherpunkArmory/UserLAnd) (MIT) — Architecture reference for PRoot + Dropbear on Android
- [Dropbear](https://matt.ucc.asn.au/dropbear/dropbear.html) — Lightweight SSH server
- [dontkillmyapp.com](https://dontkillmyapp.com/) — Manufacturer-specific background process handling

## Contributing

We welcome contributions! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Safety Notice

Running a server on a phone 24/7 generates heat. Please follow these precautions:

- Use a smart plug to limit charging to 70-80%
- Remove the phone case for better heat dissipation
- Place the phone on a metal or tile surface
- Inspect the battery monthly for swelling
- The app auto-stops at 50°C, but physical precautions are still important
