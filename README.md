<div align="center">
             <img src="./logo.png" />
             <h1>ClearSight (明澈之眼) For Android</h1>
             帮助您检查Android设备是否处于可信环境
             <br>
             <br>
             <img src="https://img.shields.io/github/release/VisualTechStudio/ClearSight" />
             <img src="https://img.shields.io/github/downloads/VisualTechStudio/ClearSight/total?color=white&style=plastic" />
             <img src="https://img.shields.io/github/stars/VisualTechStudio/ClearSight" />
             <br>
             <br>
             <a href="README_en_US.md">🇬🇧 English Readme</a>
</div>

---

# ClearSight 技术文档

## 一、项目概述

ClearSight（明澈之眼）是一款 Android 设备安全检测应用，旨在帮助用户判断设备是否处于可信环境。该应用通过多层检测机制，识别潜在的系统篡改、Root 权限滥用和危险应用安装等安全威胁。

### 1.1 核心价值

| 特性 | 描述 |
|------|------|
| **多维度检测** | 支持文件级、应用级、系统级（Key Attestation）三层检测 | @linmana
| **底层信息面板** | 实时显示设备硬件、内核、系统指纹及安全补丁状态 |
| **自适应引擎** | 根据设备 Root 状态自动选择最佳检测策略 (SU/PM Shell vs API) |
| **实时反馈** | 即时显示检测结果，支持动态水印防伪 |
| **现代化 UI** | 基于 Jetpack Compose 的单 Activity 架构，支持深色模式与预测性返回 |

### 1.2 已测试设备

| 机型 | 系统版本 | 是否移植包 | Root方案 | 工作状态 |
|------|---------|-----------|---------|---------|
| OnePlus 15 (Infiniti CN) | ColorOS 16.0.8.301 (Android 16) | 否 | KowSU LKM | 工作正常且全部通过 |
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.5.501 (Android 16) | 否 | ReSukiSU GKI | 工作正常且全部通过 |
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.7.207 (Android 16) | CoolApk@空白没有输 | KernelSU LKM | 工作正常且全部通过 |
| Redmi K40 (Alioth CN) | HyperOS 3.0 (Android 16) | 未知 | FolkPatch Full | 工作正常且全部通过 |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | 否 | Magisk Alpha | 工作正常但部分通过 |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | CoolApk@洛雪_QwQ | KernelSU 三方构建 | 工作正常且全部通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | MIUI 14.0.5.0 (Android 13) | 否 | Magisk Alpha | 工作正常但部分通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.5.0 (Android 15) | 否 | KernelSU LKM | 工作正常且全部通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.303.50 (Android 16) | CoolApk@做梦书 | FolkPatch Full | 工作正常且全部通过 |

---

## 二、项目架构

### 2.1 目录结构

```
ClearSight-main/
├── app/                      # 应用模块
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/       # 配置文件目录
│   │   │   │   ├── check.conf      # 文件检测规则配置
│   │   │   │   └── appcheck.conf   # 应用检测规则配置
│   │   │   ├── java/com/vtstudio/clearsight/
│   │   │   │   ├── MainActivity.kt     # 主界面（UI与核心逻辑）
│   │   │   │   ├── SettingsActivity.kt # 设置页面
│   │   │   │   └── ClearSightLogic.kt  # 核心检测引擎与工具类
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle.kts      # 模块构建配置
├── build.gradle.kts          # 项目构建配置
├── logo.png                  # 项目Logo
└── README.md                 # 技术文档
```

### 2.2 核心文件职责

| 文件路径 | 职责 | 状态 |
|---------|------|------|
| `ClearSightLogic.kt` | 封装所有检测算法、Shell执行、硬件信息获取、吊销列表管理 | **引擎** |
| `MainActivity.kt` | 负责状态管理、UI 渲染、动态水印以及检测任务调度 | **核心** |
| `SettingsActivity.kt` | 吊销列表手动更新、关于页面及其他配置项 | **UI** |
| `check.conf` / `appcheck.conf` | 定义待检测的文件路径与应用包名 | **配置** |

---

## 三、核心功能模块

### 3.1 检测与排序逻辑

应用根据检测结果动态调整分类的显示顺序：
1. **风险优先**：发现危险项（Critical）的分类排在最前，其次是可疑项（Suspicious）。
2. **默认顺序**：在风险等级相同时，按照 `Security` > `Apps` > `Files` 排序。
3. **交互优化**：若分类下所有条目均正常，则自动隐藏右侧的折叠/展开按钮。

### 3.2 吊销列表管理 (Revocation List)

为了修复启动时短暂显示为 `NOT_FETCHED` 的问题，采用了两步初始化策略：
- **同步预加载 (`initRevocationList`)**：在 `loadAllCategories` 开始前，优先从本地缓存 `revocation_list.json` 同步加载数据。
- **异步更新 (`fetchRevocationList`)**：应用启动后在 IO 协程中静默更新吊销数据并同步到本地缓存。

### 3.3 设备信息获取

新增设备信息面板，通过 `getDeviceInfoSummary()` 获取以下数据：
- **Device**: 品牌 型号 (产品名 设备名)
- **Hardware**: 硬件平台 主板 (主要 ABI)
- **Kernel**: 读取 `/proc/version` 以展示完整版本与编译日期
- **OS**: Android 版本 构建 ID (API Level)
- **Fingerprint**: 系统构建指纹
- **Security Patch**: 标注 `OS: [日期] | Vendor: [日期]`

---

## 四、核心 API 说明

### 4.1 硬件信息 API

#### `getDeviceInfoSummary()` - 获取汇总设备信息

**实现机制**：
结合 `android.os.Build` 字段与 `getprop` 命令。
```kotlin
// 示例：获取供应商安全补丁日期
getSystemProperty("ro.vendor.build.security_patch")
```

### 4.2 吊销列表 API

#### `initRevocationList(context: Context)` - 同步初始化

**功能**：在首次渲染前强制从缓存加载吊销数据。

#### `fetchRevocationList(context: Context)` - 网络更新

**功能**：从 `android.googleapis.com` 获取最新的 Key 吊销列表。

---

## 五、数据模型

### 5.1 DeviceInfoSummary - 设备信息汇总

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

## 六、UI 组件架构

### 6.1 动态水印

水印采用 `drawWithContent` 在内容之上绘制，循环覆盖全屏：
```kotlin
for (x in -200..size.width.toInt() + 200 step 500) {
    for (y in 0..size.height.toInt() + 500 step 400) {
        // 绘制倾斜的文字水印
    }
}
```

### 6.2 状态提示卡片

卡片左右分列对齐：
- **左侧**：检测结果状态 + 简短版本号 (V 1.2)
- **右侧**：BUILD 标签 + 构建类型 (RELEASE/DEBUG)

---

## 七、构建与部署

### 7.1 构建要求

| 依赖 | 版本 |
|------|------|
| Gradle | 8.5+ |
| compileSdk | 37 |
| versionCode | 2106080030 (Int.MAX 内) |
| versionName | 1.2 |

### 7.2 构建命令

```bash
# 构建 Release 版本 APK
./gradlew assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/`。

---

## 八、贡献者

| 名字 | 方法 |
|------|------|
| [@linmana](https://github.com/linmana) | 测试设备 OnePlus 15 |
| [@Shayne_Hui](https://github.com/ShayneHui) | 代码、测试设备:Redmi K40 |
| [@KL_Xydwg01](https://github.com/VisualTechStudio/) | 代码、测试设备:OnePlus Ace 3 Pro、Xiaomi Pad 6 Pro、Xiaomi Mix 2s |


---

**VisualTechStudio** | *让安全透明可见*
