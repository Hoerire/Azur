<div align="right">

中文 | **[English](README.md)**

</div>

# Azur

<p align="center">
  <a href="https://github.com/Hoerire/Azur/releases/latest"><img src="https://img.shields.io/github/v/release/Hoerire/Azur?include_prereleases" alt="release"/></a>
  <a href="https://github.com/Hoerire/Azur/releases/latest"><img src="https://img.shields.io/github/downloads/Hoerire/Azur/total" alt="downloads"/></a>
  <a href="https://github.com/Hoerire/Azur/stargazers"><img src="https://img.shields.io/github/stars/Hoerire/Azur?label=stars" alt="stars"/></a>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="license"/>
</p>

**一个 Android 应用，自带整套开发工具链，并能在自己运行的这台手机上编译代码。**

多数 Android 上的 AI 应用由一个对话界面和一个 API Key 构成。Azur 在此之外还具备本地执行能力：APK 内含
1,579 个 aarch64 二进制 —— CPython 3.12、305 个 applet 的 busybox、带 `clone` 与 `push` 能力的 git、
ripgrep、jq、7z、curl、sqlite3、`aapt2` —— 首次启动时解包至私有目录，注册为模型可直接调用的可执行文件。

当任务指向它自身的源码树时，定位、编辑、验证与构建均在同一设备上完成：读取文件、修改代码、执行测试、
调用 Gradle，最终产出新的 APK。整个过程不涉及主机，也不依赖外部构建服务 —— 产出该制品的工具链，正是
随应用运行的那一套。

| | |
|---|---|
| **包名** | `com.hoerire.tools` · `minSdk 26` · `compileSdk 37` |
| **APK** | 98 MB，内含 1,579 个二进制 |
| **运行时** | CPython 3.12.13（musl），标准库完整，不依赖 Chaquopy，没有 wheel ABI 包袱 |
| **Shell** | busybox（305 个 applet），外加 `rg` `fd` `fzf` `jq` `7zz` `curl` `sqlite3` `file` `rsync` `wcurl` `xz` `zstd` `lz4` `strace` |
| **Git** | 2.45.4，含 `git-core` 与 `remote-https`，`clone` 与 `push` 均可用 |
| **构建工具** | `aapt2`（bionic aarch64，3.5 MB）+ 它需要的 musl `libz` |
| **依赖库** | 71 个 `.so`，含 `ld-musl-aarch64.so.1` 与 `libexpat.so.1` |
| **门槛** | 一个任意 OpenAI 兼容端点的 Key |

