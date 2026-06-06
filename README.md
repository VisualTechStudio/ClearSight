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
| **多维度检测** | 支持文件级、应用级、系统级三层检测 |
| **自适应引擎** | 根据设备权限状态自动选择最优检测策略 |
| **实时反馈** | 即时显示检测结果，支持动态刷新 |
| **可配置性** | 通过配置文件自定义检测规则 |
| **轻量级** | 单Activity架构，资源占用低 |

### 1.2 已测试设备

| 机型 | 系统版本 | 是否移植包 | Root方案 | 工作状态 |
|------|---------|-----------|---------|---------|
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.5.501 (Android 16) | 否 | ReSukiSU GKI | 工作正常且全部通过 |
| OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.7.207 (Android 16) | CoolApk@空白没有输 | KernelSU LKM | 工作正常且全部通过 |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | 否 | Magisk Alpha | 工作正常但部分通过 |
| Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | CoolApk@洛雪_QwQ | KernelSU 三方构建 | 工作正常且全部通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | MIUI 14.0.5.0 (Android 13) | 否 | Magisk Alpha | 工作正常但部分通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.5.0 (Android 15) | 否 | KernelSU LKM | 工作正常且全部通过 |
| Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.303.50 (Android 16) | CoolApk@做梦书 | FolkPatch | 工作正常且全部通过 |

---

## 二、项目架构

### 2.1 目录结构

```
ClearSight-main/
├── .idea/                    # Android Studio 配置目录
├── app/                      # 应用模块
│   ├── release/              # 构建产物目录
│   │   ├── baselineProfiles/ # 基准配置文件
│   │   ├── app-release.apk   # 发布APK
│   │   └── output-metadata.json
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/       # 配置文件目录
│   │   │   │   ├── check.conf      # 文件检测规则配置
│   │   │   │   └── appcheck.conf   # 应用检测规则配置
│   │   │   ├── java/com/vtstudio/clearsight/
│   │   │   │   ├── ui/theme/       # Jetpack Compose 主题配置
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   └── MainActivity.kt # 主Activity（核心逻辑）
│   │   │   ├── res/          # 资源文件
│   │   │   │   ├── drawable/     # 可绘制资源
│   │   │   │   ├── mipmap-*/     # 应用图标
│   │   │   │   ├── values/       # 配置值
│   │   │   │   └── xml/          # XML配置
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/      # 仪器化测试
│   │   └── test/             # 单元测试
│   ├── .gitignore
│   ├── build.gradle.kts      # 模块构建配置
│   └── proguard-rules.pro    # ProGuard规则
├── gradle/                   # Gradle 配置
│   └── wrapper/
├── LICENSE
├── README.md
├── build.gradle.kts          # 项目构建配置
├── gradle.properties
├── gradlew
├── gradlew.bat
├── logo.png
└── settings.gradle.kts       # Gradle 设置
```

### 2.2 核心文件职责

| 文件路径 | 职责 | 状态 |
|---------|------|------|
| `app/src/main/java/.../MainActivity.kt` | 核心检测逻辑、UI展示、权限管理 | **核心** |
| `app/src/main/assets/check.conf` | 文件/目录检测规则定义 | **配置** |
| `app/src/main/assets/appcheck.conf` | 应用包名检测规则定义 | **配置** |
| `app/src/main/AndroidManifest.xml` | 应用权限声明、组件注册 | **配置** |
| `app/src/main/java/.../ui/theme/*.kt` | Jetpack Compose 主题系统 | **UI** |

---

## 三、核心功能模块

### 3.1 检测引擎架构

