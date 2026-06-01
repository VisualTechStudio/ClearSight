<div align="center">
             <img src="./logo.webp" />
             <h1>ClearSight(明澈之眼)For Android</h1>
             帮助您检查Android设备是否处于可信环境
             <br>
             <br>
             <img src="https://img.shields.io/github/release/VisualTechStudio/ClearSight" />
             <img src="https://img.shields.io/github/downloads/VisualTechStudio/ClearSight/total?color=white&style=plastic" />
             <img src="https://img.shields.io/github/stars/VisualTechStudio/ClearSight" />
             <br>
             <br>
             <a href="README_en_US.md">🇬🇧English Readme</a>
</div>

# 关于ClearSight

### 已经测试的设备
机型 | 系统 | 是否移植包 | Root方案 | 工作状态
--------- | ------ | -------- | ------ | ------
OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.5.501 (Android 16) | 否 | ReSukiSU GKI | 工作正常且全部通过
OnePlus Ace 3 Pro (Corvette CN) | ColorOS 16.0.7.207 (Android 16) | CoolApk@空白没有输 | KernelSU LKM | 工作正常且全部通过
Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | 否 | Magisk Alpha | 工作正常但部分通过
Xiaomi Mix 2s (Polaris CN) | HyperOS 3.0.5.0 (Android 16) | CoolApk@洛雪_QwQ | KernelSU 三方构建 | 工作正常且全部通过
Xiaomi Pad 6 Pro (Liuqin CN) | MIUI 14.0.5.0 (Android 13) | 否 | Magisk Alpha | 工作正常但部分通过
Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.5.0 (Android 15) | 否 | KernelSU LKM | 工作正常且全部通过
Xiaomi Pad 6 Pro (Liuqin CN) | HyperOS 3.0.303.50 (Android 16) | CoolApk@做梦书 | FolkPatch | 工作正常且全部通过



### 主要功能
检测类别 | 检测项 | 实现方式
--------- | ------ | --------
文件 | Scene、TWRP、OrangeFox、MT管理器等App或其他工具产生的文件等 | Android Sandbox / 所有文件访问权(MANAGE_EXTERNAL_STORAGE) / SU
Apps | Magisk(及其分支)、KernelSU(及其分支)、Apatch(及其分支)、Scene、MT管理器、LSPosed模块等 | Android PackageManager API / SU
系统规则 | ADB、无障碍、SELinux、系统指纹、机型配置等 | /

# 进一步开发ClearSight(明澈之眼)

### 自定义检测配置
对现有APK解包后或下载本仓库在Android Studio修改./assets/*.conf，修改完成后自行构建并签名使用
配置文件 | 描述 | 使用方法
--------- | ------ | --------
check | 需要检测的文件或目录 | "!"前缀为危险条目，"?"前缀为可疑条目，例如"!/dev/cpuset/scene-daemon"
appcheck | 需要检测的App |  "!"前缀为危险条目，"?"前缀为可疑条目，例如"!me.weishu.kernelsu"
