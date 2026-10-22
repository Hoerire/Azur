# Anvil

[English](README.md) · **简体中文**

[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](#环境要求)
[![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)](#环境要求)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-7F52FF.svg)](https://kotlinlang.org)
[![checks](https://github.com/Hoerire/anvil/actions/workflows/checks.yml/badge.svg)](https://github.com/Hoerire/anvil/actions/workflows/checks.yml)

装在手机里的 AI 编程环境：自带完整用户态工具链与构建栈，Agent 能改代码、跑起来、把 APK 构建出来，
不需要电脑。

---

## 项目来源

Anvil 从 [niki914/zafiro](https://github.com/niki914/zafiro) 起步，原项目以 MIT 许可证发布。
沿用的是**应用骨架**：Compose UI 外壳、设置体系、OKIA agent 运行时、libterm 终端后端。

UI 之下全部重写。执行模型、工具系统、Python 方案、构建方案都与起点项目不同。

原始版权声明原样保留在 [LICENSE](LICENSE) 中。

## 做了哪些改造

**执行层，重写**

| 方面 | 原来 | 现在 |
|---|---|---|
| Python | 内嵌运行时在应用进程里解释执行 | 随包分发真实的 CPython 3.12.13（musl），从私有存储运行 |
| 工具协议 | 内置 MCP 客户端与服务端 | 完全移除，代码与依赖里都不再有 MCP |
| 助手接管 | 以 Xposed 模块 hook 外部语音助手 | 移除，树内不含任何 Xposed 模块 |
| 工具形态 | 运行期注册的 Python 自定义工具 | 十二个一等公民内置工具，类型化注册表 + 独立开关 |

**工具链：随包分发，而不是假设环境**

- 约 113 MB 工具链打进 APK：CPython、ripgrep、jq、7z、curl、sqlite3、`file`、fd、fzf、
  strace-log-merge，以及 305 个 busybox applet，首启解包到私有存储。
- 解包按内容寻址：把随包树的指纹与上次启动记录的值比对，只有内容真的变了才重新解包。
- 名字以下划线开头的目录在打包阶段会被静默丢弃，因此以 `x__` 前缀随包，解包时还原。

**构建环境：放在设备上**

- 应用能在设备上拉起 Alpine chroot，装 JDK、Android SDK 与 Gradle，然后在容器里驱动
  `gradlew` 出 APK —— 构建环境本身就是一个 Agent 可调用的工具。
- 构建资产跟着应用走：bionic `aapt2` 与其 musl `libz`、装配签名的 Gradle init 脚本，
  重建环境时不需要再联网拉取。

**工程细节**

- 可移植签名：依次尝试 `ZAFIRO_KEYSTORE` 或 `-PRELEASE_STORE_FILE`、仓库内 keystore、
  标准 debug keystore。
- 主包与并行测试构建使用各自独立的构建根，实验不会污染主环境。
- 63 个源文件统一 `zafiro_` 日志前缀，便于 logcat 过滤。

---

## 支持的功能

### Agent 运行时

带工具调用的循环、流式输出、分类重试、取消，会话持久化到 Room。提示词按「稳定段 / 易变段」
分层组装，环境信息变化不会让缓存整体失效。

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
| `screen_operation_accessibility` | 通过无障碍服务读取并驱动 UI 树 |
| `screen_operation_shell` | 坐标点击、滑动、按键 |

### 技能

Markdown 技能随 APK 分发、按需加载。系统提示词保持精简，需要深层任务知识时由 Agent 主动取用。

### 界面

Jetpack Compose + Material 3 Expressive，Liquid Glass 质感与 Capsule 圆角。会话、历史、用量，
以及带独立开关与整组开关的工具设置界面。

---

## 环境要求

- Android 8.0+（`minSdk 26`），推荐 Android 14–16
- Root（KernelSU 或 Magisk）以启用完整工具链 —— 没有 root 应用仍可运行，但 App 进程自身
  无法执行私有存储里的二进制
- 屏幕操作工具需要无障碍服务权限
- 模型 API 需要 `INTERNET`

数据只写入应用私有存储，不需要存储权限。

---

## 构建

```sh
./gradlew :app:assembleDebug
```

需要 JDK 17、Android SDK platform 37 / build-tools 36。产物在 `app/build/outputs/apk/debug/`。

签名依次取第一个可用的：`ZAFIRO_KEYSTORE`（绝对路径）、`-PRELEASE_STORE_FILE` Gradle 属性、
`keystore/debug.keystore`、`keystore/release.keystore`，都没有时回落到 Gradle 生成的默认
debug keystore。

CI 会跑仓库检查：文档齐全、没有提交 keystore 或构建产物、源码里没有个人路径、Kotlin 不用 tab、
随包工具链指纹与清单一致。改动工具链后用
`python3 .github/scripts/gen_hosttools_version.py` 重新生成指纹。

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

## 许可

MIT。见 [LICENSE](LICENSE)，其中同时保留了原始版权声明与当前版权声明。