```
┌─────────────────────────────────────────────────────────────┐
│                      ClearSight Engine                      │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  权限检测层   │───▶│  策略选择层   │───▶│  执行检测层   │  │
│  │ Root/Storage │    │              │    │              │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                   │                   │          │
│         ▼                   ▼                   ▼          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │ checkRoot()  │    │ hasRoot?     │    │ SU命令执行   │  │
│  │ checkStorage │    │ hasStorage?  │    │ PM API调用   │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 检测类别说明

| 检测类别 | 检测项 | 实现方式 | 风险等级标识 |
|---------|-------|---------|------------|
| **文件检测** | su二进制、Magisk/KernelSU/Apatch目录、Scene设备节点、TWRP/Fox目录 | Android Sandbox / MANAGE_EXTERNAL_STORAGE / SU | `!` 危险 / `?` 可疑 |
| **应用检测** | Magisk、KernelSU、Apatch、Scene、MT管理器、LSPosed模块等 | PackageManager API / SU Shell | `!` 危险 / `?` 可疑 |
| **跨检测** | 通过文件痕迹推断应用存在（如MT2目录 → MT管理器） | 文件+应用联合分析 | `!` 危险 |

### 3.3 检测规则配置

配置文件采用简洁的文本格式，每行定义一个检测项：

```
# check.conf - 文件检测规则
! /system/bin/su           # 危险：系统级su二进制
! /data/adb/magisk         # 危险：Magisk安装路径
? /sdcard/TWRP             # 可疑：TWRP恢复分区备份

