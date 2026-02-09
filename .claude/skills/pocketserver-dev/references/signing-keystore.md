# Signing Keystore Guide

## Overview
Both PocketMonitor and PocketServer Engine must be signed with the **same keystore**.
This enables LocalSocket IPC package signature verification between the two apps.

## File Locations
| File | Path | Git Tracked |
|------|------|:-----------:|
| Keystore | `keystore/pocketserver-release.jks` | No (gitignored) |
| Credentials | `keystore/keystore.properties` | No (gitignored) |

## Usage in build.gradle
Both `pocket-server/app/build.gradle` and `pocket-monitor/app/build.gradle` must reference the same keystore via `keystore.properties`.

The signing config loads from `../keystore/keystore.properties` (relative to each app's root).

## Backup Requirements
- **CRITICAL**: If the keystore is lost, Play Store app updates become impossible
- Back up `pocketserver-release.jks` + `keystore.properties` to at least 2 locations:
  - Cloud storage (Google Drive, OneDrive)
  - Physical media (USB drive)
- The keystore password is stored in `keystore.properties` -- back up this file too

## Key Details
- Algorithm: RSA 2048-bit
- Validity: ~27 years (10,000 days)
- Alias: `pocketserver`
- DN: CN=PocketServer, OU=Palank, O=Palank, L=Seoul, ST=Seoul, C=KR

## When Creating pocket-monitor
Copy the same signing config pattern from pocket-server's build.gradle.
The `storeFile` path (`../keystore/pocketserver-release.jks`) works for both apps since they're sibling directories.
