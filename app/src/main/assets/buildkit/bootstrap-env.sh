#!/system/bin/sh
# bootstrap-env.sh —— 构建环境装配器（幂等）
#
# 分工原则：
#   下载 → 走 netrun（自带 DNS + CA：unshare -m + tmpfs 盖 /etc 写 resolv.conf + ca-bundle.crt）
#          没有 netrun 时才退回 App 层 py_download_file
#   装配 → 本脚本：解压、放位、chmod、校验
#
# 用法：
#   sh /data/local/aitools/bootstrap-env.sh          # 检查 + 用 临时/dl 里已有的包装配，仍缺则打印下载清单
#   sh /data/local/aitools/bootstrap-env.sh --check  # 只检查（构建前 preflight 用，0 开销）
set -u
A=${A:-/data/local/aitools}; Z=$A/环境; SDK=$Z/android-sdk; DL=$A/临时/dl
M=https://mirrors.cloud.tencent.com
CHECK=0; [ "${1:-}" = "--check" ] && CHECK=1
mkdir -p "$DL"
say() { echo "[bootstrap] $*"; }

# keystore：直接从内置 hosttools/share 拷一份（APK 里带着，2.6KB）
HT0=/data/user/0/com.hoerire.tools/files/hosttools
if [ ! -f "$Z/debug.keystore" ] && [ -f "$HT0/share/debug.keystore" ]; then
  mkdir -p "$Z"; cp -f "$HT0/share/debug.keystore" "$Z/debug.keystore" && chmod 600 "$Z/debug.keystore" && say "装配 debug.keystore"
fi
# aapt2：容器 aarch64 跑不了 maven 的 x86_64 ELF，必须自备 Android 原生 aapt2
HT0=/data/user/0/com.hoerire.tools/files/hosttools
if [ ! -x "$Z/toolchain/aapt2" ] && [ -d "$HT0/toolchain" ]; then
  cp -rf "$HT0/toolchain" "$Z/toolchain" 2>/dev/null; chmod 755 "$Z/toolchain/aapt2" 2>/dev/null
  mkdir -p "$Z/rootfs/usr/local/bin"
  cp -f "$HT0/toolchain/aapt2.wrapper.sh" "$Z/rootfs/usr/local/bin/aapt2" 2>/dev/null
  chmod 755 "$Z/rootfs/usr/local/bin/aapt2" 2>/dev/null && say "装配 aapt2 toolchain"
fi

# ── 1. 检查 ──
MISS=""
chk() { if eval "$2"; then say "ok   $1"; else MISS="$MISS $1"; fi; }
chk "rootfs"        '[ -x "$Z/rootfs/bin/busybox" ]'
chk "gradle"        'ls -1d "$Z"/gradle-[0-9]*/bin/gradle >/dev/null 2>&1'
chk "platform"      '[ -f "$SDK/platforms/android-37.0/android.jar" ] && [ -f "$SDK/platforms/android-35/android.jar" ]'
chk "build-tools"   '[ -x "$SDK/build-tools/36.0.0/aapt2" ] && [ -x "$SDK/build-tools/36.0.0/apksigner" ] || [ -f "$SDK/build-tools/36.0.0/package.xml" ]'
chk "cmdline-tools" '[ -d "$SDK/cmdline-tools/latest/bin" ]'
chk "keystore"      '[ -f "$Z/debug.keystore" ]'

# rootfs 只是裸 minirootfs：gradle 要 java，必须自己 apk add（用 run.sh 进容器）
[ -f "$Z/env.sh" ] || { mkdir -p "$Z"; cp -f /data/local/aitools/env.sh "$Z/env.sh" 2>/dev/null; chmod 755 "$Z/env.sh" 2>/dev/null; }
[ -x "$Z/run.sh" ] || { mkdir -p "$Z"; cp -f /data/local/aitools/run.sh "$Z/run.sh" 2>/dev/null; chmod 755 "$Z/run.sh" 2>/dev/null; }
if [ -x "$Z/rootfs/bin/busybox" ] && [ ! -x "$Z/rootfs/usr/bin/java" ] && [ -x "$Z/run.sh" ]; then
  say "rootfs 内装 openjdk17（gradle 必需）"
  printf 'nameserver 223.5.5.5\nnameserver 119.29.29.29\n' > "$Z/rootfs/etc/resolv.conf" 2>/dev/null
  printf '%s\n%s\n' "$M/alpine/v3.20/main" "$M/alpine/v3.20/community" > "$Z/rootfs/etc/apk/repositories" 2>/dev/null
  sh "$Z/run.sh" /bin/sh -c 'apk update >/dev/null 2>&1; apk add --no-cache openjdk17-jre-headless ca-certificates >/dev/null 2>&1' \
    && say "ok jre17" || say "warn: apk add 失败（可手动 sh 环境/run.sh /bin/sh -c 'apk add openjdk17-jre-headless'）"
