# AGENTS.md — Anvil 开发约定


## 1. 架构事实（改代码前必读）

- 模块：`app`（应用/UI/设置/工具管理）、`agent-runtime`（Agent 循环/提示词/内置工具/本地执行）、
  `business/*`（api agent files notification application permission）、`libs/*`（logging okia termux）、
  `ui-kit`、`store`、`xsettings`、`remote-view`、`common-utils`。
- **没有 Chaquopy、没有内置 Python 运行时、没有 MCP、没有更新检测、没有 Xposed 接管**。不要重新引入。
- Python 能力 = 宿主 `files/hosttools/bin/python3`（CPython 3.12.13 musl），经 `shell` 工具以 **root** 调用。
- 12 个内置工具：`shell open_uri launch_app find_installed_apps notify load_skill memory screenshot view_image zafiro_env
  screen_operation_accessibility screen_operation_shell`。新增工具须同时改 `BuiltinToolRegistry.default()` 与（如需分组）`BuiltinToolGroups`。
- `LocalTool.Py` / `customPyTools` / `ToolSettingsCodec.parseCustomPyTools` 是**老设置 JSON 兼容层**，恒为空、不执行；不要"顺手清理"。

## 2. 构建

```sh
# 源码树改完必须同步到工程树（单向镜像，工程树不手改）
sh /data/local/aitools/工程/sync.sh zafiro-15-1.8.1-Fork
# 构建（VARIANT=debug 必填，脚本默认 release，而 app 无 release 变体）
cd /data/local/aitools && ZAFIRO_VARIANT=debug sh env.sh build --max-workers=1
# 产物
/data/local/aitools/产物/*.apk   # RC=0 + size/md5 才算出包成功
```

改了 `assets/**` 或 Kotlin 之后先 `rm -rf 工程/<tree>/app/build`，否则 Gradle 判 up-to-date 不重打包。

首次在新机/新环境构建需要自举（联网）：`sh /data/local/aitools/bootstrap-env.sh`
（rootfs + `apk add openjdk17` + Android SDK + Gradle；SDK 里 `platforms/android-37.0` 需改成 `android-37` 并改 `source.properties`）。
aapt2 / libz / init.gradle 已随包分发（`assets/toolchain`、`assets/buildkit`），部署由 `HostToolsInstaller` 完成。

## 3. 测试

```sh
sh /data/local/aitools/run-plain-tests.sh    # JVM-only 子集
sh /data/local/aitools/build-test.sh         # 全量；Robolectric 在 aarch64-musl 上必挂（无 nativeruntime）
```

已知非代码失败：`ShellBuiltinTest`（容器以 root 跑，期望 `default` identity）、
`XRepoRuntimeGatewayTest.readLlmConfig_*`、`BaseSessionBuiltinTest.handleCommand_*`。

## 4. Python 装包

`pip install` 永久不可用（musl `PT_INTERP=/lib/ld-musl-aarch64.so.1` 在 Android 上不存在）。
纯 Python 包一律：`sh /data/local/aitools/pyinstall.sh <pkg>`（下载 `py3-none-any.whl` 解到 `pylib`）。

## 5. 改动铁律

1. 只改 `源码/<tree>`，改完 `sync.sh`；工程树是镜像。
2. 改 `assets/hosttools/**` 后必须重算 `assets/hosttools/VERSION`（对全量 1579 文件求 `(相对路径:大小)` 的 SHA-1），
   否则 `stamp` 指纹不匹配 → 安装后重铺 113 MB。
3. assets 里以 `_` 开头的目录必须写成 `x__<原名>`（aapt 会丢弃 `_` 开头的目录），由 `copyDir` 在复制期还原。
4. 设备侧读文件断言必须 `nsenter -t 1 -m --`（本机 shell 在隔离 mount namespace，直读会得到相反结论）。
5. 杀进程用 `am force-stop`（`pm` 没有该子命令）；`am start` 对在跑进程只切前台，不重跑 `onCreate`。
6. 装包前先输出改动摘要（文件表 + versionCode/size/md5）。

## 6. 文档索引

| 文件 | 内容 |
|---|---|
| `README.md` / `README_CN.md` | 当前版本特性（对外） |
| `文档/设备内执行-坑位清单.md` | 设备内跑命令的坑：输出污染/截断、namespace、am-vs-pm、首装假死、网络 |
| `文档/HANDOFF-hosttools.md` | hosttools/铺开/根域/构建资产的踩坑记录 |
| `assets/skills/*` | 运行期技能（App 内 load_skill 用，随包分发） |
| `.agents/skills/*` | 开发流程技能（13 个，已按本项目重写） |
