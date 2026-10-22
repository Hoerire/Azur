#!/system/bin/sh
# verify.sh - machine-checkable preflight for build/deploy.
# Read-only, side-effect free. Exit 0 = PASS, else FAIL.
# Every check reports independently; a failed check must never be silently skipped.
# Usage: sh verify.sh [tree]
A=${AITOOLS_ROOT:-/data/local/aitools}
N=0
pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; N=$((N+1)); }
note() { echo "NOTE  $*"; }

T=${1:-}
if [ -z "$T" ]; then
  cands=""
  for d in "$A"/src/*/; do
    [ -d "$d" ] || continue
    if [ -f "$d/settings.gradle.kts" ] || [ -f "$d/settings.gradle" ]; then
      cands="$cands $(basename "$d")"
    fi
  done
  if [ -z "$cands" ]; then
    for d in "$A"/src/*/; do [ -d "$d" ] && cands="$cands $(basename "$d")"; done
  fi
  cnt=$(printf '%s\n' $cands | grep -c .)
  if [ "$cnt" -eq 1 ]; then T=$(echo $cands); fi
  if [ "$cnt" -eq 0 ]; then fail "no source tree under $A/src"; fi
  if [ "$cnt" -gt 1 ]; then
    # prefer the only tree that already has a project/ mirror, else complain
    pm=""
    for c in $cands; do [ -d "$A/project/$c" ] && pm="$pm $c"; done
    pc=$(printf '%s\n' $pm | grep -c .)
    if [ "$pc" -eq 1 ]; then T=$(echo $pm); else
      fail "ambiguous tree:$cands - pass name as $0 <tree>"
    fi
  fi
fi

S="$A/src/$T"
P="$A/project/$T"

if [ -n "$T" ]; then
  if [ -d "$S" ]; then pass "tree $T"; else fail "tree missing: $S"; fi
else
  note "no tree selected - tree checks skipped"
fi

if [ -n "$T" ] && [ -d "$P" ] && [ -d "$S" ]; then
  d=$(diff -rq "$S" "$P" 2>/dev/null | grep -vE "/build/|build$|[.]gradle|[.]kotlin|[.]idea/|/schemas/" | head -3)
  if [ -z "$d" ]; then pass "src == project"; else
    echo "$d"; fail "src/project differ (run: sh $A/project/sync.sh $T)"
  fi
else
  [ -n "$T" ] && note "no project mirror yet: $P (run project/sync.sh before building)"
fi

ASSETS="$S/app/src/main/assets"
if [ -d "$ASSETS" ]; then
  # split literals so this script never matches its own patterns
  P1="/home/buil""der"
  P2="/data/data/com[.][a-z]*[.]files/usr"
  h=$(grep -rlE "$P1|$P2" "$ASSETS" 2>/dev/null | grep -v "buildkit/verify.sh" | head -3)
  if [ -z "$h" ]; then pass "assets free of build-machine paths"; else fail "stale paths: $h"; fi
else
  note "no assets/ in this tree - skipped"
fi

TC="$A/env/toolchain/aapt2"
if [ -x "$TC" ]; then
  v=$(LD_LIBRARY_PATH= "$TC" version 2>&1 | head -1)
  case "$v" in
    *Android*) pass "aapt2 standalone: $v" ;;
    *) fail "aapt2 not runnable: $v" ;;
  esac
else
  note "no aapt2 at $TC - skipped (not an Android toolchain)"
fi

SK="$ASSETS/skills"
if [ -d "$SK" ]; then
  bad=""; cnt=0
  for k in "$SK"/*/SKILL.md; do
    [ -f "$k" ] || continue
    cnt=$((cnt+1)); n=$(basename "$(dirname "$k")")
    grep -q "^name:" "$k" || bad="$bad $n:no-name"
    grep -q "^description:" "$k" || bad="$bad $n:no-desc"
    c=$(sed -n 's/^description:[ ]*//p' "$k" | head -1 | wc -c)
    [ "$c" -le 500 ] || bad="$bad $n:desc-$c"
    [ "$c" -ge 40 ] || bad="$bad $n:desc-too-short-$c"
  done
  if [ "$cnt" -eq 0 ]; then note "no skills packaged - skipped"; else
    if [ -z "$bad" ]; then pass "skills ok ($cnt)"; else fail "skills:$bad"; fi
  fi
else
  note "no assets/skills/ in this tree - skipped"
fi

APK=$(ls -t "$A"/out/*.apk 2>/dev/null | head -1)
if [ -n "$APK" ]; then
  pass "artifact $(basename "$APK") $(stat -c %s "$APK")B md5=$(md5sum "$APK" | cut -c1-12)"
else
  fail "no apk in $A/out"
fi

echo "----"
if [ "$N" -eq 0 ]; then echo "VERIFY PASS"; exit 0; fi
echo "VERIFY FAIL ($N)"; exit 1