> [!IMPORTANT]
> Android 8.0+。Agent 本体以普通权限运行；root 会把 `shell` 提升到 `su`，并解锁设备内构建环境。屏幕控制
> 需要开启无障碍服务。
>
> 从 [Releases](https://github.com/Hoerire/Azur/releases/latest) 装，或者 [自己编](#构建)。

## 闭环

这是别的 Android Agent 做不到的一段：工具链完整到能把「正在跟你说话的这个应用」重新编出来。

```text
你   →  "下载徽章折行了，修一下"
Azur →  shell: rg -n 'shields.io' README.md
     →  view_image: /data/local/aitools/out/preview.png
     →  shell: sed -i 's/…/…/' README.md
     →  zafiro_env: build   →  后台跑 gradle :app:assembleDebug
     →  zafiro_env: status  →  BUILD SUCCESSFUL, RC=0
     →  构建完成，已产出新 APK
```

底下是一条自举链路：第一次需要时去拉 Alpine rootfs、JDK 17、Android SDK 35 / 36 / 37.0。SDK 不进 APK，
只有事后取不到的东西才作为 assets 随包走。

## 为什么不需要 chroot

每个二进制都是 musl 静态链接，普通 Android 直接就能执行。但要让它真的有用，得先解决三件事：

- **网络**。应用沙箱挡掉原始 DNS 和 TLS。`netrun` 把两者都桥出去，`curl`、`git`、`pip` 都包在它上面。
- **装包**。`pip install` 在这里跑不了 —— musl 把解释器路径写死了，Android 上没有那个路径。`pyinstall.sh`
  从 PyPI 取 wheel，把纯 Python 文件解到共享的 `pylib`。只收纯 Python；带编译扩展的直接拒绝，而不是悄悄
  绑到错配的 ABI 上。
- **资源解包**。`aapt2` 会静默丢掉以 `_` 开头的资源目录，`zipfile/_path`、`pip/_internal` 这些因此残缺。
  它们以 `x__<原名>` 打包，解包时还原。

## Agent

一个回合是带类型的事件流，不是拿字符串去解析。工具调用、上下文压缩、子 Agent、上下文压力都是一等状态。

- 会话扛得住进程被杀；空闲会话释放模型槽位
- 重试次数由配置决定，不靠运气
- provider、base URL、温度、reasoning effort、超时，每个模型一套
- BYOK —— 任意 OpenAI 兼容端点，云端或本地

## 十二个工具

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

`zafiro_env` 是构建桥：`check`（21 种工具链）、`build`、`status`、`install`、`shell`、`apk`、`snapshot`。
屏幕控制优先走无障碍服务读原生 UI；view tree 读不出来时，用 `su` 按坐标操作。

## 六个技能

Markdown 文档，模型主动要才加载，不用的技能占用为零。

| 技能 | 用途 |
|---|---|
| `build` | 编译唯一入口：Gradle、AGP、SDK、NDK、`aapt2`、签名、APK 分析 |
| `hosttools` | 随包 CLI 清单，避免模型对可用命令作假设 |
| `zafiro-cmd` | 工具层命令的规范写法，避免模型自行拼接 `su` 与 `chroot` |
| `skill-creator` | 写、改、测新技能 |
| `tool-config` | 靠改沙箱里的 JSON 读写应用设置 |
| `termux` | Termux 首次 SSH 配置，以及断连恢复 |

## 界面

全 Compose。Material 3 Expressive，Liquid Glass 与 Capsule 质感，浅色 / 深色 / 跟随系统，六种语言支持
应用级切换，悬浮球随手唤起。终端是 `libterm`，本地、libsu、Shizuku、SSH 四种 backend。

## 架构

| 模块 | 内容 |
|---|---|
| `app/` | UI、设置、repo、工具胶水、`MainActivity` |
| `agent-runtime/` | 回合引擎、工具注册表、压缩、流式输出 |
| `business/api/` | 共享模型与工具函数 |
| `libs/libterm/` | 终端 core、runtime、backend |
| `assets/hosttools/` | 1,579 个文件的工具链，以及它的 `VERSION` 指纹 |
| `assets/toolchain/` | `aapt2` 与 musl `libz` |
| `assets/buildkit/` | 签名与关闭 lint 的 Gradle init 脚本 |
| `assets/skills/` | 六个技能文档 |

四条决定影响着这个仓库的改动方式：

- **指纹绑内容，不绑时间**。指纹算的是资源清单的 SHA-1，所以应用升级不会重解 113 MB；缺失的关键资产
  单独补。
- **一个包一个根域**。主构建在 `/data/local/aitools`，测试构建在 `aitools-btN`，两个安装永不共用状态。
- **签名可移植**。`-PRELEASE_STORE_FILE` → `keystore/debug.keystore` → `keystore/release.keystore` →
  AGP 默认。新克隆的仓库不填任何本地路径也能出包。
- **CI 自己查自己**。`.github/scripts/verify_repo.py` 校验文档齐全、没提交 keystore、没漏个人路径、没有
  tab；`verify_hosttools.py` 重算资源指纹与 `VERSION` 对账。

## 构建

```bash
git clone https://github.com/Hoerire/Azur
cd Azur
./gradlew :app:assembleDebug
```

| 层 | 选择 |
|---|---|
| 语言 / UI | Kotlin 2.2.0、Jetpack Compose、Material 3 Expressive |
| 构建 | AGP 9.1.1（内置 Kotlin）、KSP、`compileSdk 37` |
| Agent | OKIA 运行时、kotlinx-serialization、OkHttp |
| 存储 | Room |
| 终端 | `libterm`（libsu / Shizuku / SSH） |
| 工具链 | CPython 3.12.13（musl）、busybox、git 2.45.4、`aapt2` |

## 由来

Azur 从 [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1 长出来。留下的是它的外壳 —— Compose UI、
设置框架、OKIA 运行时、`libterm` 终端；换掉的是外壳之下的一切：工具执行层、Python 方案、环境自举、构建
流水线。上游署名保留在关于页。

## 许可

MIT。见 [LICENSE](LICENSE)。
