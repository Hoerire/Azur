#!/system/bin/sh
# zafiro-env —— 本机全量构建环境唯一入口（App 构建 + native 编译工具链）
#   sh env.sh install <tgz>  解压扩展包到 aitools 根目录并自检
#   sh env.sh setup          安装/补齐工具链（幂等）
#   sh env.sh check          打印全部工具版本
#   sh env.sh shell          进容器交互 shell
#   sh env.sh apk <args>     容器内 apk
#   sh env.sh build [args]   构建 App（透传 build-src.sh 参数）
#   sh env.sh snapshot [out] 打 rootfs 快照
#   sh env.sh restore <tgz>  还原 rootfs 快照
A=${ZAFIRO_BASE:-/data/local/aitools}
Z=${ZAFIRO_ENV:-$A/env}
R=$Z/rootfs
SRC=${ZAFIRO_SRC:-}
if [ -z "$SRC" ]; then
  for d in "$A"/project/*/; do
    if [ -f "$d/gradlew" ]; then SRC=${d%/}; break; fi
  done
fi
OUT=${ZAFIRO_OUT:-$A/out}
LOG=$A/logs
HOST_MIRROR=${ZAFIRO_MIRROR:-https://mirrors.cloud.tencent.com/alpine}
APK_VER=$(cat $R/etc/alpine-release 2>/dev/null || echo 3.20.3)
APK_REL=v${APK_VER%.*}
PKGS="build-base clang lld llvm cmake ninja pkgconf linux-headers git curl zip unzip nodejs npm go rust cargo perl ruby php83 lua5.4"
mkdir -p "$LOG"
enter() { ZAFIRO_A="$A" ZAFIRO_Z="$Z" ZAFIRO_ROOTFS="$R" sh "$Z/run.sh" "$@"; }
has_mount() { grep -qF " $1 " /proc/mounts; }
unmount_all() {
  for p in $(awk '{print $2}' /proc/mounts | grep -F "$R/" | sort -r); do umount -l "$p" 2>/dev/null; done
}

install_pack() {
  [ -f "$1" ] || { echo "missing: $1"; exit 1; }
  unmount_all
  tar xzf "$1" -C "$A" || { echo "extract failed"; exit 1; }
  echo "extracted to $A"
  chmod 755 "$Z/run.sh" "$Z/env.sh" 2>/dev/null
  enter /bin/sh -c 'echo container-ok' || exit 1
  sh "$Z/env.sh" check
}

setup() {
  printf '%s\n' "$HOST_MIRROR/$APK_REL/main" "$HOST_MIRROR/$APK_REL/community" > "$R/etc/apk/repositories"
  enter /sbin/apk update
  enter /sbin/apk add --no-cache $PKGS
}

check() {
  enter /bin/sh -c '
    for b in gcc g++ clang clang++ ld ld.lld make cmake ninja git curl zip unzip node npm perl ruby php83 lua5.4 rustc cargo go; do
      command -v $b >/dev/null 2>&1 && printf "%-10s ok\n" "$b" || printf "%-10s MISSING\n" "$b"
    done
    echo "---"
    gcc --version | head -1; clang --version | head -1
    cmake --version | head -1; git --version
    node --version; go version; rustc --version; lua5.4 -v
    /usr/lib/jvm/java-17-openjdk/bin/java -version 2>&1 | head -1
  '
}

snapshot() {
  O=${1:-$OUT/zafiro-env-rootfs-$(date +%m%d-%H%M).tar.gz}
  unmount_all
  tar czf "$O" -C "$R" .
  rc=$?
  enter /bin/true >/dev/null 2>&1
  [ $rc -eq 0 ] || { echo "snapshot FAILED rc=$rc"; return $rc; }
  echo "snapshot: $O"; echo "size: $(wc -c < "$O")"; echo "md5: $(md5sum "$O" | cut -d" " -f1)"
}

restore() {
  [ -f "$1" ] || { echo "missing: $1"; exit 1; }
  unmount_all
  rm -rf "$R"; mkdir -p "$R"
  tar xzf "$1" -C "$R"
  enter /bin/true >/dev/null 2>&1
  echo "restored: $R"
}

case "$1" in
  install)  shift; install_pack "$@" ;;
  setup)    shift; setup ;;
  check)    shift; check ;;
  shell)    shift; enter /bin/sh ;;
  apk)      shift; enter /sbin/apk "$@" ;;
  build)    shift; ZAFIRO_SRC="$SRC" exec sh $A/build-src.sh "$@" ;;
  snapshot) shift; snapshot "$@" ;;
  restore)  shift; restore "$@" ;;
  *) echo "usage: sh env.sh {install <tgz>|setup|check|shell|apk|build|snapshot|restore}"; exit 2 ;;
esac
