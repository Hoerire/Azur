---
name: tool-config
description: Read or change the App's own settings by editing JSON files in its sandbox. Use whenever the user asks to see, explain, change, add or remove App configuration (agent profiles, execution rules, text actions, LLM configs, theme, floating ball), or when a setting looks wrong, missing or stuck.
---

# App config

App keeps its settings as plain JSON files in its own sandbox. There is no config
tool and no API: you read and write those files directly with the host `python3` (via `shell`) or
`shell`. This skill gives you the file map, the shape of each file, the rules that
keep you from damaging user data, and how long a change takes to take effect.

## Where the settings live

```
/data/user/0/<package>/files/settings/
```

You do not need to know `<package>` in advance. Look around the App sandbox you are
already running inside, find the directory that contains `settings/`, and work from
there. Read the directory listing before each write so you always operate on the file
that actually exists, rather than one you assumed.

If a file below does not exist yet, App falls back to a built-in default. Writing
the file for the first time is normal and correct.

## The files

| File | Holds | Shape |
| --- | --- | --- |
| `app_state.json` | app preferences: theme, language, floating ball, notifications, markdown scale, draft text, pinned conversations | flat object, one key per setting |
| `tools/builtin_tools.json` | which built-in tools are on | `{"version":2,"enabled":{"<tool_name>":true}}` |
| `tools/custom_py_tools.json` | custom Python tools | `{"tools":[{name,code,description,schema_json,enabled,timeout_ms}]}` |
| `tools/text_actions.json` | text-selection actions | `{"actions":[{name,prompt,enabled,requires_screenshot,run_in_background}]}` |
| `rules/execution_rules.json` | command safety rules | `{"rules":[{id,name,enabled_mode,patterns}]}` |
| `rules/takeover_rules.json` | voice-assistant takeover rules | `{"rules":[{id,name,enabled,target,patterns}],"default_target":{...}}` |
| `agents/registry.json` | agent profiles | `{"agents":[{id,name,alias,enabled,order,memory_mode,created_at,updated_at}]}` |
| `agents/<id>/memory.json` | one agent's saved memories | `{"memories":[{id,content,created_at,updated_at}]}` |
| `llm/saved_configs.json` | saved model configs and the active one | `{"active_id":"...","prompt":"...","configs":[{id,name,provider,endpoint,api_key,model,protocol,supports_images,proxy,thinking_level,max_tokens,context_window_tokens,created_at,updated_at}]}` |

Read a file before you change it. Never invent fields — copy the shape you actually
saw, and keep every key you are not deliberately changing.

## Two rules you must not skip

### 1. Sensitive values: mask them, and tell the user

`llm/saved_configs.json` stores `api_key` in plain text. Anything you
read enters this conversation and therefore goes to the model provider.

- Prefer not to read those fields at all. When a task needs the rest of the file,
  read it and then remove or mask the secret before you print or echo it.
- Mask as `sk-ba2f****24dc0`: keep the first 4 and last 4 characters, replace the
  middle with `****`.
- **If you do end up seeing a real secret, say so plainly in your reply** — name the
  file and field, and suggest the user rotate that key. Do not hide it, and do not
  repeat the value.
- Never write a masked value back, and never ask the user to tell you a secret — that
  would put the value in this conversation, which is the thing you are avoiding. When a
  key or token must change, tell the user to edit it themselves in the App settings
  UI, then carry on with whatever part of the task does not need it.

### 2. Write to a temp file, verify, then replace

Never edit a settings file in place. A half-written JSON file can make App fail to
start. Write the whole document beside the original as `<name>__tmp.json` in the same
directory, read it back and confirm it parses and has the shape you intended, then
replace the original in one atomic step so App never sees a partial file. Do not
stream a file and close it in place, and do not truncate and rewrite.

Always leave the file a valid JSON document: if it parsed before your change, it must
parse after. If your verification fails, delete the temp file and leave the original
untouched.

## When a change takes effect

This is the part that surprises people, so check it before you tell the user
something is broken.

