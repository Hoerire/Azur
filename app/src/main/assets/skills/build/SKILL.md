---
name: build
description: 本地构建与出包入口。凡涉及构建/编译/打包/出 APK/assemble/R8/签名 keystore/versionCode/applicationId/gradle/AGP/SDK/aapt2/NDK/工具链/env 部署/rootfs/容器/安装 APK/pm install/反编译/解包/dex/smali/AXML/ELF/readelf/BUILD FAILED/编译报错/命令找不到，先读本技能。
---

## 铁律：装包前先报改动（用户强制要求）

**执行门禁**

- 装包前先出摘要，**摘要之后立即执行安装**，不要停在摘要上等确认。用户已明确授权装包时，摘要只是履行步骤，不是等待点。
- 摘要必须含：本轮 diff 的文件表（每个文件一行：改了什么）、产物路径、size、md5、versionCode。
- 没写摘要就装 = 失败。用户会直接打断并要求重来。

**写摘要前顺手核对：**

1. 改完源码树必须 `sync` 到构建树再构建，否则装上去的是旧代码。
2. 装包后 `HostToolsInstaller` 会用 **APK 里的 `assets/buildkit`** 覆盖 `/data/local/aitools/*.sh`。手工改过根脚本后不重新出包，下次装包就被回滚（表现为 `build-src.sh` 又变成中文路径、构建报 `SRC 必须在 .../project/ 下`）。


任何 `pm install` / 覆盖安装 **之前**，必须先输出本轮改动摘要，再装：

1. **改动文件表**：`文件 → 改了什么`（删了哪个类/字段/分支，压缩成一行）
2. **产物信息**：versionCode、size、md5、APK 路径
3. **一句话**："现在安装 vXX"
4. 安装后只报：`Success` + versionCode 核对 + crash buffer 是否为 0

禁止：先装再解释、只报 md5 不报改动、把摘要塞在安装命令后面。
没给摘要就装 = 失败，用户会要求重来。


# 构建（build）

## Python 依赖：没有 pip，用 pyinstall.sh

宿主 `hosttools/bin/python3` 是 3.12.13 **纯 stdlib**：`site-packages` 里只有坏掉的 pip 骨架，`zipfile._path` 也被裁掉（已在 hosttools 内补 stub）。缺依赖时从网上补，别折腾 pip。

```sh
# 装纯 Python 包到正式位置
sh /data/local/aitools/pyinstall.sh requests urllib3 idna certifi charset-normalizer html2text

# 用
H=/data/user/0/com.hoerire.tools/files/hosttools
PYTHONPATH=/data/local/aitools/pylib $H/bin/netrun $H/bin/python3 script.py
```

原理：`netrun curl` 拉 `https://pypi.org/pypi/<pkg>/json` → 挑 `py3-none-any.whl` → `7zz x` 解到 `/data/local/aitools/pylib`。

- **能装**：纯 Python 包（requests / urllib3 / idna / certifi / charset-normalizer / html2text …）
- **装不了**：带 `.so` 的 wheel —— hosttools 是 musl 静态构建，glibc 扩展加载不了（numpy / lxml / pillow 这类要另起 glibc 环境）
- 联网一律套 `netrun`，musl 不走 Android resolver
- 依赖放 `/data/local/aitools/pylib`（不是 `tmp/`，换构建不清）

## 0. 铁律

- 构建前先跑 `sh /data/local/aitools/verify.sh`；它只读、输出 PASS/FAIL、退码非 0 即存在真实问题。**不要用自己的叙述代替它**。
- 可写只有 `/data/local/aitools/`；例外：`pm install` 用 `/data/local/tmp`（装完删）。
- 改代码只改 `src/<树>`；构建只跑 `project/<同名树>`；同步只走 `project/sync.sh <源码目录名>`。
- 产物→`out/`；日志→`logs/`；中间件→`tmp/`（任务结束清空）。
- 执行规则会硬拦：手搓 `chroot`、`su -c`、`env/run.sh`、直接敲 `gradlew`、`grep -P`、`ls -R`、无 `-maxdepth` 的 `find`。
- 命名铁律：`project/<名>` 必须与 `src/<名>` 同名；`build-src.sh` 解析后强校验，不同名 `exit 2` 并列出可用源码。
- 例外：内核树可命名 `project/kernel-<soc>-<ver>`（如 `kernel-sm8750-6.6`），它不走 `build-src.sh`，命名铁律只约束 app 构建。