# appcheck.conf - 应用检测规则  
! me.weishu.kernelsu       # 危险：KernelSU主应用
? bin.mt.plus              # 可疑：MT管理器
```

**规则语法**：
- `!` 前缀：**危险项** - 直接表明系统已被篡改或存在高风险
- `?` 前缀：**可疑项** - 可能存在风险，需进一步确认
- 无前缀：普通检测项（当前版本未使用）

---

## 四、核心 API 说明

### 4.1 权限检测 API

#### `checkRootPermission()` - Root 权限检测

**功能**：检测设备是否具有 Root 权限

**实现机制**：
```kotlin
fun checkRootPermission(): Boolean {
    // 执行 `su` 命令并验证 uid=0
    process = Runtime.getRuntime().exec("su")
    os.writeBytes("id\n")
    // 返回 true 如果输出包含 "uid=0"
}
```

**返回值**：`Boolean` - `true` 表示已获取 Root 权限

**调用场景**：应用启动时初始化检测策略

---

### 4.2 文件检测 API

#### `checkPathsWithRoot(paths: List<String>)` - Root 模式文件检测

**功能**：通过 SU 命令检测文件/目录是否存在

**参数**：
| 参数 | 类型 | 说明 |
|------|------|------|
| `paths` | `List<String>` | 待检测的文件路径列表 |

**返回值**：`Map<String, Pair<Boolean, String>>` - 路径 → (是否存在, 检测方法)

**实现机制**：
```kotlin
// 批量执行 shell 命令
for (path in paths) {
    os.writeBytes("if [ -e \"$path\" ]; then echo \"1\"; else echo \"0\"; fi\n")
}
```

---

#### `checkPathsWithNormalApi(context: Context, paths: List<String>)` - 普通模式文件检测

**功能**：通过 Android 文件 API 检测路径

**权限依赖**：
- `READ_EXTERNAL_STORAGE`（Android < 11）
- `MANAGE_EXTERNAL_STORAGE`（Android ≥ 11）

**检测方法标识**：
- `Android File Permissions` - 已获取存储权限
- `Android Sandbox / Scoped Storage` - 仅沙箱访问

---

### 4.3 应用检测 API

#### `checkAppsWithPm(context: Context, packageNames: List<String>)` - PackageManager 检测

**功能**：通过系统 PackageManager API 检测应用安装状态

**实现机制**：
```kotlin
try {
    pm.getPackageInfo(pkg, 0)  // 若抛出异常则表示未安装
    resultMap[pkg] = Pair(true, "PackageManager API")
} catch (_: PackageManager.NameNotFoundException) {
    resultMap[pkg] = Pair(false, "PackageManager API")
}
```

**权限依赖**：`QUERY_ALL_PACKAGES`（Android ≥ 11）

---

#### `checkAppsWithRoot(packageNames: List<String>)` - Root 模式应用检测

**功能**：通过 SU + PM Shell 命令检测应用

**实现机制**：
```kotlin
os.writeBytes("pm list packages | grep -q \"package:$pkg\" && echo \"1\" || echo \"0\"\n")
```

**优势**：可检测通过 HMA 等工具隐藏的应用

---

### 4.4 应用身份解析 API

#### `resolveAppIdentity(context: Context, pkgName: String, hasRoot: Boolean)` - 解析应用信息

**功能**：获取应用的显示名称和图标

**返回值**：`Pair<String?, Drawable?>` - (应用名称, 应用图标)

**解析策略**：
1. **优先**：通过 `PackageManager.getApplicationInfo()` 获取
2. **降级**：Root 环境下通过 `pm path` 获取 APK 路径，再解析
3. **兜底**：内置包名映射表（fallbackName）

**内置映射表**：
| 包名 | 显示名称 |
|------|---------|
| `bin.mt.plus` | MT管理器 |
| `com.omarea.vtools` | Scene |
| `com.topjohnwu.magisk` | Magisk |
| `io.github.truboxl.helis` | HMA |
| `com.catchingnow.icebox` | 冰箱 |
| `com.vmos.glow` | VMOS |

---

### 4.5 配置文件读取 API

#### `readConfFile(context: Context, fileName: String)` - 读取检测配置

**功能**：从 assets 目录读取配置文件

**参数**：
| 参数 | 类型 | 说明 |
|------|------|------|
| `context` | `Context` | Android 上下文 |
| `fileName` | `String` | 配置文件名（`check.conf` 或 `appcheck.conf`） |

**返回值**：`List<String>` - 非空行的列表

---

### 4.6 检测结果汇总 API

#### `loadAllCategories(context: Context)` - 加载所有检测类别

**功能**：整合文件检测和应用检测结果，执行跨检测关联

**跨检测映射表**：
| 包名 | 触发文件路径 |
|------|-------------|
| `bin.mt.plus` | `/sdcard/MT2` |
| `com.omarea.vtools` | `/dev/scene`, `/dev/cpuset/scene-daemon` |

**返回值**：`List<CheckCategory>` - 包含 Files 和 Apps 两个类别

---

## 五、数据模型

### 5.1 CheckSubItem - 检测子项

```kotlin
data class CheckSubItem(
    val rawPath: String,       // 原始配置行（包含前缀）
    val cleanPath: String,     // 清理后的目标路径/包名
    val isFound: Boolean,      // 是否已检测到
    val isCritical: Boolean,   // 是否为危险项
    val checkMethod: String,   // 检测方法描述
    val appName: String?,      // 应用名称（仅应用检测）
    val appIcon: Drawable?     // 应用图标（仅应用检测）
)
```

### 5.2 CheckCategory - 检测类别

```kotlin
data class CheckCategory(
    val name: String,           // 类别名称（Files / Apps）
    val subItems: List<CheckSubItem>,
    val hasIssue: Boolean       // 是否存在异常项
)
```

---

## 六、UI 组件架构

### 6.1 状态管理

| 状态 | 类型 | 说明 |
|------|------|------|
| `refreshTrigger` | `Int` | 刷新触发器，变更时重新执行检测 |
| `hasRootPermission` | `Boolean` | Root 权限状态 |
| `hasStoragePermission` | `Boolean` | 存储权限状态 |
| `isPollingActive` | `Boolean` | 权限轮询激活状态 |
| `expandedStates` | `MutableList<Boolean>` | 分类卡片展开状态 |
| `showInfoDialogForCategory` | `CheckCategory?` | 信息弹窗显示状态 |

### 6.2 检测结果状态机

```
                    ┌─────────────────┐
                    │   开始检测       │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌─────────┐    ┌─────────┐    ┌─────────┐
        │  危险项  │    │  可疑项  │    │  正常   │
        │ isFound │    │ isFound │    │  环境   │
        │ &&Critical│  │ &&!Critical│  │         │
        └────┬────┘    └────┬────┘    └────┬────┘
             │              │              │
             ▼              ▼              ▼
        红色警告        橙色警告        绿色正常
        (hasCritical)  (hasSuspicious)  (正常环境)
