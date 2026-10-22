# 性能加速：高负载任务期间点亮全部 8 核，跑完恢复
import subprocess


def main(action: str = "status"):
    '''高负载任务的性能加速开关（CPU 全核全速）：boost=点亮全部 8 核（performance 调速器 + 频率下限拉满）；restore=按快照恢复；status=查看每核状态；snapshot=手动存档。'''
    valid = {"boost", "restore", "status", "snapshot"}
    if action not in valid:
        print("用法：action ∈ {boost, restore, status, snapshot}")
        return
    script = _perform_script()
    if not script:
        print("未找到 perform.sh（候选：/data/ig5/perform.sh、/opt/perform.sh、同目录）")
        return
    try:
        r = subprocess.run(
            ["su", "-c", "sh " + script + " " + action],
            capture_output=True, text=True, timeout=60,
        )
        out = (r.stdout or "").strip()
        print(out if out else "(无输出)")
        err = (r.stderr or "").strip()
        if err:
            print("[stderr] " + err[:300])
    except Exception as e:
        print("执行失败：%s: %s" % (type(e).__name__, e))

def _perform_script():
    """找 perform.sh：本机 /data/ig5，chroot 内 /opt，别处可与工具同目录。"""
    for _s in ("/data/ig5/perform.sh", "/opt/perform.sh", "perform.sh"):
        if _pathlib.Path(_s).exists():
            return _s
    return ""