## 0.1 工具优先级（先查再动手，禁止一个个试）
容器内（`zafiro_env(action="shell")`）已装、直接用，**优先用这些**：
```
编译  gcc g++ clang clang++ lld ld objcopy ar as nm strip readelf objdump addr2line
构建  cmake ninja make pkg-config samu
脚本  python3 node npm git rg jq sqlite3 tree xxd strace lsof file diff patch sed awk
压缩  tar gzip zip unzip 7z aria2c lzop pzstd zstd xz bzip2
网络  curl wget nc ss ip ping traceroute whois
加密  openssl keytool jarsigner
Java  java(17/21) javac jar javadoc javap jlink jimage
安卓  aapt2 zipalign apksigner adb fastboot lpmake lpunpack unpack_bootimg mkdtboimg avbtool simg2img img2simg
内核  pahole bc bison flex m4 dtc kmod rsync cpio
语言  go(/usr/lib/go/bin/go，不在 PATH) gofmt rustc cargo ruby gem perl php lua5.4
```
- 事实基线：rootfs 有 725 个可执行、259 个 apk 包；找不到先 `command -v X` → `ls /usr/bin` → 才考虑 `zafiro_env(action="apk", cmd="add --no-cache X")`。
- **宿主**（`shell` 工具）：无 gcc/clang/python3/node/git，只有 `/system/bin/curl`；宿主 `aapt/aapt2` 是 x86_64 → `not executable: 64-bit ELF`。
- 已知缺口（别浪费试错）：`pip3`、`apktool`、`jadx`、`baksmali`、`meson`、`mkbootimg` 都没有（boot 镜像用 `lpmake/lpunpack/unpack_bootimg`）。

## 1. 目录
```
env/  rootfs run.sh env.sh android-sdk/ gradle-<ver>/ gradle-home/ toolchain/ debug.keystore
project/  <源码同名目录>（gradlew 只在这里跑）+ sync.sh
src/  权威源码，只改不编译（必须无 build/ .gradle/ .kotlin/）
out/  logs/  tmp/
```

## 2. env
- 有 `env/env.sh` 就是装好了，不要重建。查版本 `zafiro_env(action="check")`。
- 缺 → `sh /data/local/aitools/bootstrap-env.sh`（幂等装配器：缺 rootfs/gradle/platform×3/build-tools/cmdline-tools 时**自动走 netrun 下载并装配**，含 `apk add openjdk17`、aapt2 toolchain 与 debug.keystore 自愈，全程不用人工下载）。`build-src.sh` preflight 缺 gradle 时会自动调它；构建失败会自动跑 env-doctor 并把结果放日志尾部。
- 体检/补装：`sh /data/local/aitools/aitools-fix.sh --check`，加 `--with-ndk --with-platforms --with-cmdline` 自动补。
- 依赖缓存 `env/gradle-home`（实测全量约 1.39G）：首次构建由 gradle 自己拉，无预热脚本、无 seed 文件。是否已缓存用 `du -sh env/gradle-home` 判断，不看文档。

## 3. 出包
```sh
zafiro_env(action="build", args="-PdevSuffix --max-workers=1")            # 工程下只有一个工程时
sh /data/local/aitools/build-src.sh --src=/data/local/aitools/project/<名> --max-workers=1
```
- 成功三判据：日志 `BUILD SUCCESSFUL`、`RC=0`、`path: out/…apk`。
- 脚本自动落盘并打印 `path/size/md5/appId=…`；自定义名 `ZAFIRO_APK_NAME=`，不落盘 `ZAFIRO_NO_COPY=1`。
- 必须串行，`--max-workers=1` 必带。热构建 2s、冷 15-30s、全模块首编 4-5 分钟。
- 强制重打：删 `app/build/outputs/apk/<variant>/app-release.apk` 再跑，比 `--rerun-tasks` 快。

## 4. 命令兼容（实测）
| 写法 | 宿主 toybox | 容器 busybox | 改用 |
|---|---|---|---|
| 交替用 `\|` | ✗ 静默 0 | ✓ | `-E 'a|b'` 或 `-e a -e b` |
| `-P` 正则 | ✗ | ✗ | `-E` |
| `sed -n` 交替 | ✗ 空输出 | ✓ | `sed -nE` |
| `diff -x` / `--exclude` | ✗ | ✗ | md5 清单法 |
| `find -printf` | ✓ | ✗ | `-exec` |
`awk / stat -c / xargs -r / sort -k2 / unzip -l / readlink -f` 两端都行；`sed -i` 用 `#` 遇到含 `##` 的文本会 `bad pattern` → 换 `@`。

## 5. 改源码 & 校验
**修复铁律（三步，缺一即失败）**：① 改 `src/` ② `sh project/sync.sh <源码目录名>` 必须回 `SYNC OK` ③ 才允许构建。
漏第②步 = 编译的是旧代码，白等一轮（已踩）。

```sh
cp -p f f.bak && sed -i '453s@.*@新内容@' f && sed -n '450,456p' f    # 行号定位最稳，改完必须回读
sh /data/local/aitools/project/sync.sh <源码目录名>                       # 唯一同步入口，内含 md5 diff
```
- 批量改写优先容器内 `python3`（引号地狱少）。
- 校验类改动最容易假失败：先看磁盘真实产物，再看脚本判定。

## 6. 容器执行
```sh
zafiro_env(action="shell", cmd="gcc -O2 -o /data/local/aitools/tmp/a /data/local/aitools/tmp/a.c && /data/local/aitools/tmp/a")
zafiro_env(action="apk", cmd="add --no-cache jq")
```
- 容器只 rbind `src/project/out/logs/tmp`：根下脚本在容器里是 rootfs 旧副本，必须在宿主 `shell` 改。
- 容器 `/tmp` 与宿主不同份，别当工作区。