```

### 6.3 HMA 检测机制

应用通过检测应用列表隐藏工具（HMA/OSS）的痕迹来识别规避行为：

```kotlin
val isHmaSuspicion = categories.find { it.name == "Apps" }?.subItems?.any { 
    it.isFound && it.checkMethod.startsWith("File Trace:") 
} == true
```

当检测到通过文件痕迹推断出的应用时，提示用户可能正在使用隐藏工具。

---

## 七、权限管理

### 7.1 权限声明

| 权限 | 用途 | 保护级别 |
|------|------|---------|
| `MANAGE_EXTERNAL_STORAGE` | 访问外部存储以检测文件 | 特殊权限 |
| `QUERY_ALL_PACKAGES` | 查询所有已安装应用 | 签名权限 |

### 7.2 权限请求流程

```
应用启动
    │
    ▼
检查存储权限 ──未授权──▶ 显示权限提示卡片
    │                           │
    │                           ▼
    │                    点击卡片跳转设置
    │                           │
    │                           ▼
    │                    5秒轮询权限状态
    │                           │
    │                           ▼
    │                    权限获取成功
    │                           │
    ▼                           │
检查Root权限                    │
    │                           │
    ▼                           │
执行检测 ◀──────────────────────┘
```

---

## 八、构建与部署

### 8.1 构建要求

| 依赖 | 版本 |
|------|------|
| Gradle | 8.5+ |
| Kotlin | 1.9+ |
| Compose Compiler | 1.5+ |
| minSdkVersion | 24 (Android 7.0) |
| targetSdkVersion | 34 (Android 14) |

### 8.2 构建命令

```bash
# 构建release版本
./gradlew assembleRelease

# 构建debug版本
./gradlew assembleDebug

# 运行测试
./gradlew test
```

### 8.3 自定义检测规则

1. 解包 APK 或克隆仓库
2. 修改 `app/src/main/assets/check.conf` 和 `appcheck.conf`
3. 重新构建并签名

---

## 九、扩展开发

### 9.1 添加新的检测项

**步骤 1**：在配置文件中添加规则
```
# check.conf - 添加新的危险文件
! /data/adb/new_root_tool

# appcheck.conf - 添加新的危险应用
! com.example.malicious.app
```

**步骤 2**：如需跨检测关联，修改 `crossCheckMap`
```kotlin
val crossCheckMap = mapOf(
    "bin.mt.plus" to listOf("/sdcard/MT2"),
    "com.omarea.vtools" to listOf("/dev/scene", "/dev/cpuset/scene-daemon"),
    "com.example.app" to listOf("/path/to/trigger/file")  // 新增映射
)
```

**步骤 3**：如需图标和名称映射，修改 `fallbackName`
```kotlin
val fallbackName = when (pkgName) {
    // ... 现有映射
    "com.example.app" -> "示例应用"
    else -> null
}
```

### 9.2 检测方法扩展

当前支持的检测方法：

| 检测方法 | 触发条件 | 说明 |
|---------|---------|------|
| `SU / Root Engine` | Root + 文件检测 | 通过su执行文件存在性检查 |
| `SU / PM Shell` | Root + 应用检测 | 通过su执行pm命令 |
| `PackageManager API` | 非Root + 应用检测 | 直接调用系统API |
| `Android File Permissions` | 有存储权限 | 直接文件访问 |
| `Android Sandbox / Scoped Storage` | 无存储权限 | 沙箱限制访问 |
| `File Trace: <path>` | 跨检测触发 | 通过文件痕迹推断应用 |

---

## 十、许可证

```
MIT License

Copyright (c) 2024 VisualTechStudio

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 十一、贡献指南

欢迎提交 Issue 和 Pull Request！

### 贡献规范

1. **代码风格**：遵循 Kotlin 官方编码规范
2. **提交信息**：使用 Conventional Commits 格式
3. **测试要求**：新增功能需附带单元测试
4. **文档更新**：API 变更需同步更新本文档

---

**VisualTechStudio** | *让安全透明可见*
