---
name: zafiro-cmd
description: 命令调用格式速查（zafiro_env 正确写法全集）。凡是出现「调用工具 / 命令拼接 / 命令被拦截 / 报错看不懂 / 输出噪音 / su -c / 容器 / shell 工具 / 构建命令 / 编译命令 / 解压 / install / check / status / deploy / md5 / 怎么执行」这类问题，先读本技能照抄，不要自己拼命令试错。
---

# 命令格式速查（全部实测过）
> 命令本身的兼容性（`\|` 静默失效、`diff -x` 不支持、`find -printf`、aapt 是 x86_64）见 `build` 技能第 4 节「命令兼容性」，先读再拼命令。

**优先级**：能用宿主内置工具（52 个：rg/jq/sqlite3/file/tree/7zz/zip/rsync/curl/xz/zstd/lz4/python3+pip/git/openssl，
见 `hosttools` 技能）就别进容器；只有编译、跑 gcc/cmake、装 apk、出 APK 才用 `zafiro_env`。

工具内部已经处理提权、进容器、参数转义。**你只传裸命令，一个 action 干一件事**。

## 0. 先判断属于哪类
| 要做的事 | 用哪个 | 别用 |
|---|---|---|
| 读/改工程里的文件、列一层目录 | `shell` 工具（宿主 root） | 不要进容器 |
| 编译、跑编译器、装 apk 包、查环境 | `zafiro_env` | 不要手拼提权前缀 |
| 出 APK | `zafiro_env(action="build")` | 不要手拼 gradle 命令 |

## 1. 环境状态（deploy/install 已退场）

**部署编排已从 App 移除**：没有 `deploy` 按钮、没有 DevenvInstaller、install-full.sh 也不在 APK 里了。
环境出问题不要再想"重新部署"，按下面处理：

```sh
sh /data/local/aitools/env-doctor.sh        # 25 项实测体检，缺什么会带 fix 行
```
- 构建失败时 `build-src.sh` **已自动**跑 doctor 并写进日志尾部，先看日志
- 缺件按 fix 行手动补；宿主工具在 `files/hosttools`（见 `hosttools` 技能）
- 判断环境在不在：`ls -A /data/local/aitools/env`（有 `rootfs`/`android-sdk`/`gradle-*` 即可），不要递归、不要 du

## 2. 构建（实测 BUILD SUCCESSFUL in 30~40s）
```
{"action":"build","args":"-PdevSuffix --max-workers=1 -Dorg.gradle.parallel=false -Dkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process"}
{"action":"status"}
```
- `args` 只放 gradle 参数，不要带 cd，不要带 build-src.sh。
- 返回带 `src:`（本次工程目录）和 `slot=N`（并发槽位），抢不到槽会排队，不会拒绝。
- 成功判据：`status` 出现 `BUILD SUCCESSFUL` 且 `RC=0`，再核 `产物/` 的文件大小与 md5。

## 3. 容器内执行（实测）
```
{"action":"shell","cmd":"gcc --version"}
{"action":"shell","cmd":"gcc -O2 -o /data/local/aitools/tmp/a /data/local/aitools/tmp/a.c && /data/local/aitools/tmp/a"}
{"action":"shell","cmd":"cmake -S /data/local/aitools/tmp/p -B /data/local/aitools/tmp/p/out -G Ninja && cmake --build /data/local/aitools/tmp/p/out"}
{"action":"shell","cmd":"md5sum /data/local/aitools/out/app-debug.apk"}
{"action":"shell","cmd":"rustc --version; node --version; go version"}                        # 多条用分号是允许的
{"action":"apk","cmd":"info -e gcc"}          # 查包是否安装，装了回显包名
{"action":"apk","cmd":"add --no-cache jq"}    # 装包（腾讯源）
```
**临时铁律**：临时文件、构建目录、草稿一律放 `/data/local/aitools/tmp/` —— 这是唯一被 run.sh 绑进容器的临时目录，宿主与容器同一份。禁用 `/tmp`、`/sdcard`；`/data/local/tmp` 只给 `pm install` 用。
容器 `/tmp` 是 rootfs 内部、与宿主不同份，产物会丢 → 一律不用。`临时/` 实测 cmake+ninja 零 `bad depfile` 噪音。

## 4. 宿主侧操作（不经过容器）
```
sed -n '100,130p' /data/local/aitools/project/<源码同名目录>/app/src/main/AndroidManifest.xml
ls -A /data/local/aitools/project/<源码同名目录>
tail -20 /data/local/aitools/logs/build-py.log
```
- 宿主 `python3` 路径：`/data/user/0/com.hoerire.tools/files/hosttools/bin/python3`

## 5. 绝对禁止（执行规则会直接拦）
1. 手拼提权前缀（自己写 s-u 加 -c 再套引号），多套引号必然产生噪音与转义失败
2. 手工进容器：chroot、exec chroot
3. 一个 action 里用管道串多条不相关命令，塞 while / set -e / export
4. cd 加 && 的前缀拼接式构建
5. 全盘扫描：递归列目录、find 从根开始且无 maxdepth、du 扫 /data

## 6. 被拦截了怎么办
- 看到 `COMMAND_BLOCKED` 与 `matched_rule_name`：写法违规，改成上表写法重试一次，不要换拼法继续撞。
- 看到 `[timeout]`：命令超过 110s，拆小或用 build + status。
- 看到 `ENV_NOT_INSTALLED` / `PACK_NOT_FOUND`：跑 `sh /data/local/aitools/env-doctor.sh` 看缺哪项，按 fix 行手动补（**没有 deploy 了**）。

## 目录地图（先读这个）
`/data/local/aitools/{源码,工程,产物,日志,tmp}` + `环境/`（构建工具链，缺件跑 env-doctor）。
`源码/zafiro-15-1.8.1-Fork` 就是权威源码树，直接改，别管它内部模块怎么分。
