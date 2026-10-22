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

**一个 Android 应用，内嵌完整开发工具链，并可在其运行的设备上编译代码。**

Azur 衍生自 [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1，沿用其 Compose UI、设置框架、OKIA
Agent 运行时与 `libterm` 终端。上述层次之下的部分已被替换：可执行原生二进制的执行环境、在设备上配置
构建工具链的自举路径、以及构建输入随 APK 分发的构建流水线。上游署名保留于关于页。

应用以单一 APK 分发。首次启动时将 1,579 个 aarch64 二进制解包至私有目录，并注册为模型可调用的
可执行文件。

| | |
|---|---|
| **包名** | `com.hoerire.tools` · `minSdk 26` · `compileSdk 37` |
| **APK** | 98 MB，内含 1,579 个二进制 |
| **解释器** | CPython 3.12.13（musl），标准库完整，不含 Chaquopy，不含 wheel ABI 层 |
| **Python 包** | `requests` 2.34.2、`urllib3` 2.8.0、`idna` 3.20、`certifi` 2026.10.19、`charset_normalizer` 3.5.2、`html2text` 2025.4.15 |
| **Shell** | busybox（305 个 applet），另有 `rg` `fd` `fzf` `jq` `7zz` `curl` `sqlite3` `file` `rsync` `wcurl` `xz` `zstd` `lz4` `strace` |
| **Git** | 2.45.4，含 `git-core` 与 `remote-https`，支持 `clone` 与 `push` |
| **构建工具** | `aapt2`（bionic aarch64，3.5 MB）及其链接的 musl `libz` |
| **共享库** | 71 个，含 `ld-musl-aarch64.so.1` 与 `libexpat.so.1` |
| **模型接入** | 任意 OpenAI 兼容端点，凭据由用户提供 |

> [!IMPORTANT]
> 要求 Android 8.0 或更高版本。Agent 以普通权限运行；root 用于将 `shell` 提升为 `su` 身份，并启用设备内
> 构建环境。屏幕控制需启用无障碍服务。
>
> 从 [Releases](https://github.com/Hoerire/Azur/releases/latest) 安装，或[从源码构建](#构建)。

## 执行模型

随包二进制均静态链接 musl，以普通 Android 进程方式执行，不涉及容器、伴随进程或外部运行时。平台的三项
限制决定了其余设计。

**网络出口**。应用沙箱不允许直接访问 DNS 与 TLS。`netrun` 将两者转发至沙箱之外，`curl`、`git` 与包安装器
均包装为经由它访问网络。

**Python 包安装**。`pip install` 在该平台无法执行：musl 解释器记录的是绝对加载器路径，Android 上不存在该
路径，且根文件系统只读。`pyinstall.sh` 从 PyPI 解析 wheel，将其纯 Python 内容解压至共享的 `pylib` 目录；
含编译扩展的发行版一律拒绝，不会绑定到不匹配的 ABI。

**资源解包**。`aapt2` 会丢弃名称以 `_` 开头的资源目录，导致随包标准库中 `zipfile/_path`、`pip/_internal`
等路径缺失。这些目录以 `x__<原名>` 形式存放，解包时还原。

## 环境按需获取

随包工具链是下限，不是上限。任务需要某个当前不存在的能力时，由 Agent 在设备上自行获取，不需要主机参与，
也不需要用户预先准备。

| 通道 | 机制 |
|---|---|
| 任意 HTTP | `netrun` 包装 `curl`，可访问的 URL 均可拉取 |
| 源码检出 | 通过 `remote-https` 执行 `git clone` |
| Alpine 软件包 | `zafiro_env apk` 在 chroot 内执行 `apk add`，用于 JDK、头文件、系统库 |
| Python 发行版 | `pyinstall.sh` 从 PyPI 解析 wheel，解压至 `pylib` |
| 扩展包 | `zafiro_env install` 将 `.tar.gz` 解压至工具根目录 |
| 临时二进制 | 静态链接的 musl 构建，赋予可执行权限后直接运行 |

两条限制是强制校验，而非文档里的注意事项。含编译扩展的发行版一律拒绝：解释器为 musl 链接，glibc wheel
会绑定到不匹配的 ABI。任何依赖平台不存在的加载器路径的操作会立即失败，而不是静默降级。

已获取的内容持久化在私有工具根目录中，一次会话取得的能力对后续会话仍然可用。

实际影响需要说明：纯 Python 库数秒内即可安装，而依赖 C 编译的库要么由 Alpine 软件源提供，要么不可用。
原生能力改由随包二进制提供 —— `curl`、`jq`、`sqlite3`、`rg` 等已覆盖通常需要扩展模块的常见场景。

## 构建闭环

工具链完整度足以产出应用自身的制品。针对源码树的请求全程在设备上完成：定位文件、修改内容、执行测试、
调用 Gradle、产出 APK。无主机参与，亦不访问外部构建服务。

```text
请求 → shell        rg -n 'shields.io' README.md
     → view_image   /data/local/aitools/out/preview.png
     → shell        sed -i 's/…/…/' README.md
     → zafiro_env   build    →  gradle :app:assembleDebug，后台执行
     → zafiro_env   status   →  BUILD SUCCESSFUL, RC=0
     → 构建完成，已产出新 APK
```

Alpine rootfs、JDK 17 与 Android SDK 平台 35 / 36 / 37.0 于首次使用时配置。仅无法在运行时获取的组件
（`aapt2`、其 shim、签名与 no-lint init 脚本）作为 assets 随包分发。

## Agent 运行时

一个回合表示为带类型的事件流，而非解析文本。工具调用、上下文压缩、子 Agent 与上下文压力均建模为一等状态。

- 会话在进程终止后保留；空闲会话释放模型槽位
- 重试次数由配置限定
- provider、base URL、温度、reasoning effort 与超时均按模型配置
- 凭据由用户提供，不内置任何端点

## 会话与上下文管理

长会话通过摘要处理，而非截断。

- **自动压缩**。上下文占用达到模型窗口的配置比例时 —— 默认 70%，可在运行时设置中调整或关闭 —— 引擎通过
  一次独立的模型调用对较早的部分生成摘要，并逐字保留最近若干轮。压缩点会在会话时间线中记录为分隔线，
  使摘要区间与原始历史的边界保持可见。
- **手动压缩**。发送 `/compact` 可在任意占用率下强制走同一路径。
- **重复检测**。七档灵敏度，从关闭到最严格，用于抑制模型输出的退化循环。
- **增量输出**。模型响应边生成边呈现；仍在执行的工具也会随输出产生即时上报，而不是等命令退出才返回。
  长时间构建与测试在执行过程中即可观察。
- **持久化**。进程终止后，会话从最后一轮继续。

## 工具

共十二个，各自独立开关。

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

`zafiro_env` 通过七个操作承接构建环境：`check`（枚举 21 种工具链）、`build`、`status`、`install`、`shell`、
`apk`、`snapshot`。屏幕控制对原生视图层使用无障碍服务；视图层未暴露时，改用 `su` 身份执行坐标输入。

## 技能

Markdown 文档，按需加载。未被请求的技能不占用上下文。

| 技能 | 用途 |
|---|---|
| `build` | 编译的唯一入口：Gradle、AGP、SDK、NDK、`aapt2`、签名、APK 分析 |
| `hosttools` | 随包 CLI 能力清单，避免模型对可用命令作假设 |
| `zafiro-cmd` | 工具层命令的规范写法，避免模型自行拼接 `su` 与 `chroot` |
| `skill-creator` | 技能的编写、修改与测试 |
| `tool-config` | 通过沙箱内的 JSON 读写应用设置 |
| `termux` | 通过 SSH 接入另行安装的 Termux —— 首次配置与断连恢复 |

## 界面

界面完全由 Compose 实现。Material 3 Expressive，配合 Liquid Glass 与 Capsule 表面，支持浅色 / 深色 / 跟随
系统，六种语言可按应用切换，悬浮球用于快速唤起。终端基于 `libterm`，提供本地、libsu、Shizuku 与 SSH
四种 backend。

## 架构

| 模块 | 内容 |
|---|---|
| `app/` | UI、设置、repository、工具绑定、`MainActivity` |
| `agent-runtime/` | 回合引擎、工具注册表、上下文压缩、流式输出 |
| `business/api/` | 共享模型与工具函数 |
| `libs/libterm/` | 终端 core、runtime、backend |
| `assets/hosttools/` | 1,579 个文件的工具链及其 `VERSION` 指纹 |
| `assets/toolchain/` | `aapt2` 与其 musl `libz` |
| `assets/buildkit/` | 用于签名与关闭 lint 的 Gradle init 脚本 |
| `assets/skills/` | 六个技能文档 |

四条约束规范了对本仓库的改动：

- **指纹基于内容**。解包以资源清单的 SHA-1 为键，而非安装时间戳，因此应用更新不会重新解包 113 MB；
  个别缺失的关键资产单独修复。
- **一个包一个根域**。主安装在 `/data/local/aitools`，测试安装在 `aitools-btN`，两个安装不共享状态。
- **签名按序解析**。依次为 `-PRELEASE_STORE_FILE`、`keystore/debug.keystore`、`keystore/release.keystore`、
  AGP 默认值。干净克隆无需任何本地路径即可构建。
- **CI 校验仓库**。`verify_repo.py` 断言文档完整、未提交 keystore、无个人信息路径、无 tab 字符；
  `verify_hosttools.py` 重算资源指纹并与 `VERSION` 比对。

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

## 许可

MIT。见 [LICENSE](LICENSE)。
