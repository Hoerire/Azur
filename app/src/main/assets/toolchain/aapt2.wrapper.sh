#!/bin/sh
LD_LIBRARY_PATH=/data/local/aitools/env/toolchain/lib:/system/lib64:/vendor/lib64 exec /data/local/aitools/env/toolchain/aapt2 "$@"
