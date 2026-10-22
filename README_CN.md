# Anvil

[English](README.md) · **简体中文**

跑在手机上的 Android AI Agent —— 而且能真的操作手机。

[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](#requirements)
[![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-7F52FF.svg)](https://kotlinlang.org)
[![checks](https://github.com/Hoerire/zafiro/actions/workflows/checks.yml/badge.svg)](https://github.com/Hoerire/zafiro/actions/workflows/checks.yml)


Anvil 是一个以对话驱动的 Agent 应用：它把一整套用户态工具链打包进 APK，能在设备上执行真实命令，
甚至能在设备内部给自己构建 APK。

---

## 它凭什么不一样

**工具链跟着应用走。** 113 MB 工具链打进 APK，首启解包到私有存储：CPython 3.12.13、ripgrep、jq、
7z、curl、sqlite3、`file`，外加 305 个 busybox applet。不用 Termux，不用另外装，不用手工配置。
授予 root 后，Agent 拿到的是一个带完整工具的 shell。

**它能重新构建自己。** Anvil 可以在设备上拉起 Alpine chroot、装 JDK 与 Android SDK，
然后自己 assemble 出 APK。构建环境本身就是一个 Agent 可以驱动的工具。

**它有手。** 读屏与操作是一等工具：走无障碍服务拿结构化 UI 树，或走 shell input 做坐标点击与按键。

---

## 核心能力

### Agent 运行时
带工具调用的循环、流式输出、重试、取消，会话持久化到 Room。提示词按「稳定段 / 易变段」分层组装，
环境信息变化不会让缓存整体失效。

### 内置工具

| 工具 | 作用 |
|---|---|
| `shell` | 通过随包工具链执行命令（可用 root） |
| `open_uri` | 打开 URL / geo / tel / mailto |
| `launch_app` | 按名称或包名启动已装应用 |
| `find_installed_apps` | 解析已安装应用 |
| `notify` | 发系统通知 |
| `load_skill` | 按需把技能文档载入上下文 |
| `memory` | 跨会话持久化事实 |
| `screenshot` | 截取当前屏幕 |
| `view_image` | 读取磁盘上的图片 |
| `zafiro_env` | 检查工具链与构建环境，触发设备内构建 |
| `screen_operation_accessibility` | 通过无障碍读取并驱动 UI 树 |
| `screen_operation_shell` | 坐标点击 / 滑动 / 按键 |

### 技能
Markdown 技能随 APK 分发、按需加载。系统提示词保持精简，需要深层任务知识时由 Agent 主动取用。

### 界面
Jetpack Compose + Material 3 Expressive，Liquid Glass 质感与 Capsule 圆角。会话、历史、用量，
以及重排过的工具设置界面（每个工具独立开关）。

---

## 环境要求

- Android 8.0+（`minSdk 26`），推荐 Android 14–16
- Root（KernelSU 或 Magisk）以启用完整工具链；没有 root 应用仍可运行，但 App 进程自身无法执行私有目录里的二进制
- 屏幕操作工具需要无障碍服务权限
- 模型 API 需要 `INTERNET`

数据只写入应用私有存储，不需要存储权限。

---

## 构建

```sh
./gradlew :app:assembleDebug
```

需要 JDK 17、Android SDK platform 37 / build-tools 36。产物在 `app/build/outputs/apk/debug/`。

签名：放 `keystore/release.keystore` 或设环境变量 `ZAFIRO_KEYSTORE` 为绝对路径；
两者都没有时回落到标准 debug keystore。

---

## 模块结构

```
app/              应用、Compose UI、设置、工具管理
agent-runtime/    Agent 循环、提示词组装、内置工具、工具执行
business/         api · agent · files · notification · application · permission
libs/             logging · okia(运行时) · termux(libterm 后端)
ui-kit/           共享 Compose 组件
store/            KV 与域设置存储
xsettings/        运行时设置桥
remote-view/      远控视图基础件
common-utils/     公共工具
```

---

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.2.0 · Jetpack Compose · Material 3 Expressive · Liquid Glass · Capsule |
| 构建 | AGP 9.1.1（内置 Kotlin）· KSP · `compileSdk 37` · `minSdk 26` · `targetSdk 34` |
| Agent | OKIA 运行时 · kotlinx-serialization · OkHttp |
| 存储 | Room 2.7.2 |
| 终端 | libterm（core / runtime / libsu / shizuku / ssh 后端） |
| 图像 | coil-resvg |
| 设备内 Python | CPython 3.12.13（musl），随包分发，stdlib + 已打包 wheel |

---

---

## 来源说明

本项目由 [niki914/zafiro](https://github.com/niki914/zafiro) 衍生而来，原项目以 MIT 许可证发布。
原始版权声明保留在 `LICENSE` 中。应用的大部分内容此后已被重写，当前功能集见 `CHANGELOG.md`。

## 许可

见 `LICENSE`。
