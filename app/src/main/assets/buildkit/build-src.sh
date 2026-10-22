#!/system/bin/sh
# 出包入口（通用版，不绑定具体源码树）
# 用法: sh build-src.sh [--src=<工程目录>] [gradle 参数...]
# 环境变量: ZAFIRO_ENV ZAFIRO_SRC ZAFIRO_OUT ZAFIRO_VARIANT(release|debug)
#           ZAFIRO_APK_NAME(产物文件名) ZAFIRO_NO_COPY=1(不落产物) ZAFIRO_KS
set -u
A=${ZAFIRO_BASE:-/data/local/aitools}
Z=${ZAFIRO_ENV:-$A/env}
OUT=${ZAFIRO_OUT:-$A/out}
V=${ZAFIRO_VARIANT:-release}
SRC=${ZAFIRO_SRC:-}
ARGS=""
for a in "$@"; do
  case "$a" in
    --src=*) SRC=${a#--src=} ;;
    *) ARGS="$ARGS $a" ;;
  esac
done
if [ -z "$SRC" ]; then
  N=0
  CAND=""
  for d in "$A"/project/*/; do
    d=${d%/}
    if [ -f "$d/gradlew" ]; then N=$((N+1)); CAND="$CAND $d"; fi
  done
  if [ "$N" = "0" ]; then
    echo "SRC missing: project/ 下没有含 gradlew 的子目录（每个任务应建 project/<task>/）"
    exit 1
  fi
  if [ "$N" -gt 1 ]; then
    echo "project/ 下有 $N 个 gradle 工程，请用 --src= 明确指定其一："
    for c in $CAND; do echo "  $c"; done
    exit 1
  fi
  SRC=${CAND# }
fi
{ [ -n "${SRC:-}" ] && [ -f "$SRC/gradlew" ]; } || { echo "SRC missing: ${SRC:-none}"; exit 1; }

# 工程目录命名铁律（2026-10-21）：工程/<名> 必须与 src/<名> 一一对应。
# 目的：多家源码并存时绝不共用同一 build 树（否则 build/ 缓存、产物、md5 对齐全部串味）。
SB=$(basename "$SRC")
# 容器内路径：run.sh 把 $A 绑到容器 /data/local/aitools，故宿主 $A 前缀换成容器根
SRC_G=$(printf '%s' "$SRC" | sed "s#^$A#/data/local/aitools#")
# 容器内一律用映射后的路径（run.sh 把宿主 $A 绑到容器 /data/local/aitools）
ZG=$(printf '%s' "$Z" | sed "s#^$A#/data/local/aitools#")

case "$SRC" in
  "$A"/project/*) ;;
  *) echo "SRC 必须在 $A/project/ 下: $SRC"; exit 2 ;;
esac
if [ -d "$A/src" ] && [ ! -d "$A/src/$SB" ]; then
  echo "project目录名与源码不匹配: project/$SB 需要 src/$SB 存在"
  echo "改法: sh $A/project/sync.sh <源码目录名> 然后 --src=$A/project/<源码目录名>"
  echo "现有源码:"
  ls -1 "$A/src" 2>/dev/null | sed 's/^/  /'
  exit 2
fi
[ -x "$Z/run.sh" ] || { echo "env missing: $Z/run.sh"; exit 1; }
mkdir -p "$Z/gradle-home"
if [ -z "$(ls -A "$Z/gradle-home" 2>/dev/null)" ]; then
  echo "WARN: $Z/gradle-home 为空 —— 首次构建需联网拉 AGP/Kotlin 依赖(约 1.4GB)"
fi
# gradle lib 目录动态解析：升级 gradle 后不再找不到 -cp
# 按版本号取最高，避免多版本并存时套用到旧 gradle 的 lib
GDLIB=$(ls -1d "$Z"/gradle-[0-9]*/lib 2>/dev/null | sort -V | tail -1)
if [ -z "$GDLIB" ]; then
  # 构建即自愈：不依赖谁来"记得部署"，缺 gradle 就在这里装配
  echo "[preflight] 环境缺 gradle 发行版，尝试装配：sh $A/bootstrap-env.sh"
  sh $A/bootstrap-env.sh || {
    echo "[preflight] 装配没完成：按上面清单用 py_download_file 下到 $A/tmp/dl/ 后重跑构建"
    exit 1
  }
  GDLIB=$(ls -1d "$Z"/gradle-[0-9]*/lib 2>/dev/null | sort -V | tail -1)
