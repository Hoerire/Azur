---
name: add-worktree
description: Use when the user wants to work on something in parallel without touching the current tree — "new worktree", "spin up a branch for this", "work on X separately". 本仓库 无 .git，等价做法是「复制源码树 + 新建工程树」，路径为 `<aitools>/源码/<task>` 与 `<aitools>/工程/<task>`。
---

# Add Worktree

> 本仓库无 git 历史，**没有 `.git`**，`git worktree` 全部命令不可用。
> 等价语义：一条"工作线"= `源码/<task>`（可编辑副本）+ `工程/<task>`（构建镜像）+ 可选独立包名/根域。

Worktrees live in a sibling directory of the primary repo, one directory per branch:

    <parent of primary repo>/zafiro-worktrees/<branch name>

The directory is the branch name, character for character. A slashed name (`fix/latex-render`) nests: `zafiro-worktrees/fix/latex-render`. `dev-to-main` at `/Users/niki/.repo/android/zafiro-worktrees/dev-to-main` is a lived example of the layout.

## 1 — Name the branch from the requirement

> 本仓库无 git 分支，本节用于**任务目录命名**（`源码/<task>` 与 `工程/<task>` 同名）；命名规范原文保留。

`<type>-<kebab-case-summary>`, following the repo's existing branch convention (`feat/agent-status`, `refactor/aidl-state-sync`).

| Type | For |
|------|-----|
| `feat` | new capability |
| `fix` | bug fix |
| `refactor` | behaviour-preserving restructure |
| `perf` | speed / memory |
| `chore` | build, deps, tooling |
| `test` | tests only |
| `docs` | docs only |

Rules: lowercase ASCII, hyphens only, summary describes the change (`fix/latex-blank-render`, not `fix/niki-work`); ≤ 40 characters total, since the name is also the directory name; no ticket numbers, no dates.

The summary comes from what the user asked for. If the user asks for a parallel line without saying what the work is, ask before naming — a task name guessed from nothing is a rename later. 目录名用连字符，不用斜杠（避免与 `工程/<tree>` 的层级冲突）。

## 2 — Resolve the paths

```sh
A=/data/local/aitools                    # 根域；测试包可能是 $A-btN
TASK=fix-latex-blank-render              # from step 1
S=$A/源码/$TASK                          # 可编辑副本（约 70 MB，含 assets）
P=$A/工程/$TASK                          # 构建镜像；必须与源码树同名
ls -d "$S" "$P" 2>/dev/null && echo "REFUSE: 已存在，先收线"
```

`build-src.sh` 的硬性要求：`工程/<名>` 与 `源码/<名>` **同名一一对应**，否则拒绝构建。

## 3 — Create it

Base 是当前主线 `源码/zafiro-15-1.8.1-Fork`，除非用户指定别的基线。

```sh
cp -a $A/源码/zafiro-15-1.8.1-Fork "$S"
mkdir -p "$P" && rsync -a --exclude build --exclude .gradle --exclude .kotlin "$S/" "$P/"
# 构建这条线（必须显式 --src=，且 VARIANT=debug）
cd $A && ZAFIRO_VARIANT=debug ZAFIRO_APK_NAME="Forge-$TASK.apk" \
  sh env.sh build --src="$P" --max-workers=1
```

真机并行验证时给测试线换包名，根域自动分到 `$A-btN`：

```sh
sed -i 's/applicationId = "com.hoerire.tools"/applicationId = "com.hoerire.tools.bt"/' "$P/app/build.gradle.kts"
```

Report the absolute paths (`$S`, `$P`, 产物 APK) back to the user.

## 4 — Remove it

```sh
rm -rf "$S" "$P"
pm uninstall com.hoerire.tools.bt 2>/dev/null
```

Run removal only on explicit request; leftover trees with uncommitted work are the user's to discard. 收线前先把要保留的改动人工挑回主线（`rsync -a --dry-run` 先看差异，勿整目录覆盖）。

## 5 — 共用环境，不要复制

`$A/环境`（rootfs + SDK + Gradle，约 2.8 GB）唯一，两条线共用。换根域构建前清残留 bind 挂载：

```sh
for p in $(awk '{print $2}' /proc/mounts | grep -F "$A/环境/rootfs/"); do umount -l "$p"; done
```
