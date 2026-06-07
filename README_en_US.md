<div align="center">
             <img src="./logo.png" />
             <h1>ClearSight For Android</h1>
             Helping you verify if your Android device is in a trusted environment
             <br>
             <br>
             <img src="https://img.shields.io/github/release/VisualTechStudio/ClearSight" />
             <img src="https://img.shields.io/github/downloads/VisualTechStudio/ClearSight/total?color=white&style=plastic" />
             <img src="https://img.shields.io/github/stars/VisualTechStudio/ClearSight" />
             <br>
             <br>
             <a href="README.md">🇨🇳 Chinese Readme</a>
</div>

---

# ClearSight Technical Documentation

## I. Project Overview

ClearSight is an Android device security detection application designed to help users determine if their device is in a trusted environment. The app uses a multi-layered detection mechanism to identify potential system tampering, Root privilege abuse, and dangerous application installations.

### 1.1 Core Values

| Feature | Description |
|------|------|
| **Multi-dimensional Detection** | Supports three layers of detection: File-level, App-level, and System-level (Key Attestation) |
| **Low-level Info Panel** | Real-time display of hardware, kernel, system fingerprint, and security patch status |
| **Adaptive Engine** | Automatically selects the best detection strategy based on Root status (SU/PM Shell vs API) |
| **Real-time Feedback** | Instant display of results with dynamic anti-counterfeiting watermark |
| **Modern UI** | Single Activity architecture based on Jetpack Compose, supporting Dark Mode and Predictive Back |

### 1.2 Tested Devices

| Model | OS Version | Ported? | Root Solution | Status |
|------|---------|-----------|---------|---------|
| OnePlus 15 (Infiniti CN) | ColorOS 16.0.8.301 (Android 16) | No | KowSU LKM | Working perfectly |
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.5.501 (Android 16) | No | ReSukiSU GKI | Working perfectly |
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.7.207 (Android 16) | CoolApk@空白没有输 | KernelSU LKM | Working perfectly |
| Redmi K40 (Alioth CN) | HyperOS 3.0 (Android 16) | Unknown | FolkPatch Full | Working perfectly |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | No | Magisk Alpha | Working with partial pass |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | CoolApk@洛雪_QwQ | KernelSU Third-party | Working perfectly |
| Xiaomi Pad 6 Pro (Liuqin CN) | MIUI 14.0.5.0 (Android 13) | No | Magisk Alpha | Working with partial pass |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.5.0 (Android 15) | No | KernelSU LKM | Working perfectly |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.303.50 (Android 16) | CoolApk@做梦书 | FolkPatch Full | Working perfectly |

---

## II. Project Architecture

### 2.1 Directory Structure

```
ClearSight-main/
├── app/                      # Application Module
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/       # Configuration directory
│   │   │   │   ├── check.conf      # File detection rules
│   │   │   │   └── appcheck.conf   # App detection rules
│   │   │   ├── java/com/vtstudio/clearsight/
│   │   │   │   ├── MainActivity.kt     # Main UI & Core Logic
│   │   │   │   ├── SettingsActivity.kt # Settings Page
│   │   │   │   └── ClearSightLogic.kt  # Detection Engine & Utilities
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle.kts      # Module build config
├── build.gradle.kts          # Project build config
├── logo.png                  # Project Logo
└── README.md                 # Technical Documentation
```

### 2.2 Core Responsibilities

| File Path | Responsibility | Status |
|---------|------|------|
| `ClearSightLogic.kt` | Encapsulates all algorithms, Shell execution, hardware info retrieval, and revocation list management | **Engine** |
| `MainActivity.kt` | Handles state management, UI rendering, dynamic watermark, and detection scheduling | **Core** |
| `SettingsActivity.kt` | Manual revocation list updates, About page, and other configurations | **UI** |
| `check.conf` / `appcheck.conf` | Defines file paths and package names to be detected | **Config** |

---

## III. Core Functional Modules

### 3.1 Detection and Sorting Logic

The app dynamically adjusts the display order based on results:
1. **Risk Priority**: Categories with Critical issues are shown first, followed by Suspicious items.
2. **Default Order**: When risk levels are equal, the order follows `Security` > `Apps` > `Files`.
3. **UX Optimization**: If all items in a category are normal, the expand/collapse button on the right is automatically hidden.

### 3.2 Revocation List Management

To fix the brief `NOT_FETCHED` status during startup, a two-step initialization strategy is used:
- **Synchronous Preloading (`initRevocationList`)**: Before `loadAllCategories` starts, the revocation list is forcibly loaded from the local `revocation_list.json` cache.
- **Asynchronous Update (`fetchRevocationList`)**: After startup, the list is updated silently in an IO coroutine and synced to local cache.

### 3.3 Device Information Retrieval

The new device info panel retrieves data via `getDeviceInfoSummary()`:
- **Device**: Brand Model (Product DeviceName)
- **Hardware**: Platform Board (Primary ABI)
- **Kernel**: Reads `/proc/version` for full version and compilation date
- **OS**: Android Version Build ID (API Level)
- **Fingerprint**: System Build Fingerprint
- **Security Patch**: Labeled as `OS: [Date] | Vendor: [Date]`

---

## IV. Core API Specifications

### 4.1 Hardware Info API

#### `getDeviceInfoSummary()` - Get Summarized Device Info

**Mechanism**:
Combines `android.os.Build` fields with `getprop` commands.
```kotlin
// Example: Get vendor security patch date
getSystemProperty("ro.vendor.build.security_patch")
```

### 4.2 Revocation List API

#### `initRevocationList(context: Context)` - Synchronous Initialization

**Function**: Forcibly loads revocation data from cache before the first render.

#### `fetchRevocationList(context: Context)` - Network Update

**Function**: Retrieves the latest key revocation list from `android.googleapis.com`.

---

## V. Data Models

### 5.1 DeviceInfoSummary - Summary Model

```kotlin
data class DeviceInfoSummary(
    val device: String,
    val hardware: String,
    val kernel: String,
    val android: String,
    val os: String,
    val fingerprint: String,
    val security: String
)
```

---

## VI. UI Component Architecture

### 6.1 Dynamic Watermark

The watermark uses `drawWithContent` to draw over the content, covering the full screen:
```kotlin
for (x in -200..size.width.toInt() + 200 step 500) {
    for (y in 0..size.height.toInt() + 500 step 400) {
        // Draw tilted text watermark
    }
}
```

### 6.2 Status Banner

The banner layout is split and aligned:
- **Left**: Detection status + Short version (V 1.2)
- **Right**: BUILD label + Build type (RELEASE/DEBUG)

---

## VII. Build & Deployment

### 7.1 Build Requirements

| Dependency | Version |
|------|------|
| Gradle | 8.5+ |
| compileSdk | 37 |
| versionCode | 2106080030 (Within Int.MAX) |
| versionName | 1.2 |

### 7.2 Build Command

```bash
# Build Release APK
./gradlew assembleRelease
```

Generated APK is located at `app/build/outputs/apk/release/`.

---

## VIII. Contributors

| Name | Role |
|------|------|
| [@linmana](https://github.com/linmana) | Test device: OnePlus 15 |
| [@Shayne_Hui](https://github.com/ShayneHui) | Code, Test device: Redmi K40 |
| [@KL_Xydwg01](https://github.com/VisualTechStudio/) | Code, Test devices: OnePlus Ace 3 Pro, Xiaomi Pad 6 Pro, Xiaomi Mix 2s |

---

**VisualTechStudio** | *Making Security Transparent and Visible*
