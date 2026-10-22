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
    return _pathlib.Path("" + str(_root()) + "")

"""测当前启用模型的 TTFT / 总耗时 / 生成速度（直连 API，多轮取中位数与 P90）。
兼容两类端点：/chat/completions（messages）与 /responses（input）。"""

import json
import pathlib
import statistics
import time

import requests

CONFIG = pathlib.Path(str(_root()) + "/files/settings/llm/saved_configs.json")
PROMPT = "用大约 200 字解释什么是首 token 时间（TTFT），分点说明，并分析其取舍。"


def _cfg():
    data = json.loads(CONFIG.read_text())
    for c in data.get("configs") or []:
        if c.get("id") == data.get("active_id"):
            return c
    cs = data.get("configs") or []
    return cs[0] if cs else {}


def _pct(vals, q):
    v = sorted(vals)
    if len(v) == 1:
        return float(v[0])
    pos = (len(v) - 1) * q
    lo = int(pos)
    hi = min(lo + 1, len(v) - 1)
    return float(v[lo] * (1 - (pos - lo)) + v[hi] * (pos - lo))


def _run(sess, url, key, model, prompt, cap):
    is_resp = "/responses" in url
    if is_resp:
        body = {"model": model, "input": prompt, "stream": True}
        if cap > 0:
            body["max_output_tokens"] = cap
    else:
        body = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "stream": True,
            "stream_options": {"include_usage": True},
        }
        if cap > 0:
            body["max_tokens"] = cap
    head = {"Authorization": "Bearer " + key, "Content-Type": "application/json", "Accept": "text/event-stream"}
    t0 = time.perf_counter()
    first = None
    n = 0
    chars = 0
    used = 0
    with sess.post(url, headers=head, json=body, stream=True, timeout=180) as r:
        if r.status_code >= 400:
            raise RuntimeError("HTTP %d: %s" % (r.status_code, (r.text or "")[:200]))
        for line in r.iter_lines(decode_unicode=True):
            if not line or not line.startswith("data:"):
                continue
            raw = line[5:].strip()
            if not raw or raw == "[DONE]":
                continue
            try:
                ev = json.loads(raw)
            except Exception:
                continue
            u = ev.get("usage") or (ev.get("response") or {}).get("usage") or {}
            if isinstance(u, dict) and (u.get("output_tokens") or u.get("completion_tokens")):
                used = int(u.get("output_tokens") or u.get("completion_tokens"))
            txt = ""
            if is_resp:
                if str(ev.get("type") or "").endswith(".delta"):
                    d = ev.get("delta")
                    txt = d if isinstance(d, str) else ((d or {}).get("text") or (d or {}).get("content") or "")
            else:
                d = (ev.get("choices") or [{}])[0].get("delta") or {}
                txt = d.get("content") or d.get("reasoning_content") or ""
            if txt:
                n += 1
                chars += len(txt)
                if first is None:
                    first = time.perf_counter() - t0
    total = time.perf_counter() - t0
    # 记账优先级：官方 usage > 按字符估算(约 4 字符/token) > delta 条数
    est = max(1, int(round(chars / 4.0))) if chars else 0
    tok = used or est or n
    src = "usage" if used else ("est" if est else "delta")
    # 速度只算生成窗口：扣掉首 token 前的等待
    gen = max(total - (first if first is not None else total), 1e-6)
    return {"ttft": first if first is not None else total, "total": total, "tok": tok,
            "tps": (tok / gen) if gen else 0.0, "src": src}


def main(runs: int = 5, prompt: str = "", max_tokens: int = 256, model: str = "", endpoint: str = "", api_key: str = "") -> str:
    """测当前启用模型的 TTFT、总耗时与生成速度，多轮取中位数与 P90。"""
    c = _cfg()
    url = endpoint or c.get("endpoint") or "https://api.deepseek.com/chat/completions"
    key = api_key or c.get("api_key") or ""
    mdl = model or c.get("model") or "deepseek-chat"
    prompt = prompt or PROMPT
    runs = max(1, min(int(runs), 10))
    if not key:
        print("缺少 api_key")
        return "缺少 api_key"
    sess = requests.Session()
    rows, errs = [], []
    for i in range(runs):
        try:
            x = _run(sess, url, key, mdl, prompt, int(max_tokens))
            rows.append(x)
            print("run %d/%d ttft=%.3fs total=%.3fs tok=%d(%s) %.1f tok/s" % (i + 1, runs, x["ttft"], x["total"], x["tok"], x["src"], x["tps"]))
        except Exception as e:
            errs.append("%s: %s" % (type(e).__name__, e))
            print("run %d 失败 %s" % (i + 1, errs[-1]))
    if not rows:
        return "全部失败: " + "; ".join(errs)
    tt = [r["ttft"] for r in rows]
    tt_total = [r["total"] for r in rows]
    tp = [r["tps"] for r in rows]
    print("模型=%s 端点=%s 轮数=%d" % (mdl, url, len(rows)))
    print("TTFT  中位 %.3fs  P90 %.3fs  min %.3fs  max %.3fs" % (statistics.median(tt), _pct(tt, 0.9), min(tt), max(tt)))
    print("总耗时 中位 %.3fs  P90 %.3fs" % (statistics.median(tt_total), _pct(tt_total, 0.9)))
    print("速度  中位 %.1f tok/s  max %.1f tok/s" % (statistics.median(tp), max(tp)))
    if errs:
        print("失败 %d 轮" % len(errs))
    return "ok"