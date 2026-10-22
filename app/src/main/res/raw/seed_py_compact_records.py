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

import sqlite3, json, datetime, re

DB = str(_root()) + "/databases/conversation_history.db"
STATE = str(_root()) + "/files/settings/app_state.json"

def _rows():
    con = sqlite3.connect("file:%s?mode=ro" % DB, uri=True)
    rows = []
    for cid, eid, ts, mj in con.execute(
        "select conversation_id,id,timestamp,message_json from conversation_entry order by timestamp"):
        try:
            o = json.loads(mj)
        except Exception:
            continue
        if not str(o.get("type", "")).endswith("User"):
            continue
        for b in o.get("content", []) or []:
            t = b.get("text", "") or ""
            if not t.startswith("<zfr-summary>"):
                continue
            def grab(k):
                m = re.search(k + r":\s*([^\n]+)", t)
                return m.group(1).strip() if m else ""
            g = re.search(r"##\s*Goal\s*\n+(.+)", t)
            rows.append((datetime.datetime.fromtimestamp(ts / 1000).strftime("%m-%d %H:%M"),
                         grab("generation") or "1", grab("folded-tokens"), grab("keep-turns"),
                         len(t), g.group(1).strip()[:70] if g else "", t))
    con.close()
    return rows

def main(detailed: bool = False, limit: int = 10):
    """查看 Azur 上下文的压缩记录与摘要（读本机对话库中的 <zfr-summary> 消息）。"""
    rows = _rows()
    try:
        with open(STATE) as f:
            st = json.load(f)
        th = "阈值 %s tokens / %s%%（窗口 128k）" % (
            st.get("context_compaction_threshold_tokens"),
            st.get("context_compaction_threshold_percent"))
    except Exception as e:
        th = "阈值读取失败: %s" % e
    print("压缩记录 %d 条   %s" % (len(rows), th))
    if not rows:
        print("（没有 <zfr-summary> 消息，尚未发生压缩）")
    for i, (tm, gen, folded, keep, n, goal, text) in enumerate(rows[-limit:], 1):
        print("%d) %s  gen%s  folded %s tokens  keep %s  摘要 %d 字" % (i, tm, gen, folded, keep, n))
        if goal:
            print("   Goal: " + goal)
        if detailed:
            body = text.split("</zfr-summary>")[0]
            body = re.sub(r"^\s*<zfr-summary>.*?<summary>", "", body, flags=re.S)
            lines = body.strip().splitlines()
            print("   ---- 摘要正文 %d 行 ----" % len(lines))
            for ln in lines[:40]:
                print("   " + ln)