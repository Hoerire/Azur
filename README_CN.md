# Anvil

住在手机里的 AI 编程环境。

Anvil 是一个 Android Agent 运行时，外面焊了一整套真实的开发工具链。APK 里带着 CPython、305 个
applet 的 busybox、ripgrep、jq、7z、curl、sqlite3、git 和 aarch64 的 `aapt2`；首次启动把它们铺进私有
目录，直接交给模型当可执行文件用。于是 Agent 能在手机上翻仓库、跑测试、拉一个带 JDK 和 Android SDK 的
Alpine chroot，然后在这台手机上把一个 APK 编出来。

包名 `com.hoerire.tools`。本次实测产物 `Anvil-1.0.apk`，98,110,774 字节。

## 来源

Anvil 从 [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1 起步，保留了它的 Compose UI 外壳、
设置框架、OKIA Agent 运行时和 `libterm` 终端。UI 之下全部重写：工具执行层、Python 方案、环境自举、
设置模型、构建流水线。上游署名保留在关于页。

## 相对上游的不同

### 1. 工具链随 APK 分发

上游假设设备上已经有可用工具，或者把 Python 交给内嵌解释器。Anvil 把 1,579 个真实 aarch64 二进制
作为 assets 打包，首启铺到 `/data/user/0/com.hoerire.tools/files/hosttools`。

| 组成 | 明细 |
|---|---|
| 解释器 | CPython 3.12.13（musl），完整标准库，`ssl` / `sqlite3` / `zipfile` / `xml` / `zoneinfo` / `email` 都在 |
| Python 包 | `requests` 2.34.2、`urllib3` 2.8.0、`idna` 3.20、`certifi` 2026.10.19、`charset_normalizer` 3.5.2、`html2text` 2025.4.15、`pip` 24.0 |
| Shell 工具 | busybox，305 个 applet |
| 独立 CLI | `rg`、`fd`、`fzf`、`jq`、`7zz`、`curl`、`sqlite3`、`file`、`rsync`、`wcurl`、`xz`、`zstd`、`lz4`、`strace` |
| Git | `git` 2.45.4，带 `git-core` 与 `remote-https`，Agent 能直接 clone / push |
| Android 构建工具 | `aapt2`（bionic aarch64，3.5 MB）+ 它需要的 musl `libz` |
| 运行时库 | 71 个 `.so`，含 `ld-musl-aarch64.so.1` 与 `libexpat.so.1`（`pyexpat` 需要） |

这些二进制都是 musl 静态构建，普通 Android 上直接跑，不需要 chroot。联网统一走 `netrun`：它把 DNS
和 TLS 送出应用沙箱，`curl`、`pip`、`git` 都包在它上面。

### 2. 构建环境跑在设备上

`zafiro_env` 是新工具，给模型七个动作：`check`、`build`、`status`、`install`、`shell`、`apk`、`snapshot`。
用它 Agent 能查 21 种工具链版本、后台起 Gradle 构建、轮询日志、解压扩展包，全程不需要主机。

它下面是一条自举链路：按需拉 Alpine rootfs、JDK 17、Android SDK 35/36/37.0。SDK 本身不进 APK，只有
事后取不到的东西（`aapt2`、它的 shim、签名 init 脚本、no-lint init 脚本）作为 assets 随包走。

这两段合起来才叫自举：`build` 编的就是 Agent 自己刚改过的源码，跑在正在运行 Agent 的这台手机上。

### 3. Python 是真解释器，不是内嵌运行时

上游用 Chaquopy 跑 Python，那套东西会把基于 wheel 的 ABI 拖进 APK。Anvil 把它整个删了，连带删掉
依赖它的一切：`execute_python` 工具、`py_meta_tools`、自定义 Python 工具编辑器、runtime python 包
及其测试。替代方案是按 `su` 身份拉起一个普通 CPython 进程 —— 不兼容的 wheel 会直接报错，而不是悄悄绑
到错配的 ABI 上。

这个平台上 `pip install` 跑不了（musl 把解释器路径写死了，Android 没有那个路径），装包走
`pyinstall.sh`：从 PyPI 取 wheel，把纯 Python 文件解到共享的 `pylib` 目录。只收纯 Python，带编译扩展
的一律拒绝。

### 4. 启动更新检测

冷启动时向 GitHub 查 `Hoerire/anvil` 的最新 release，拿 tag 跟当前 `versionName` 比对，有新版就弹窗。
关掉弹窗即记账，同一版本只打扰一次。draft 和旧的 prerelease 会跳过；还没有 stable 时回落到最近一个
release。

### 5. 从上游删掉的

| 删除项 | 原因 |
|---|---|
| Chaquopy 及其下游 | 被上面那套随包 CPython 取代 |
| MCP 客户端与服务端 | 树里不再有任何 MCP 依赖 |
| Xposed 模块 | 不再 hook 第三方助手，`xposed_init` 资源也删了 |
| Ssh 工具 | 终端的 SSH backend 还在，独立工具不要了 |
| `phone-use`、`install-skill-from-github` 技能 | 由 `zafiro-cmd` 和 `hosttools` 接管 |

### 6. 看不见但能感觉到的工程修复

- **铺开指纹**。上游只要 `lastUpdateTime` 变了就重铺资源树，等于每次升级都重解 113 MB，看起来像卡死。
  Anvil 改成按资源清单的 SHA-1 打指纹，并且只补缺失的关键资产。
- **根域隔离**。工具根目录按包名派生（`/data/local/aitools`，测试包是 `aitools-btN`），两套构建不会
  共用状态。
- **签名可移植**。`build.gradle.kts` 依次找 `-PRELEASE_STORE_FILE` → `keystore/debug.keystore` →
  `keystore/release.keystore` → AGP 默认，新克隆的仓库不填任何本地路径也能出包。
- **`aapt` 的下划线怪癖**。`aapt2` 会静默丢掉以 `_` 开头的资源目录，`zipfile/_path`、`pip/_internal`
  这些因此残缺。现在以 `x__<原名>` 打包，铺开时还原。

## 功能

### Agent 运行时

一个回合是带类型的事件流，不是一段字符串：引擎把工具调用、压缩、子 Agent、上下文压力都当作一等状态
跟踪。会话能扛住进程被杀，空闲会话释放模型槽位，请求失败按配置的重试次数重试。provider、base URL、
温度、reasoning effort、超时都是按模型配置，不是全局开关。

### 内置工具

十二个工具，每个在设置里都有独立开关。

| 工具 | 作用 |
|---|---|
| `shell` | 用随包工具链执行命令，支持 root，会话常驻 |
| `open_uri` | 打开 https / geo / tel / mailto |
| `launch_app` | 按名称或包名启动已装应用 |
| `find_installed_apps` | 解析已安装包 |
| `notify` | 发系统通知 |
| `load_skill` | 按需把技能文档拉进上下文 |
| `memory` | 跨会话持久化事实 |
| `screenshot` | 截屏 |
| `view_image` | 读磁盘上的图片 |
| `zafiro_env` | 查工具链、起构建、解扩展包 |
| `screen_operation_accessibility` | 通过无障碍服务读并操作 UI 树 |
| `screen_operation_shell` | 按坐标点按、滑动、发按键 |

工具设置页改成平铺，一个工具一个开关，两个屏幕操作工具并成一条组入口。

### 技能

六个技能，按需加载，不用就不占上下文。

| 技能 | 用途 |
|---|---|
| `build` | 编译唯一入口：Gradle、AGP、SDK、NDK、`aapt2`、签名、APK 分析 |
| `hosttools` | 随包 CLI 清单：`rg`、`jq`、`sqlite3`、`7zz`、`curl`、`python3`、305 个 applet |
| `zafiro-cmd` | 工具层命令调用格式，避免模型自己拼 `su` / `chroot` |
| `skill-creator` | 编写、修改、测试新技能 |
| `tool-config` | 靠改沙箱里的 JSON 读写应用设置 |
| `termux` | Termux 首次 SSH 配置与断连恢复 |

### 界面

全 Compose，Material 3 Expressive 加 Liquid Glass、Capsule 质感，浅色 / 深色 / 跟随系统，六种语言的
应用级语言选择，悬浮球快速唤起，终端基于 `libterm`，支持本地、libsu、Shizuku、SSH 四种 backend。

## 环境要求

- Android 8.0+（`minSdk 26`），`compileSdk 37`。
- Agent 本体不需要 root。但 `shell` 要以 `su` 身份跑、设备内构建环境、`/data/local/aitools` 下的东西
  都需要 root。KernelSU 与 Magisk 都行。
- `screen_operation_accessibility` 要先开无障碍服务。
- 首启解压约 113 MB，一次性 10–30 秒。

## 构建

```bash
git clone https://github.com/Hoerire/anvil
cd anvil
./gradlew :app:assembleDebug
```

签名可选，优先级从高到低：`-PRELEASE_STORE_FILE=/path/keystore` 配
`RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`；或在构建机上放一个
`keystore/debug.keystore`。两者都没有就用 AGP 默认 debug keystore。

仓库在 CI 里自己检查自己：`.github/scripts/verify_repo.py` 校验文档齐全、没提交 keystore、没漏个人
路径、没有 tab 字符；`verify_hosttools.py` 重算资源指纹并与 `assets/hosttools/VERSION` 对账。

## 工程结构

| 路径 | 内容 |
|---|---|
| `app/` | 应用模块：UI、设置、repo、工具胶水、`MainActivity` |
| `agent-runtime/` | 回合引擎、工具注册表、压缩、流式输出 |
| `business/api/` | 共享模型与工具函数 |
| `libs/libterm/` | 终端 core、runtime 与 backend（本地 / libsu / Shizuku / SSH） |
| `app/src/main/assets/hosttools/` | 1,579 个文件的工具链，以及 `VERSION` 指纹 |
| `app/src/main/assets/toolchain/` | `aapt2` 与它的 musl `libz` |
| `app/src/main/assets/buildkit/` | 签名与关闭 lint 的 Gradle init 脚本 |
| `app/src/main/assets/skills/` | 六个技能文档 |
| `.agents/skills/` | 给改这个仓库的人用的开发技能 |

## 技术栈

| 层 | 选择 |
|---|---|
| 语言 / UI | Kotlin 2.2.0、Jetpack Compose、Material 3 Expressive、Liquid Glass、Capsule |
| 构建 | AGP 9.1.1（内置 Kotlin）、KSP、`compileSdk 37`、`minSdk 26`、`targetSdk 34` |
| Agent | OKIA 运行时、kotlinx-serialization、OkHttp |
| 存储 | Room 2.7.2 |
| 终端 | `libterm`（core / runtime / libsu / shizuku / ssh） |
| 图片 | coil-resvg |
| Python | CPython 3.12.13（musl），标准库 + 七个随包发行版 |

## 许可

MIT。原始作品版权 (c) 2026 niki914；修改与新增部分版权 (c) 2026 Hoerire。见 [LICENSE](LICENSE)。
