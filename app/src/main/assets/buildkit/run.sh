#!/system/bin/sh
# 进容器唯一入口：挂载去重 + 只绑必要子目录（绝不绑 env/ 本身，否则含 rootfs 会递归嵌套）
R=${ZAFIRO_ROOTFS:-${ZAFIRO_A:-/data/local/aitools}/env/rootfs}
Z=${ZAFIRO_Z:-${ZAFIRO_A:-/data/local/aitools}/env}
A=${ZAFIRO_A:-/data/local/aitools}
has_mount() { grep -qF " $1 " /proc/mounts; }
for d in proc dev sys; do mkdir -p $R/$d; has_mount $R/$d || mount -o bind /$d $R/$d; done
for d in system apex linkerconfig vendor; do mkdir -p $R/$d; has_mount $R/$d || mount -o rbind /$d $R/$d; done
mkdir -p $R/data/media/0/Download; has_mount $R/data/media/0/Download || mount -o rbind /data/media/0/Download $R/data/media/0/Download
mkdir -p $R/data/local/aitools
for d in src project out logs tmp; do
  mkdir -p $R/data/local/aitools/$d
  has_mount $R/data/local/aitools/$d || mount -o rbind $A/$d $R/data/local/aitools/$d
done
for f in build-src.sh zafiro-handoff.md; do
  [ -f $A/$f ] && cp -f $A/$f $R/data/local/aitools/$f 2>/dev/null
done
# 环境内的构建资产逐个绑定（源不含 rootfs，不会递归）
mkdir -p $R/data/local/aitools/env
# 顺序铁律：先解析 gradle 发行版，后创建 gradle-home。
# gradle-home 也匹配 gradle-*，若先创建，sort -V 会把它当"最高版本"选中 → 发行版永不 bind，
# 容器内 $Z/gradle-<ver>/lib 不存在 → build-src.sh 的 java -cp 空 → GradleMain ClassNotFound。
# 通配限定数字开头，永不含 gradle-home。
GD=$(ls -1d "$Z"/gradle-[0-9]*/ 2>/dev/null | sort -V | tail -1)
GD=${GD%/}; GD=${GD##*/}
if [ -z "$GD" ] || [ ! -x "$Z/$GD/bin/gradle" ]; then
  echo "WARN: 未找到 gradle 发行版 ($Z/gradle-<ver>/bin/gradle)，App 构建会失败" >&2
fi
# gradle-home 必须先存在：不存在时下面的 [ -e ] 会 continue 跳过 bind，
# 依赖缓存会写进 rootfs 内部（实测丢 1.4G，且 rootfs 一重建就重下）。
mkdir -p $Z/gradle-home
for d in android-sdk gradle-home $GD toolchain tools pbuild work buildkit; do
  [ -e $Z/$d ] || continue
  mkdir -p $R/data/local/aitools/env/$d
  has_mount $R/data/local/aitools/env/$d || mount -o rbind $Z/$d $R/data/local/aitools/env/$d
done
[ -f $Z/debug.keystore ] && cp -f $Z/debug.keystore $R/data/local/aitools/env/debug.keystore 2>/dev/null
exec chroot $R /usr/bin/env -i PATH=/usr/lib/jvm/java-17-openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin HOME=/root JAVA_HOME=/usr/lib/jvm/java-17-openjdk "$@"
