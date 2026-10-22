#!/system/bin/sh
# env-doctor.sh —— 环境体检（只读，不修）。部署期门禁：所有工具必须实测可用。
# 用法: sh env-doctor.sh        返回 0 = 全绿；非 0 = 有 FAIL
A=${A:-/data/local/aitools}; Z=$A/env; R=$Z/rootfs; SDK=$Z/android-sdk
NDKV=${NDKV:-27.2.12479018}; BT=36.0.0
P=0; F=0
ok()   { P=$((P+1)); printf "PASS  %s\n" "$1"; }
no()   { F=$((F+1)); printf "FAIL  %s\n      fix: %s\n" "$1" "$2"; }
info() { printf "INFO  %s\n" "$1"; }
sz()   { stat -c %s "$1" 2>/dev/null || echo 0; }

for x in src project out logs tmp; do [ -d "$A/$x" ] || no "目录缺 $A/$x" "mkdir -p $A/$x"; done
[ -d "$A/src" ] && [ -d "$A/tmp" ] && ok "目录结构(src/project/out/logs/tmp)"

if [ -x "$R/bin/busybox" ] && [ -h "$R/bin/sh" ]; then ok "rootfs"; else no "rootfs 不可用" "解 alpine-minirootfs 到 $R"; fi

for v in android-35 android-36 android-37.0; do
  J=$SDK/platforms/$v/android.jar
  if [ -f "$J" ] && [ "$(sz "$J")" -gt 1000000 ]; then ok "platforms/$v"; else no "platforms/$v 缺失或占位($(sz "$J")B)" "重下 $v"; fi
done
[ -f "$SDK/build-tools/$BT/package.xml" ] && ok "build-tools $BT" || no "build-tools $BT 缺" "重下 build-tools_r36"
[ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ] && ok "cmdline-tools" || no "cmdline-tools 缺" "重下 commandlinetools"

HT=$A/hosttools
if [ -x "$HT/env.sh" ]; then
  ( . "$HT/env.sh"
    for t in busybox jq rg fd fzf sqlite3 xz zstd lz4 file tree lsof strace 7zz zip rsync curl; do
      if v=$("$HT/bin/$t" --version 2>&1 | head -1) && [ -n "$v" ]; then :; else no "宿主工具 $t 不可用" "重跑 bootstrap-env.sh"; fi
    done
    ok "宿主工具集 16 个实测通过（bins=$(ls "$HT/bin" | wc -l), libs=$(ls "$HT/lib" | wc -l), applets=$(ls "$HT/applets" 2>/dev/null | wc -l)）"
  )
else no "hosttools 未部署" "跑 bootstrap-env.sh"; fi

NDKB=$SDK/ndk/$NDKV/toolchains/llvm/prebuilt/linux-x86_64/bin; bad=""
for t in clang clang++ llvm-ar llvm-ranlib ld.lld; do
  p=$NDKB/$t
  if [ ! -e "$p" ]; then bad="$bad $t(缺)"
  elif [ "$(head -c4 "$p" 2>/dev/null)" = "$(printf '\177ELF')" ]; then bad="$bad $t(x86-64)"
  elif ! head -1 "$p" 2>/dev/null | grep -q '^#!'; then bad="$bad $t(占位)"
  elif ! sh -n "$p" 2>/dev/null; then bad="$bad $t(语法错)"; fi
done
[ -z "$bad" ] && ok "NDK bin wrappers 内容合法" || no "NDK bin:$bad" "sh aitools-fix.sh（不带 --check）"

GR=$(ls -1d "$Z"/gradle-[0-9]*/ 2>/dev/null | sort -V | tail -1); GR=${GR%/}
if [ -n "$GR" ] && [ -x "$GR/bin/gradle" ] && ls "$GR"/lib/gradle-gradle-cli-main-*.jar >/dev/null 2>&1; then ok "gradle $(basename "$GR") 发行版完整"
else no "gradle 发行版缺失/残缺" "解 gradle-9.3.1-bin.zip 到 $Z/gradle-9.3.1"; fi

if [ -x "$Z/run.sh" ]; then
  S=$A/tmp/doctor-smoke.sh; mkdir -p "$A/tmp"
  cat > "$S" <<'EOS'
NN=/data/local/aitools/env/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin
echo "S_JAVA=$(java -version 2>&1 | head -1 | cut -d'"' -f2)"
echo "S_PY=$(python3 -V 2>&1 | cut -d' ' -f2)"
echo "S_GIT=$(git --version 2>&1 | cut -d' ' -f3)"
echo "S_AAPT2=$(aapt2 version 2>&1 | head -1 | awk '{print $NF}')"
echo "S_ZL=$(zipalign 2>&1 | head -1 | cut -c1-16)"
echo "S_AS=$(apksigner --version 2>&1 | head -1)"
printf 'int f(void){return 1;}\n' > /tmp/s.c
if $NN/aarch64-linux-android21-clang -c /tmp/s.c -o /tmp/s.o 2>/dev/null; then
  echo "S_CLANG=$(file /tmp/s.o | grep -c 'ARM aarch64')"
else echo "S_CLANG=FAIL"; fi
GL=$(ls -1d /data/local/aitools/env/gradle-[0-9]*/lib 2>/dev/null | sort -V | tail -1)
if [ -n "$GL" ] && java -cp "$GL/*" org.gradle.launcher.GradleMain --version > /tmp/g.txt 2>&1; then
  echo "S_GRADLE=$(grep -m1 '^Gradle' /tmp/g.txt | cut -d' ' -f2)"
else echo "S_GRADLE=FAIL"; fi
EOS
  OUT=$(sh "$Z/run.sh" /bin/sh /data/local/aitools/tmp/doctor-smoke.sh 2>&1)
  sm() { v=$(printf '%s\n' "$OUT" | sed -n "s/^S_$1=//p" | head -1)
         case "$v" in ""|FAIL*|0) no "实测 $2" "容器内跑不起来（$3）" ;; *) ok "实测 $2 ($v)" ;; esac; }
  sm JAVA "java"
  sm PY "python3"
  sm GIT "git"
  sm AAPT2 "aapt2"
  sm ZL "zipalign"
  sm AS "apksigner"
  sm CLANG "NDK clang 编 aarch64 .o"
  sm GRADLE "gradle 启动(GradleMain)"
else no "实测门禁跑不了" "缺 $Z/run.sh"; fi

M2=$(du -sm "$Z/gradle-home/caches" 2>/dev/null | cut -f1); M2=${M2:-0}
[ "$M2" -gt 200 ] && ok "gradle-home 缓存 ${M2}MB" || info "gradle-home 缓存 ${M2}MB（部署不预热，构建时按需联网）"

MEM=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo 2>/dev/null); MEM=${MEM:-0}
[ "$MEM" -gt 600 ] && ok "可用内存 ${MEM}MB" || no "可用内存仅 ${MEM}MB" "gradle --stop"
DFK=$(df /data 2>/dev/null | awk 'NR==2{print $4}'); DFK=${DFK:-0}
[ "$DFK" -gt 3000000 ] && ok "磁盘可用 $((DFK/1024))MB" || no "磁盘仅 $((DFK/1024))MB" "清 tmp/ 与旧包"

echo "----  PASS=$P FAIL=$F"
[ "$F" -gt 0 ] && echo "----  修法: sh /data/local/aitools/bootstrap-env.sh（缺件会打印下载清单，下载走 py_download_file）"
[ "$F" = 0 ]
