import pathlib as _pathlib


def _root():
    """当前应用自己的数据根：能读到的那个包目录。"""
    for _pkg in ("com.hoerire.tools", "com.hoerire.ai", "com.hoerire.zafiro", "com.hoerire.tools.zafiro"):
        _p = _pathlib.Path("/data/user/0") / _pkg
        try:
            if _p.exists():
                return _p
        except OSError:
            continue
    return _pathlib.Path("/data/user/0/com.hoerire.ai")

"""上下文用量：读当前配置的窗口上限（未设置=1,000,000）与本对话已用 token，算出占用率并在接近阈值时建议预警。"""
import json, os, glob, shutil, sqlite3, tempfile

BASE = str(_root()) + ""
CONF = BASE + "/files/settings/llm/saved_configs.json"
DBDIR = BASE + "/databases"
DEFAULT_WINDOW = 1_000_000


def _window():
    with open(CONF, encoding="utf-8") as f:
        d = json.load(f)
    aid = d.get("active_id")
    cfg = next((c for c in d.get("configs", []) if c.get("id") == aid), None) or {}
    raw = str(cfg.get("context_window_tokens") or "").strip()
    win = int(raw) if raw.isdigit() and int(raw) > 0 else DEFAULT_WINDOW
    return win, cfg.get("name"), cfg.get("model"), bool(raw)


def _usage(conversation_id=None, latest=8):
    src = DBDIR + "/conversation_history.db"
    tmp = tempfile.mkdtemp(prefix="ctx_")
    try:
        for f in glob.glob(src + "*"):
            shutil.copy(f, os.path.join(tmp, os.path.basename(f)))
        c = sqlite3.connect(os.path.join(tmp, "conversation_history.db"))
        cid = conversation_id or c.execute(
            "select id from conversation order by updated_at desc limit 1").fetchone()[0]
        rows = c.execute(
            "select message_json from conversation_entry where conversation_id=? order by timestamp desc limit ?",
            (cid, latest)).fetchall()
        for (raw,) in rows:
            m = json.loads(raw)
            u = (m.get("message") or {}).get("usage") or {}
            if u.get("inputTokens") is not None:
                return cid, u
        return cid, {}
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(conversation_id: str = "", warn_ratio: float = 0.8, detailed: bool = False):
    win, name, model, configured = _window()
    cid, u = _usage(conversation_id or None)
    used = sum(int(u.get(k) or 0) for k in ("inputTokens", "cacheReadTokens", "cacheWriteTokens"))
    pct = used * 100.0 / win if win else 0.0
    out = []
    out.append("窗口上限 %s（%s）" % (format(win, ","), "配置值" if configured else "默认值/未设置"))
    if name or model:
        out.append("配置 %s / %s" % (name or "?", model or "?"))
    out.append("会话 %s 已用 %s（%.1f%%）" % ((cid or "?")[:8], format(used, ","), pct))
    if detailed and u:
        out.append("明细 input=%s cacheRead=%s cacheWrite=%s output=%s" % tuple(
            format(int(u.get(k) or 0), ",")
            for k in ("inputTokens", "cacheReadTokens", "cacheWriteTokens", "outputTokens")))
    if pct >= warn_ratio * 100:
        out.append("预警 已达 %.0f%%，先给交接摘要再继续" % pct)
    elif pct >= warn_ratio * 75:
        out.append("提示 接近阈值 %.0f%% / %.0f%%" % (pct, warn_ratio * 100))
    else:
        out.append("正常")
    print("\n".join(out))