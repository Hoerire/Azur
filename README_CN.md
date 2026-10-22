<div align="right">

中文 | **[English](README.md)**

</div>

# Azur

<p align="center">
  <a href="https://github.com/Hoerire/azur/releases/latest"><img src="https://img.shields.io/github/v/release/Hoerire/azur?include_prereleases" alt="release"/></a>
  <a href="https://github.com/Hoerire/azur/releases/latest"><img src="https://img.shields.io/github/downloads/Hoerire/azur/total" alt="downloads"/></a>
  <a href="https://github.com/Hoerire/azur/stargazers"><img src="https://img.shields.io/github/stars/Hoerire/azur?label=stars" alt="stars"/></a>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="license"/>
</p>

Azur 是一个跑在手机上的 AI 编程 Agent，不需要任何别的设备。工具链就装在 APK 里 —— CPython 3.12、
305 个 applet 的 busybox、`git`、`rg`、`jq`、`7zz`、`curl`、`sqlite3`、`aapt2` —— 首启解包，直接交给模型
当可执行文件用。你让它修个 bug，它改源码、跑测试、重新出 APK，全程在它自己运行的那台手机上完成。

自带 Key，任何 OpenAI 兼容端点都能接，从云端旗舰到本地跑的小模型。

> [!IMPORTANT]
> Android 8.0+。Agent 本体不需要 root —— root 才能让 `shell` 以 `su` 身份跑、才能用设备内构建环境。
> 屏幕控制需要开无障碍服务。
>
> 去 [Releases](https://github.com/Hoerire/azur/releases/latest) 下包，或者 [自己编](#构建)。

## 核心能力

### 工具链跟着应用走

大多数「Android 上的 AI」只是调个 API 然后渲染文字。Azur 把另一半也带来了。

- **1,579 个二进制**，全是 aarch64 musl 编译，首启铺进私有目录
- **CPython 3.12，标准库是完整的** —— `ssl`、`sqlite3`、`zipfile`、`xml`、`zoneinfo`，外加 `requests`、`urllib3`、`html2text`
- **`git` 真能 clone 和 push** —— `git-core` 和 `remote-https` 都在，不是砍过的壳
- **`aapt2` 随包** —— 构建链里唯一没法运行时下载的一环
- **不要 chroot、不要 Termux、不要配套应用** —— 静态 musl 二进制，普通 Android 直接跑
- **网络是通的** —— `netrun` 把 DNS 和 TLS 送出应用沙箱，`curl`、`pip`、`git` 都能正常干活

### 在手机上，为手机而编

Agent 能自己把构建环境备齐，然后自己用。

- **`zafiro_env`** 给模型七个动作：`check`、`build`、`status`、`install`、`shell`、`apk`、`snapshot`
- **按需自举** —— 第一次用的时候拉 Alpine rootfs、JDK 17、Android SDK 35/36/37.0
- **后台跑真 Gradle** —— 起构建、轮询日志、拿到 APK
- **闭环** —— 它编出来的 APK，就是它自己这个应用

### 是 Agent 运行时，不是聊天壳

- **带类型的事件流** —— 工具调用、压缩、子 Agent、上下文压力都是一等状态，不靠解析字符串
- **杀进程也能活** —— 会话从断的地方接着走
- **按模型配置** —— provider、base URL、温度、reasoning effort、超时，每个模型一套，不是全局开关
- **空闲会话释放槽位**，请求失败按配置次数重试

### 十二个工具

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

每个都有独立开关。屏幕控制走无障碍服务；UI 树读不出来时，还能用 `su` 按坐标操作。

### 技能按需加载

六份文档，模型不主动要就不占上下文：`build` 管从编译到出包的整条流水线，`hosttools` 管随包 CLI 清单，
`zafiro-cmd` 管工具层的规范写法，另有 `skill-creator`、`tool-config`、`termux`。想加就加，就是普通 Markdown。

### 界面

Material 3 Expressive，Liquid Glass 与 Capsule 质感，浅色 / 深色 / 跟随系统，六种语言，悬浮球随手唤起，
终端基于 `libterm`，本地、libsu、Shizuku、SSH 四种 backend。

## 由来

Azur 从 [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1 长出来。留下的是它的外壳 —— Compose UI、
设置框架、OKIA 运行时、`libterm` 终端；换掉的是外壳之下的一切：工具执行层、Python 方案、环境自举、构建
流水线。上游署名保留在关于页。

## 构建

```bash
git clone https://github.com/Hoerire/azur
cd azur
./gradlew :app:assembleDebug
```

签名可选。构建依次找 `-PRELEASE_STORE_FILE`、`keystore/debug.keystore`、`keystore/release.keystore`，
都没有就用 AGP 默认 debug key。

CI 跑两个脚本：`verify_repo.py` 查文档齐全、没提交 keystore、没漏个人路径、没有 tab；`verify_hosttools.py`
重算资源指纹，跟 `assets/hosttools/VERSION` 对账。

## 工程结构

| 路径 | 内容 |
|---|---|
| `app/` | UI、设置、repo、工具胶水 |
| `agent-runtime/` | 回合引擎、工具注册表、压缩、流式输出 |
| `business/api/` | 共享模型与工具函数 |
| `libs/libterm/` | 终端 core、runtime、backend |
| `app/src/main/assets/hosttools/` | 随包工具链 |
| `app/src/main/assets/skills/` | 六个技能文档 |

## 技术栈

| 层 | 选择 |
|---|---|
| 语言 / UI | Kotlin 2.2.0、Jetpack Compose、Material 3 Expressive |
| 构建 | AGP 9.1.1、KSP、`compileSdk 37`、`minSdk 26` |
| Agent | OKIA 运行时、kotlinx-serialization、OkHttp |
| 存储 | Room |
| 终端 | `libterm`（libsu / Shizuku / SSH backend） |
| 工具链 | CPython 3.12.13（musl）、busybox、git 2.45.4、aapt2 |

## 参与

Issue 和 PR 都欢迎。构建循环、资源指纹规则、CI 检查项见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可

MIT。见 [LICENSE](LICENSE)。
