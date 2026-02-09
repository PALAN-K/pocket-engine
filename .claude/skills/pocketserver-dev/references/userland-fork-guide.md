# UserLand Fork Guide

> Source: github.com/CypherpunkArmory/UserLAnd (MIT License)
> Last open-source version: v2.8.3 (October 2021)

## Table of Contents
- Repository Structure
- Key Components to Keep
- Components to Remove
- PRoot Architecture
- Dropbear SSH Integration
- Asset Distribution Model
- Build System

## Repository Structure

```
UserLAnd/
├── app/src/main/
│   ├── java/tech/ula/
│   │   ├── model/          # Room DB entities, repositories
│   │   ├── ui/             # Activities, Fragments, ViewModels
│   │   ├── utils/          # Filesystem, PRoot, network utilities
│   │   └── viewmodel/      # MVVM ViewModels
│   ├── res/                # Android resources (layouts, strings, drawables)
│   └── AndroidManifest.xml
├── build.gradle
└── gradle/
```

Language: Kotlin (52%) + Java (47%), ~20,000 LOC total.

## Key Components to Keep

### PRoot Execution (`utils/`)
- `ProotUtils.kt` — Launches PRoot process with correct args
- `FilesystemManager.kt` — Downloads/extracts rootfs tarballs
- `ServerUtility.kt` — Manages Dropbear/VNC server processes

### Session Management (`model/`)
- `Session.kt` — Room entity for active sessions
- `Filesystem.kt` — Room entity for installed filesystems
- `AppDatabase.kt` — Room database definition

### Service Layer
- `ServerService.kt` — Android Service that runs PRoot + servers
- Notification management for foreground service

## Components to Remove

| Component | Location | Reason |
|-----------|----------|--------|
| VNC server support | `ServerUtility.kt` (VNC paths) | Not needed for SSH-only |
| bVNC client | Dependency in `build.gradle` | External SSH client used instead |
| ConnectBot client | Dependency in `build.gradle` | External SSH client used instead |
| Desktop env setup | Asset scripts for XFCE/LXDE | CLI server only |
| App shortcuts | `model/App.kt`, UI for GIMP/Firefox | Not needed |
| Multiple distro selection | `ui/` filesystem creation dialogs | Ubuntu 24.04 fixed |
| Kali/Alpine/Arch/Debian assets | Asset repos | Only Ubuntu 24.04 |

## PRoot Architecture

PRoot intercepts syscalls via `ptrace()` to fake root inside a container:

```
Android App Process
└── PRoot binary (ARM64)
    ├── ptrace() syscall interception
    ├── Fake chroot to Ubuntu rootfs
    ├── Bind mounts: /dev, /proc, /sys, /sdcard
    └── Ubuntu 24.04 userspace
        ├── apt package manager
        ├── Dropbear SSH (port 2022)
        └── User-installed services
```

Key PRoot launch arguments (from UserLand source):
```
proot
  --link2symlink
  --kill-on-exit
  -0                          # Fake root (UID 0)
  -r <rootfs_path>            # Root filesystem
  -b /dev
  -b /proc
  -b /sys
  -b /sdcard
  -b /storage
  -w /root
  /usr/bin/env -i
  HOME=/root
  TERM=xterm-256color
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
  /bin/bash --login
```

## Dropbear SSH Integration

UserLand uses Dropbear (lightweight SSH, ~110KB binary) instead of OpenSSH:

- **Binary location**: Bundled in app assets, extracted to app's private directory
- **Default port**: 2022 (Android restricts ports < 1024)
- **Auto-configured**: UserLand generates host keys and starts Dropbear automatically
- **Config**: Minimal — password auth enabled by default

Dropbear launch command (from UserLand):
```
dropbear -E -p 2022 -R -F
```
Flags: `-E` log to stderr, `-p` port, `-R` generate hostkey on first run, `-F` foreground

## Asset Distribution Model

UserLand downloads rootfs from separate GitHub repos:
- `UserLAnd-Assets-Support` — Support scripts, PRoot binary
- `UserLAnd-Assets-Ubuntu` — Ubuntu rootfs tarball
- `UserLAnd-Assets-Debian` — Debian rootfs (remove)
- `UserLAnd-Assets-Arch` — Arch rootfs (remove)

For PocketServer, we only need Ubuntu 24.04 assets. Update the asset download URLs to point to Ubuntu 24.04 rootfs.

### Official Ubuntu rootfs source
```
https://cloud-images.ubuntu.com/minimal/releases/24.04/release/
ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz
```

## Build System

- Gradle with Kotlin DSL
- minSdkVersion: 24 (Android 7.0) — consider raising to 26 (Android 8.0)
- targetSdkVersion: needs updating from original (was 30, update to 34+)
- Dependencies to add: Jetpack Compose, Google AdMob SDK, WorkManager
- Dependencies to remove: bVNC, ConnectBot