fi
[ -n "$GDLIB" ] || { echo "env missing: $Z/gradle-<ver>/lib"; exit 1; }
KS=${ZAFIRO_KS:-$Z/debug.keystore}
# 容器内路径映射（run.sh 把宿主 $A 绑到容器 /data/local/aitools）
GDLIB_G=$(printf '%s' "$GDLIB" | sed "s#^$Z#/data/local/aitools/env#")
KS_G=$(printf '%s' "$KS" | sed "s#^$A#/data/local/aitools#")
if [ "$V" = "release" ]; then
  TASK=":app:assembleRelease"
  SIGN="-PRELEASE_STORE_FILE=$KS -PRELEASE_STORE_PASSWORD=android -PRELEASE_KEY_ALIAS=androiddebugkey -PRELEASE_KEY_PASSWORD=android"
else
  TASK=":app:assembleDebug"
  SIGN=""
fi
# 构建期 init-script 注入：
#  no-lint.init.gradle -> 关 lintVital（lint-gradle 走 detachedConfiguration，不在依赖预热范围，离线必挂）
#  sign.init.gradle    -> 工程没写 signingConfigs 时用 -PRELEASE_STORE_* 注入，避免产出未签名 APK
INITS=""
for i in no-lint sign; do
  F="$Z/buildkit/$i.init.gradle"
  [ -f "$F" ] && INITS="$INITS --init-script $F"
done
INITS_G=""
for i in $INITS; do
  case "$i" in
    --init-script) INITS_G="$INITS_G $i" ;;
    *) INITS_G="$INITS_G $(printf '%s' "$i" | sed "s#^$A#/data/local/aitools#")" ;;
  esac
done
echo "SRC=$SRC"
echo "VARIANT=$V ARGS=$ARGS INITS=${INITS:-none}"
"$Z/run.sh" /bin/sh -c "cd $SRC_G && export ANDROID_HOME=$Z/android-sdk ANDROID_SDK_ROOT=$Z/android-sdk GRADLE_USER_HOME=$Z/gradle-home JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH && exec /usr/lib/jvm/java-17-openjdk/bin/java -Xmx1800m -XX:+UseParallelGC -cp \"$GDLIB_G/*\" org.gradle.launcher.GradleMain --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/usr/local/bin/aapt2 -PzafiroKs=$KS_G $SIGN $INITS_G $TASK $ARGS"
RC=$?
echo "RC=$RC"
if [ "$RC" != "0" ]; then
  # 失败即自动体检：不依赖 AI 记得跑 env-doctor（确定性 hook）
  echo "======== 构建失败，自动环境体检（env-doctor）========"
  [ -f "$A/env-doctor.sh" ] && sh "$A/env-doctor.sh" 2>&1 | tail -n 40
  echo "======== 体检结束：缺什么按上面 fix 行补；宿主工具在 files/hosttools（TOOLS.txt）========"
  exit "$RC"
fi
[ "${ZAFIRO_NO_COPY:-0}" = "1" ] && { echo "copy skipped (ZAFIRO_NO_COPY=1)"; exit 0; }

META=""
for m in "$SRC"/app/build/outputs/apk/"$V"/output-metadata.json "$SRC"/*/build/outputs/apk/"$V"/output-metadata.json; do
  [ -f "$m" ] && META=$m && break
done
# 构建返回 0 但没有 metadata 说明产物缺失，必须报错（原来 exit 0 会伪装成成功）
[ -n "$META" ] || { echo "ERROR: 构建返回 0 但找不到 output-metadata.json（产物未生成）"; exit 1; }
DIR=$(dirname "$META")
APPID=$(sed -n 's/.*"applicationId": "\([^"]*\)".*/\1/p' "$META" | head -1)
VC=$(sed -n 's/.*"versionCode": \([0-9]*\).*/\1/p' "$META" | head -1)
VN=$(sed -n 's/.*"versionName": "\([^"]*\)".*/\1/p' "$META" | head -1)
OF=$(sed -n 's/.*"outputFile": "\([^"]*\)".*/\1/p' "$META" | head -1)
APK="$DIR/$OF"
[ -f "$APK" ] || { echo "ERROR: metadata 指向的 apk 不存在: $APK"; exit 1; }
ROOT=$(sed -n 's/.*rootProject.name *= *"\([^"]*\)".*/\1/p' "$SRC"/settings.gradle* 2>/dev/null | head -1)
[ -n "$ROOT" ] || ROOT=$(basename "$SRC")
NAME=${ZAFIRO_APK_NAME:-$ROOT-$VN-$VC.apk}
mkdir -p "$OUT"
cp -p "$APK" "$OUT/$NAME"
echo "path: $OUT/$NAME"
echo "size: $(stat -c %s "$OUT/$NAME") bytes"
echo "md5:  $(md5sum "$OUT/$NAME" | cut -d' ' -f1)"
echo "built: $(stat -c %y "$OUT/$NAME")"
echo "appId=$APPID versionCode=$VC versionName=$VN"
