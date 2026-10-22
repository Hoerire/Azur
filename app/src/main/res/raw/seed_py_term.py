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

import os
import sqlite3

LOG_DIR = str(_root()) + "/files/terminal"
LOG = os.path.join(LOG_DIR, "live.log")


def _ensure():
    os.makedirs(LOG_DIR, exist_ok=True)
    if not os.path.exists(LOG):
        with open(LOG, "w", encoding="utf-8") as f:
            f.write("")


def _tail(path: str, n: int) -> str:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
        return "".join(lines[-n:])
    except FileNotFoundError:
        return ""


def main(action: str = "log", text: str = "", lines: int = 40) -> str:
    """终端日志：action=log 追加一行进度；action=read 读回最后 lines 行；action=clear 清空。"""
    print(_main(action, text, lines))
    return "ok"


def _main(action: str, text: str, lines: int) -> str:
    import time
    _ensure()
    if action == "log":
        ts = time.strftime("%H:%M:%S")
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(f"[{ts}] {text}\n")
        return f"logged: [{ts}] {text}"
    if action == "read":
        out = _tail(LOG, int(lines))
        return out if out else "(空)"
    if action == "clear":
        with open(LOG, "w", encoding="utf-8") as f:
            f.write("")
        return "cleared"
    return "unknown action"