fi

if [ -z "$MISS" ]; then say "环境就绪（无需装配）"; exit 0; fi
say "缺:$MISS"
[ "$CHECK" = 1 ] && exit 1

# ── 2a. 自动下载（走 netrun：unshare + resolv.conf + CA，宿主自足，不依赖 App 层）──
HT=/data/user/0/com.hoerire.tools/files/hosttools
dl() {  # $1=url $2=目标文件
  [ -s "$2" ] && { say "have $(basename "$2")"; return 0; }
  if [ ! -x "$HT/bin/netrun" ]; then
    say "缺少 netrun（联网能力）→ 先跑: sh \"$HT/../hosttools-install.sh\" 或重开 App 触发铺开"
    say "  兜底：用 py_download_file 手动下 $1 到 $DL/"
    return 1
  fi
  say "下载(netrun) $(basename "$2")"
  "$HT/bin/netrun" "$HT/bin/curl" -sSL --retry 2 --connect-timeout 20 --max-time 1800 \
    -o "$2.part" "$1" && mv -f "$2.part" "$2"
}
[ -x "$Z/rootfs/bin/busybox" ] || dl "$M/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz" "$DL/alpine-minirootfs-3.20.3-aarch64.tar.gz"
ls -1d "$Z"/gradle-[0-9]*/bin/gradle >/dev/null 2>&1 || dl "$M/gradle/gradle-9.3.1-bin.zip" "$DL/gradle-9.3.1-bin.zip"
[ -f "$SDK/platforms/android-37.0/android.jar" ] || dl "$M/AndroidSDK/platform-37.0_r01.zip" "$DL/platform-37.0_r01.zip"
for v in 35 36; do [ -f "$SDK/platforms/android-$v/android.jar" ] || dl "$M/AndroidSDK/platform-${v}_r01.zip" "$DL/platform-${v}_r01.zip"; done
[ -x "$SDK/build-tools/36.0.0/aapt2" ] || dl "$M/AndroidSDK/build-tools_r36_linux.zip" "$DL/build-tools_r36_linux.zip"
[ -d "$SDK/cmdline-tools/latest/bin" ] || dl "$M/AndroidSDK/commandlinetools-linux-11076708_latest.zip" "$DL/commandlinetools-linux-11076708_latest.zip"

# ── 2b. 用 临时/dl 里已有的包装配 ──
unp() {  # $1=zip/tgz $2=目标
  case "$1" in
    *.zip) unzip -qo "$1" -d "$2" 2>/dev/null ;;
    *.tar.gz|*.tgz) mkdir -p "$2"; tar -xzf "$1" -C "$2" 2>/dev/null ;;
  esac
}
X=$A/临时/bs-x; rm -rf "$X"; mkdir -p "$X"

if ! [ -x "$Z/rootfs/bin/busybox" ]; then
  f=$(ls "$DL"/alpine-minirootfs-*.tar.gz 2>/dev/null | head -1)
  if [ -n "$f" ]; then mkdir -p "$Z/rootfs"; tar -xzf "$f" -C "$Z/rootfs" && say "装配 rootfs"; fi
fi
if ! ls -1d "$Z"/gradle-[0-9]*/bin/gradle >/dev/null 2>&1; then
  f=$(ls "$DL"/gradle-*-bin.zip 2>/dev/null | head -1)
  if [ -n "$f" ]; then
    unp "$f" "$X/g"
    g=$(ls -1d "$X"/g/gradle-*/ 2>/dev/null | head -1)
    [ -n "$g" ] && mv "$g" "$Z/" && chmod 755 "$Z"/gradle-*/bin/gradle && say "装配 gradle $(basename "$g")"
  fi
