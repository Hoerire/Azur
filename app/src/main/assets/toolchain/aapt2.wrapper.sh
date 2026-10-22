#!/bin/sh
# 相对自身定位：任意根域（/data/local/aitools 或 /data/local/aitools-btN）都能用
D=$(cd "$(dirname "$0")" && pwd)
LD_LIBRARY_PATH="$D/lib:/system/lib64:/vendor/lib64" exec "$D/aapt2" "$@"