## 7. 装包与验证
```sh
cp x.apk /data/local/tmp/x.apk && pm install -r /data/local/tmp/x.apk && rm -f /data/local/tmp/x.apk
```
- 版本信息读 `output-metadata.json`；APK 内 zip 时间戳被规范化成 1981，判新旧看宿主 `ls -l` mtime。
- 共存测试：在**工程副本**把 `applicationId` 改成 `<包名>.beta`，Fork 源码永远保持原包名。

## 8. 分析/反编译
可用：`unzip -l/-p`、`file`、`readelf -hd`、`nm -D`、`objdump -d`、`strings -n 6`、`java -jar build-tools/<v>/lib/apksigner.jar verify --print-certs`、`d8`。
不可用：`apktool`、`jadx`、`baksmali`（要用就下 release zip 到 `tmp/`，用完删）。`unzip -p x.apk AndroidManifest.xml` 出来是二进制 AXML，解析用容器 python3，或直接读 `output-metadata.json`。

## 9. 踩坑（实测）
- `>>` 追加到无换行结尾的文件会粘连 → 先 `printf '\n' >> f`。
- 宿主别定义 shell 函数（`fc: history functions not available`）；`shell` 工具别传 `workdir`（`Session gone`），用绝对路径。
- 目录改名后先 `grep -rn 旧名 /data/local/aitools/tmp` 改掉脚本里写死的路径，否则镜像脚本认为「源为空」把目标清空（实测删过 1076 个文件）。破坏性脚本先 dry-run 打印将删数量。
- 长任务 `nohup … > logs/x.log 2>&1 &` 后按固定间隔 `tail -n 4`，禁止紧循环轮询。
- 内存 <300M free 先 `zafiro_env(action="shell", cmd="gradle --stop")` 再编译。

## 10. 报告
结束必贴：`path / size / md5 / built` + 关键日志 20-30 行 + 未验证项标「需验证」。

## hosttools 内置 Python：aapt `_` 目录坑（v26 已修）
- aapt 打包会**丢弃 assets 下以 `_` 开头的目录**（文件保留）→ 曾缺 `zipfile/_path`、`pip/_internal`、`pip/_vendor`。
- 解法：assets 侧目录改名 `x__<原名>`，`HostToolsInstaller.copyDir` 用 `removePrefix("x__")` 还原。
- `pyexpat` 需 `assets/hosttools/lib/libexpat.so.1`（musl 2.8.1）。
- pip 只能 `--version`，**不能 `pip install`**（musl PT_INTERP=/lib/ld-musl-aarch64.so.1 不存在）。装纯 py 包用 `sh /data/local/aitools/pyinstall.sh <pkg>`。

## hosttools / assets 改动后的重铺

- HostToolsInstaller 的重铺开关是 `app/src/main/assets/hosttools/VERSION` 的 **SHA-1**（stamp 文件 = `files/hosttools.stamp`），**不是** APK lastUpdateTime。改了 hosttools 里的二进制却不动 VERSION → 装完新版用户手里还是旧文件（aapt2 mtime 不变、新加的 so 不出现），看着"改了没生效"。
- 改 hosttools 必须同轮改 VERSION（追加一行标记，如 `toolchain-clean-1`）。
- **assets 下有两份 toolchain**：`assets/toolchain`（进 APK）与 `assets/hosttools/toolchain`（铺到 files/hosttools）。只改一份 → APK 里还是旧的。先 `find . -type d -name toolchain` 找全。
- 判断是否真重铺：`ls -l files/hosttools/toolchain/aapt2` 的 mtime + `cat files/hosttools.stamp`。
- 改了 assets 里的大文件（assets 目录 >10MB）要 `rm -rf project/<tree>/app/build` 再构建，否则 AGP 复用旧 assets 缓存。
- 技能只改 `assets/skills/`，禁止手改 `files/skills/`：后者是播种产物，手改会让 `.seed` 与内容失配，播种逻辑随即判定「用户已改」而不再更新；assets 里删掉的技能不会自动从运行目录消失，需要手动清理。

## 出包前守卫：assets 洁净度

```sh
# assets 不应带构建机的绝对路径（换机必崩）
# verify.sh 拼接字面串构造模式，避免脚本自匹配
sh /data/local/aitools/verify.sh || exit 1
```

- 第三方预编译二进制（aapt2 及其依赖库）常把构建机路径写进 ELF：RUNPATH 指向构建机的绝对 `usr/lib`，`.rodata` 带构建目录。这类二进制只在装有同款环境时能跑，换机立刻 `library "libz.so.1" not found`。
- 净化（不需要重编）：`patchelf --set-rpath '$ORIGIN'`（库）/ `'$ORIGIN/lib'`（可执行）；`.rodata` 等长掩码（长度不变、不动偏移）；把闭包缺的库（libz.so.1 / libexpat.so.1 / libbz2.so.1.0）收编进 `toolchain/lib`。
- 验收：**不设** LD_LIBRARY_PATH 直接跑 `toolchain/aapt2 version`，再 `aapt2 compile <某个 values.xml>` 出 zip。