fi
if [ ! -f "$SDK/platforms/android-37.0/android.jar" ]; then
  f=$(ls "$DL"/platform-37.0_r*.zip 2>/dev/null | head -1)
  if [ -n "$f" ]; then
    unp "$f" "$X/p"
    src=$(ls -1d "$X"/p/android-37.0* "$X"/p/android-37 2>/dev/null | head -1)
    [ -n "$src" ] && { mkdir -p "$SDK/platforms"; rm -rf "$SDK/platforms/android-37.0"; mv "$src" "$SDK/platforms/android-37.0"; say "装配 platform android-37.0"; }
  fi
  for v in 35 36; do
    [ -f "$SDK/platforms/android-$v/android.jar" ] && continue
    f=$(ls "$DL"/platform-${v}_r*.zip 2>/dev/null | head -1)
    [ -n "$f" ] || continue
    unp "$f" "$SDK/platforms"
    src=$(ls -1d "$SDK"/platforms/android-$v "$SDK"/platforms/android-1[0-9] 2>/dev/null | head -1)
    [ -n "$src" ] && { [ "$(basename "$src")" != "android-$v" ] && mv "$src" "$SDK/platforms/android-$v"; say "装配 platform android-$v"; }
  done
fi
if [ ! -x "$SDK/build-tools/36.0.0/aapt2" ] && [ ! -f "$SDK/build-tools/36.0.0/package.xml" ]; then
  f=$(ls "$DL"/build-tools_r36*.zip 2>/dev/null | head -1)
  if [ -n "$f" ]; then
    unp "$f" "$X/b"
    src=$(ls -1d "$X"/b/android-1[0-9] "$X"/b/36.0.0 2>/dev/null | head -1)
    [ -n "$src" ] && { mkdir -p "$SDK/build-tools"; rm -rf "$SDK/build-tools/36.0.0"; mv "$src" "$SDK/build-tools/36.0.0"; say "装配 build-tools 36.0.0"; }
  fi
fi
if [ ! -d "$SDK/cmdline-tools/latest/bin" ]; then
  f=$(ls "$DL"/commandlinetools-linux-*.zip 2>/dev/null | head -1)
  if [ -n "$f" ]; then
    unp "$f" "$X/c"
    src=$(ls -1d "$X"/c/cmdline-tools 2>/dev/null | head -1)
    [ -n "$src" ] && { mkdir -p "$SDK/cmdline-tools"; rm -rf "$SDK/cmdline-tools/latest"; mv "$src" "$SDK/cmdline-tools/latest"; say "装配 cmdline-tools"; }
  fi
fi
rm -rf "$X"

# ── 3. 还缺什么，打印下载清单（交给 AI 用 py_download_file）──
STILL=""
[ -x "$Z/rootfs/bin/busybox" ] || STILL="$STILL\n  $M/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"
ls -1d "$Z"/gradle-[0-9]*/bin/gradle >/dev/null 2>&1 || STILL="$STILL\n  $M/gradle/gradle-9.3.1-bin.zip"
[ -f "$SDK/platforms/android-37.0/android.jar" ] || STILL="$STILL\n  $M/AndroidSDK/platform-37.0_r01.zip"
[ -x "$SDK/build-tools/36.0.0/aapt2" ] || STILL="$STILL\n  $M/AndroidSDK/build-tools_r36_linux.zip"
[ -d "$SDK/cmdline-tools/latest/bin" ] || STILL="$STILL\n  $M/AndroidSDK/commandlinetools-linux-11076708_latest.zip"

if [ -n "$STILL" ]; then
  say "仍需下列包 → 下到 $DL/ 后重跑本脚本：$STILL"
  say "下载方式（宿主没有 DNS，必须走 App 层）：py_download_file(url=..., path=$DL)"
  exit 1
fi
say "装配完成（下载全程走 netrun，未依赖 App 层工具）；建议再跑一次 env-doctor 确认"
exit 0
