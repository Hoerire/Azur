#!/system/bin/sh
# hosttools-install.sh —— 把 assets/hosttools 铺成宿主可直接调用的工具集
# 用法: sh hosttools-install.sh [KIT目录] [目标目录]
#   静态二进制      -> $HT/bin/            （直接 exec）
#   动态 musl 二进制 -> $HT/libexec/ + bin 里生成 wrapper（用自带 loader 启动）
#   so / magic.mgc  -> $HT/lib、$HT/share
umask 022   # 关键：默认 077 会把 hosttools/lib 建成 700，非 root(app 域)访问不到 musl loader
KIT=${1:-/data/local/aitools/临时/buildkit}
HT=${2:-/data/local/aitools/hosttools}
SRC=$KIT/hosttools
LD=$HT/lib/ld-musl-aarch64.so.1
[ -d "$SRC" ] || { echo "FAIL: $SRC 不存在" >&2; exit 1; }
rm -rf "$HT"; mkdir -p "$HT/bin" "$HT/libexec" "$HT/lib" "$HT/share" "$HT/applets"

ns=0; nd=0
# 1) 静态工具（含 assets 旧版布局：busybox/jq/rg/fd/fzf 直接在 hosttools 根）
for f in "$SRC"/static/* "$SRC"/busybox "$SRC"/jq "$SRC"/rg "$SRC"/fd "$SRC"/fzf; do
  [ -f "$f" ] || continue
  cp -f "$f" "$HT/bin/$(basename "$f")" && chmod 755 "$HT/bin/$(basename "$f")" && ns=$((ns+1))
done
# 2) 动态 musl 工具 + wrapper
for f in "$SRC"/musl/*; do
  [ -f "$f" ] || continue
  b=$(basename "$f")
  cp -f "$f" "$HT/libexec/$b" && chmod 755 "$HT/libexec/$b" || continue
  if [ "$b" = file ]; then
    printf '#!/system/bin/sh\nexec %s --library-path %s %s -m %s/share/misc/magic.mgc "$@"\n' \
      "$LD" "$HT/lib" "$HT/libexec/$b" "$HT" > "$HT/bin/$b"
  else
    printf '#!/system/bin/sh\nexec %s --library-path %s %s "$@"\n' \
      "$LD" "$HT/lib" "$HT/libexec/$b" > "$HT/bin/$b"
  fi
  chmod 755 "$HT/bin/$b"; nd=$((nd+1))
done
# 3) 库与数据
cp -a "$SRC"/lib/. "$HT/lib/" 2>/dev/null
cp -a "$SRC"/share/. "$HT/share/" 2>/dev/null
chmod 755 "$LD" 2>/dev/null
# 3.4) 预置 libexec：assets/libexec 里放的是"非 musl/ 来源"的真身（如 python3.12）
for f in "$SRC"/libexec/*; do
  [ -f "$f" ] || continue
  b=$(basename "$f")
  cp -f "$f" "$HT/libexec/$b" && chmod 755 "$HT/libexec/$b"
done

# 3.45) soname 软链：libX.so.A.B -> libX.so.A（musl loader 按 soname 找库，缺链就 ImportError）
for f in "$HT"/lib/*.so.*; do
  [ -f "$f" ] || continue
  b=$(basename "$f")
  base=${b%%.so.*}; rest=${b#*.so.}; major=${rest%%.*}
  link="$HT/lib/$base.so.$major"
  if [ "$link" != "$f" ] && [ ! -e "$link" ]; then ln -s "$b" "$link" 2>/dev/null && echo "[hosttools] soname $base.so.$major"; fi
done

# 3.5) Python 3（musl 动态 + 自带 stdlib 33MB）：libexec 里有真身就生成 wrapper
if [ -f "$HT/libexec/python3.12" ]; then
  for n in python3 python3.12 python; do
    printf '#!/system/bin/sh\nHT=%s\nexport PYTHONHOME=$HT\nexport PYTHONDONTWRITEBYTECODE=1\nexec $HT/lib/ld-musl-aarch64.so.1 --library-path $HT/lib $HT/libexec/python3.12 "$@"\n' "$HT" > "$HT/bin/$n"
    chmod 755 "$HT/bin/$n"
  done
  PYV=$("$HT/bin/python3" -V 2>&1 | head -1)
  echo "[hosttools] python: $PYV"
fi

# 3.6) pip（site-packages 已在 lib/python3.12 内，无需单独解包）
if [ -d "$HT/lib/python3.12/site-packages/pip" ]; then
  for n in pip pip3; do
    printf '#!/system/bin/sh\nHT=%s\nexport PYTHONHOME=$HT\nexport PYTHONDONTWRITEBYTECODE=1\nexec $HT/lib/ld-musl-aarch64.so.1 --library-path $HT/lib $HT/libexec/python3.12 -m pip "$@"\n' "$HT" > "$HT/bin/$n"
    chmod 755 "$HT/bin/$n"
  done
  echo "[hosttools] pip: $("$HT/bin/pip" --version 2>&1 | head -1)"
fi

# 3.7) netrun：让宿主程序能联网（unshare -m + tmpfs 盖 /etc + CA bundle，只影响本进程树）
#      assets/hosttools/bin/netrun 自带 HT 推算（dirname $0/..），直接铺即可
if [ -f "$SRC/bin/netrun" ] && command -v unshare >/dev/null 2>&1; then
  cp -f "$SRC/bin/netrun" "$HT/bin/netrun" && chmod 755 "$HT/bin/netrun"
  NET=$("$HT/bin/netrun" "$HT/bin/curl" -sS -o /dev/null -w 'http=%{http_code}' --max-time 12 https://mirrors.aliyun.com/ 2>&1 | tail -1)
  echo "[hosttools] netrun 就绪（$NET）"
fi

# 3.8) git 专用：GIT_EXEC_PATH / templates / CA（否则 clone 报 remote-https is not a git command）
if [ -f "$HT/libexec/git" ]; then
  mkdir -p "$HT/libexec/git-core"
  cp -f "$SRC/libexec/git-core"/* "$HT/libexec/git-core/" 2>/dev/null
  chmod 755 "$HT/libexec/git-core"/* 2>/dev/null
  for n in git-remote-https git-remote-http git-upload-pack git-receive-pack \
           git-upload-archive git-shell git-credential-store git-credential-cache; do
    ln -sf ../git "$HT/libexec/git-core/$n" 2>/dev/null
  done
  printf '#!/system/bin/sh\nHT=%s\nexport GIT_EXEC_PATH=$HT/libexec/git-core\nexport GIT_TEMPLATE_DIR=$HT/share/git-core/templates\nexport GIT_SSL_CAINFO=$HT/share/ca-bundle.crt\nexec $HT/lib/ld-musl-aarch64.so.1 --library-path $HT/lib $HT/libexec/git "$@"\n' "$HT" > "$HT/bin/git"
  chmod 755 "$HT/bin/git"
  echo "[hosttools] git: $("$HT/bin/git" --version 2>&1 | head -1)"
fi

# 4) busybox applet 软链（305 个）
# applet 软链：app 域**不能 exec** 自己拥有的 ELF（SELinux app_data_file），
# 所以不能跑 `busybox --install`；改为按 assets 里的 applet 清单直接 ln -s。
if [ -f "$SRC/applet-list.txt" ] && [ -f "$HT/bin/busybox" ]; then
  rm -f "$HT"/applets/* 2>/dev/null
  while IFS= read -r a; do
    [ -n "$a" ] && ln -sf ../bin/busybox "$HT/applets/$a" 2>/dev/null
  done < "$SRC/applet-list.txt"
fi
# 5) 入口
printf '#!/system/bin/sh\n# 宿主工具集入口：source 本文件\nHT=/data/local/aitools/hosttools\nexport PATH=$HT/bin:$HT/applets:$PATH\n' > "$HT/env.sh"
chmod 755 "$HT/env.sh"
# 6) 注入宿主默认 PATH：/data/adb/ksu/bin 在系统默认 PATH 里且可写（实测 drwxrwxrwx）。
#    这样装完就能直接 `rg`/`jq`/`sqlite3`，不需要写全路径、也不需要 source env.sh。
#    不覆盖已存在的同名项（ksud/busybox 等是 KernelSU 自己的）。
PKD=""
for p in /data/adb/ksu/bin /data/adb/magisk /data/local/tmp/bin; do
  [ -d "$p" ] && [ -w "$p" ] || continue
  PKD="$p"; break
done
if [ -n "$PKD" ]; then
  for f in "$HT"/bin/*; do
    b=$(basename "$f")
    [ -e "$PKD/$b" ] || ln -s "$f" "$PKD/$b" 2>/dev/null
  done
  # 第二遍：强制覆盖会被 toybox 抢走的同名命令（file/awk/sed/grep/tar/head/tail...）
  for b in file awk sed grep tar xargs find sort uniq head tail cut tr diff md5sum sha256sum wc jq rg fd fzf sqlite3 zstd xz lz4 tree lsof strace 7zz zip rsync curl; do
    [ -x "$HT/bin/$b" ] && ln -sf "$HT/bin/$b" "$PKD/$b" 2>/dev/null
  done
  echo "[hosttools] 已注入默认 PATH: $PKD"
fi
NA=$(ls "$HT/applets" 2>/dev/null | wc -l)
echo "[hosttools] static=$ns musl=$nd applets=$NA -> $HT"
[ "$ns" -ge 3 ] && [ "$nd" -ge 3 ] || { echo "FAIL: 铺开不完整" >&2; exit 1; }
# 6) 冒烟：静态 + 动态各测一个
# smoke：app 域无法 exec，做文件级校验；能 exec 时（root/无 SELinux 限制）再实跑一遍
for t in busybox jq python3 sqlite3 netrun; do
  [ -f "$HT/bin/$t" ] || { echo "FAIL 缺 bin/$t" >&2; exit 1; }
done
[ -f "$HT/lib/ld-musl-aarch64.so.1" ] || { echo "FAIL 缺 musl loader" >&2; exit 1; }
if "$HT/bin/busybox" echo ok >/dev/null 2>&1; then echo "[hosttools] smoke: exec OK"
else echo "[hosttools] smoke: 文件级 OK（当前域不能 exec，root 可用）"; fi
# 目录权限：非 root 域（app）必须能读写执行 bin/applets/libexec/lib/share
chmod 755 "$HT" "$HT/bin" "$HT/applets" "$HT/libexec" "$HT/lib" "$HT/share" 2>/dev/null
chmod -R a+rX "$HT/lib" "$HT/share" 2>/dev/null
chmod -R a+rX "$HT/libexec" 2>/dev/null
echo "[hosttools] smoke OK"

# 7) 清单文件：AI 检索宿主目录时第一眼就能看到有什么、怎么用
{
  echo "# hosttools 工具集清单（自动生成 $(date)）"
  echo "HT=$HT"
  echo "入口: export PATH=\$HT/bin:\$HT/applets:\$PATH"
  echo ""
  echo "## bin/ 直接执行（40 个）"
  ls "$HT/bin" | sed 's/^/  /'
  echo ""
  echo "## applets/ busybox（$(ls "$HT/applets" 2>/dev/null | wc -l) 个软链）"
  echo "  awk sed grep tar find xargs md5sum sha256sum sort uniq head tail cut tr diff wc …"
  echo ""
  echo "## 动态 musl 工具的真身在 libexec/，wrapper 在 bin/ 里自动带 loader"
  echo "## python3: $("$HT/bin/python3" -V 2>&1 | head -1)"
  echo "## 限制: 无 DNS（无 /etc/resolv.conf）；file/curl 会被 /system/bin 抢先，需写全路径"
} > "$HT/TOOLS.txt" 2>/dev/null
echo "[hosttools] 清单: $HT/TOOLS.txt"

# toolchain：aapt2 Android 原生 ELF + 依赖 so（容器 aarch64 构建必需，maven 的 x86_64 跑不了）
if [ -d "$SRC/toolchain" ]; then
  mkdir -p "$HT/toolchain"
  cp -a "$SRC"/toolchain/. "$HT/toolchain/" 2>/dev/null
  chmod 755 "$HT/toolchain/aapt2" "$HT/toolchain/aapt2.wrapper.sh" 2>/dev/null
  chmod -R a+rX "$HT/toolchain" 2>/dev/null
  echo "[hosttools] toolchain: $(ls "$HT/toolchain" | tr '\n' ' ')"
fi
