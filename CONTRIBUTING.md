# Contributing to PocketEngine

Thank you for your interest in contributing to PocketEngine! This guide will help you get started.

## How to Contribute

### Reporting Bugs

- **Device compatibility issues** are especially valuable — we can't test every phone
- Use the [Bug Report](https://github.com/PALAN-K/pocket-engine/issues/new?template=bug_report.md) template
- Include: device model, Android version, RAM, and steps to reproduce

### Suggesting Features

- Use the [Feature Request](https://github.com/PALAN-K/pocket-engine/issues/new?template=feature_request.md) template
- Explain the use case, not just the solution

### Submitting Code

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Test on a real device (emulator is insufficient for keep-alive and IPC testing)
5. Submit a pull request

## Development Setup

### Prerequisites

- Android Studio (Arctic Fox or later)
- JDK 17
- Android SDK API 34
- A physical Android device (ARM64, Android 8.0+) for testing

### Building

```bash
git clone https://github.com/YOUR_USERNAME/pocket-engine.git
cd pocket-engine
./gradlew assembleDebug
```

The PRoot binary is automatically downloaded during the first build via the `downloadProot` Gradle task.

### Firebase (Optional)

Crashlytics is optional for development. The app builds and runs without `google-services.json`. If you want crash reporting during development, set up your own Firebase project.

## Code Style

### General

- **Kotlin** with coroutines for async operations
- **Jetpack Compose** for all UI
- **StateFlow** for UI state management (MVVM pattern)
- Keep it simple — no over-engineering or unnecessary abstractions

### Naming

- Composable functions: prefixed with context (e.g., `SetupWizardScreen`, `OptimizationGuideCard`)
- ViewModels: `*ViewModel` suffix
- Managers: `*Manager` suffix for system-level components

### Formatting

- 4-space indentation
- Follow standard Kotlin conventions ([kotlinlang.org/docs/coding-conventions.html](https://kotlinlang.org/docs/coding-conventions.html))
- No trailing whitespace
- Newline at end of file

### What NOT to Do

- Don't add VNC, desktop environments, or multiple distro selection
- Don't add ads or analytics (this is the open-source Engine; ads are in the closed-source Monitor app only)
- Don't remove or weaken any of the 7 keep-alive layers
- Don't change the thermal auto-stop threshold (50°C is non-negotiable)
- Don't change the SSH port from 2022

## Architecture Rules

### Boundaries

PocketEngine is the **server engine** in a 2-app architecture:

| Rule | Reason |
|------|--------|
| No ad SDKs in Engine | Ads belong in PocketMonitor (Play Store) |
| No Play Store policy-sensitive code removal | Engine is sideloaded; these features are intentional |
| Single distro (Ubuntu 24.04 LTS) | Simplicity is a feature |
| Dropbear SSH on port 2022 | Lightweight, auto-configured |

### IPC Contract

The LocalSocket IPC protocol (`pocketserver_ipc`) is a contract with PocketMonitor. Changes to the IPC protocol must be backward-compatible. If you're modifying `ipc/IpcServer.kt`, please document protocol changes clearly in your PR.

### Keep-Alive System

The 7-layer keep-alive system in `ServerForegroundService.kt` is critical. All 7 layers are mandatory:

1. Foreground Service
2. Partial Wake Lock
3. WiFi Lock
4. Battery Optimization Exemption
5. START_STICKY + onTaskRemoved
6. BOOT_COMPLETED Receiver
7. Manufacturer-specific optimization

Removing or weakening any layer causes server instability on certain devices.

## Pull Request Guidelines

### Before Submitting

- [ ] Tested on a real device (not just emulator)
- [ ] No new warnings or lint errors
- [ ] Existing functionality is not broken
- [ ] Commit messages are clear and concise

### PR Description

Please include:
- **What** changed and **why**
- **Device(s)** tested on (model + Android version)
- **Screenshots** for UI changes

### Review Process

- All PRs require at least 1 review before merging
- We aim to review PRs within a few days
- If your PR has no activity for a week, feel free to ping

## Priority Contribution Areas

These are areas where community help is most impactful:

### Device Compatibility
- Testing and fixing keep-alive on specific manufacturers
- Adding manufacturer deep links for new OEMs
- Reporting devices where the server dies in the background

### Performance
- Reducing PRoot overhead
- Optimizing resource monitoring
- Improving install speed

### Localization
- Translating UI strings (currently Korean + English)
- We use Android `strings.xml` resources

### Documentation
- Improving setup guides
- Writing troubleshooting docs
- Documenting device-specific quirks

## Language

This project is maintained by a Korean-speaking team. We communicate in:
- **Issues and PRs**: English preferred (we use AI-assisted translation)
- **Code and comments**: English
- **UI strings**: English + Korean (via `strings.xml`)

Korean is also welcome in issues — we'll respond in both languages when possible.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
