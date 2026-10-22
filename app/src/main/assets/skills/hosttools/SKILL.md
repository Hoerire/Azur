---
name: hosttools
description: 宿主侧内置命令行工具集（rg 搜索 / jq JSON / sqlite3 数据库 / file 类型识别 / fd 找文件 / fzf / tree / 7zz / zip / rsync / curl / xz / zstd / lz4 / python3+pip / git / openssl / busybox 305 applet）。凡出现「搜索文件 / 搜代码 / grep / find / 找文件 / JSON 解析 / jq / 数据库 / sqlite / 识别文件类型 / 目录树 / 解压 / 压缩 / 7z / zip / zstd / xz / lz4 / rsync / curl / wget / awk / sed / md5sum / sha256sum / 宿主没有 python3 / 没工具 / 没有解释器 / 命令找不到」先读本技能，直接用，不要绕容器、不要自己下二进制。
---

# 宿主工具集（53 个命令，全部真机实测）

```
/data/user/0/com.hoerire.tools/files/hosttools/     ← app 私有目录
  bin/       53 个命令（直接 exec；musl 动态工具的 wrapper 已自动带 loader）
  toolchain/ aapt2(Android 原生 ELF) + lib/*.so —— 容器 aarch64 构建必需，bootstrap 自动拷到 环境/toolchain
  applets/   305 个 busybox 软链（awk sed grep tar md5sum xargs sort uniq …）
  libexec/   动态工具真身        lib/  库 + python3.12 标准库 + site-packages(pip)
  TOOLS.txt  全量清单
```
已注入默认 PATH（`/data/adb/ksu/bin` 是 bin 的软链）→ **直接敲命令名**，不用写全路径。

## 铁律
1. 命令找不到 / Permission denied / 构建失败 → 先跑 `sh /data/local/aitools/env-doctor.sh`（**构建失败已自动跑，看日志尾部**）；缺构建件再跑 `sh /data/local/aitools/bootstrap-env.sh`（装配器：缺件时**自动走 netrun 下载并装配**，含 `apk add openjdk17` 与 aapt2 toolchain 自愈）。不要乱下二进制。
2. 缺工具先查 `$HT/bin`、`$HT/applets`、`TOOLS.txt`；确实没有按最后一节扩装。
3. **联网用 `netrun`**：`netrun $HT/bin/curl -sSL -o f url`、`netrun $HT/bin/git clone ...`、`netrun $HT/bin/python3 -m pip install ...`
   （原理：unshare -m + tmpfs 盖 /etc 写 resolv.conf + CA bundle，只影响本进程树；实测 https 200）。App 层 `py_download_file` 仍可用作备选。

## 速查
| 需求 | 命令 | 备注 |
|---|---|---|
| 搜内容/代码 | `rg -n "pat" /path` | `-g '*.kt'` `-e` 多模式 `-c` 计数 |
| 找文件 | `fd -e sh name /path` | 比 find 快，默认忽略 .git |
| JSON | `jq -c '.a.b' f.json` | 1.7.1 |
| SQLite | `sqlite3 db "select 1"` | 3.45.3，可建库 |
| 文件真类型 | `file -b x.apk` | 带 magic.mgc，比 toybox 准 |
| 目录树 | `tree -L 2 -d /path` | 2.1.1 |
| 压缩/解压 | `zstd -19 f` `xz -9 f` `lz4 f` `7zz a o.7z d` `zip -r a.zip d` | |
| 同步 | `rsync -a --delete src/ dst/` | 3.4.3 |
| HTTP | `netrun $HT/bin/curl -sSL -o f url` | 裸 curl 无 DNS；netrun 内才有 |
| 文本 | `awk sed grep sort uniq head tail cut tr diff md5sum sha256sum tar xargs` | busybox applet |
| 脚本 | `python3 x.py`；`netrun pip install pkg` | Python 3.12.13 |
| git 本地 | `git init/add/commit/diff/log` | 完整可用 |
| git 取远端 | `netrun curl -L https://codeload.github.com/u/r/tar.gz/refs/heads/main -o r.tgz && tar xzf r.tgz` | clone 不可用 |
| 诊断 | `lsof -p PID`、`strace -f -e openat cmd` | |

## 什么时候别用它（省得白试）
- `file` / `curl` 要完整能力：`/system/bin` 在 PATH 里排第一，写全路径 `$HT/bin/file`
- 编译 / gradle / 出 APK：用 `/data/local/aitools`（环境在 `环境/`，不是本技能）
- `pip install` 联网：用 `netrun $HT/bin/pip install pkg`（裸 pip 无 DNS）

## 扩装一个命令（成本从低到高）
1. **单文件静态二进制**：下到 `$HT/bin/<name>`，`chmod 755` 即可。
2. **Alpine arm64 包**（apk 就是 tar.gz，包通常几十 KB）：
   ```sh
   tar -xzf pkg.apk -C /data/local/aitools/临时/x        # usr/bin/X、usr/lib/*.so*
   cp .../usr/bin/X $HT/libexec/X && chmod 755 $HT/libexec/X
   printf '#!/system/bin/sh\nexec %s/lib/ld-musl-aarch64.so.1 --library-path %s/lib %s/libexec/X "$@"\n' "$HT" "$HT" "$HT" > $HT/bin/X
   chmod 755 $HT/bin/X; cp .../usr/lib/*.so* $HT/lib/     # 缺库逐个补
   # 有 libX.so.A.B 就建链：ln -sf libX.so.A.B $HT/lib/libX.so.A
   ```
3. **建项目 / 编译**：目录固定 `/data/local/aitools/{源码,工程,产物,日志,临时}`；改完先 `sync.sh` 再构建；gradle 串行，只带 `--max-workers=1`。