| What you changed | Effect |
| --- | --- |
| execution rules, takeover rules, text actions, built-in tool flags, saved LLM configs, agent profiles | **hot** — App re-reads these from disk on the next turn, and the settings screens re-read on open. No restart needed. |
| `app_state.json` preferences: theme, language, floating ball, resident notification, turn notification, keep screen on, always show message actions, voice-assistant isolated session, markdown scale | **cold** — these are loaded once at app start into in-memory state. The change is on disk but not visible yet. |
| `agents/<id>/memory.json` | hot — memories are re-read each turn |
| anything you are unsure about | assume cold, and tell the user a restart may be needed |

So: after editing an `app_state.json` preference, do not claim it is done and working.
Say what you changed and that it needs a restart to show.

**Do not restart App yourself, and do not run anything that kills or cold-starts the
app.** A restart ends this conversation, so only the user can decide to do it. Report
that a restart is needed, hand the decision over, and stop there.

## Notes

- Keep `id` and `name` pairs consistent when you edit rules; other code looks rules up
  by `id`.
- `max_tokens` and `context_window_tokens` inside a saved LLM config are **strings**,
  not numbers — an empty string means "not set, use the default". Do not convert them.
- Deleting an entry from `tools/text_actions.json` is permanent; unlike some other
  defaults it is not restored.
- Prefer host `python3` over raw `shell` JSON editing here: JSON handling in Python is safer, and it
  avoids shell quoting bugs around regex patterns, which contain backslashes.

---

## 附：执行规则（Execution Rules）的维护

# 执行规则（设计原则与维护）

## 分层原则（最重要）
规则的目的是**省时间**，不是限权。按「错了会怎样」分三层：
| 层 | 模式 | 放什么 | 命中后 |
|---|---|---|---|
| ① 破坏性命令 | `DISABLED`（默认关） | rm -rf、mkfs、dd、setprop、reboot、pm uninstall、su | 不拦；需要时自己在规则页打开 |
| ② 无效操作 | `ALWAYS` | 做了必错或无意义：全盘枚举、手搓 chroot、grep 的 PCRE 选项、gradle 旧参数、直接跑 gradlew、`--src` 指 src/、手工 tar 打环境包 | 直接拦 + `怎么改:` 提示 |
| ③ 高影响操作 | `CONFIRM` | 写共享存储、写 `/tmp`、写系统分区、写其他 App 沙盒、pkill 宽匹配 | **弹窗确认**；允许即执行，拒绝 `CONFIRM_DENIED`，无渠道 `CONFIRM_UNAVAILABLE` |

实测（2026-10-20）：`CONFIRM` 会真的弹窗，用户点「允许」后命令照常执行 → 既不放任也不堵路。
判断标准：**能用规则判定的进②③；需要语义或跨调用状态的（串行构建、改完回读、临时目录自清）只能留技能。**

## 结构
```
rules[0] builtin-dangerous  "default"         DISABLED  10 条
rules[1] builtin-guard      "无效操作 · 直接拦"  ALWAYS    15 条
rules[2] builtin-impact     "高影响操作 · 需确认" CONFIRM    7 条
```
- 后两者都带 `tool_filter = [shell, zafiro_env]`：只在执行类工具上匹配，写文档/写文件不参与。
- 每条规则都有 `hint`，命中时返回 `怎么改:` 一行。

## pattern 写法铁律
1. **锚定命令起点** `(^|[;&|\n])\s*`：`echo "…pkill -f…" > file`、文档正文都不会误伤。
2. 例外判定用全字符类：`find` 放行 `-maxdepth` 要写 `(?![\s\S]*maxdepth)`；用 `[\n]*` 因空格而恒不生效（踩过）。
3. 区分源/目标：`cp|mv|ln` 只判目标在末尾（`\s/(storage|sdcard)/\S*\s*$`），源在共享存储的备份场景放行。
4. 拦 `/tmp` 用 `>\s*/tmp/`，天然不命中 `/data/local/tmp`（pm install 需要）。
- 宿主 `python3` 路径：`/data/user/0/com.hoerire.tools/files/hosttools/bin/python3`
   → 副作用：**写文档时正文里出现这些 API 名，会把自己送进规则**；写这类文档要用字符串拼接避开。
