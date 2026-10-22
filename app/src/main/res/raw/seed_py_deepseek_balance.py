# 小鲸鱼：DeepSeek 余额 / 今日用量 / 折扣时段
import datetime
import json
import os
import sqlite3

import requests

_PKGS = ('com.hoerire.tools', 'com.hoerire.ai', 'com.hoerire.zafiro', 'com.hoerire.tools.zafiro')
_SUFFIXES = ('/chat/completions', '/responses', '/completions', '/messages')


def _pick(rel):
    for pkg in _PKGS:
        p = '/data/user/0/%s/%s' % (pkg, rel)
        if os.path.exists(p):
            return p
    return '/data/user/0/%s/%s' % (_PKGS[0], rel)


def _base(endpoint):
    b = (endpoint or '').strip().rstrip('/')
    for suf in _SUFFIXES:
        if b.endswith(suf):
            b = b[:-len(suf)]
            break
    if not b:
        b = 'https://api.deepseek.com'
    return b.rstrip('/')


def main(detailed: bool = False):
    '''查询 DeepSeek 账户余额、今日本机 token 用量与折扣时段状态（小鲸鱼挂件的 Anvil 版）。'''
    cfg_path = _pick('files/settings/llm/saved_configs.json')
    db_path = _pick('databases/conversation_history.db')
    lines = []
    try:
        with open(cfg_path, encoding='utf-8') as f:
            cfg = json.load(f)
    except Exception as e:
        print('读取配置失败：%s' % e)
        return
    conf = None
    for c in cfg.get('configs', []):
        if c.get('id') == cfg.get('active_id'):
            conf = c
            break
    if conf is None and cfg.get('configs'):
        conf = cfg['configs'][0]

    if conf:
        try:
            key = conf.get('api_key', '')
            url = _base(conf.get('endpoint')) + '/user/balance'
            r = requests.get(url, headers={'Authorization': 'Bearer ' + key}, timeout=20)
            try:
                data = r.json()
            except Exception:
                lines.append('余额查询失败：HTTP %d %s' % (r.status_code, (r.text or '')[:120].replace('\n', ' ')))
                data = None
            if isinstance(data, dict):
                for b in data.get('balance_infos', []):
                    lines.append('余额 %s：%s（赠送 %s / 充值 %s）' % (
                        b.get('currency'), b.get('total_balance'),
                        b.get('granted_balance'), b.get('topped_up_balance')))
                if not data.get('balance_infos'):
                    lines.append('余额接口返回：' + json.dumps(data, ensure_ascii=False)[:300])
            if detailed:
                lines.append('配置：%s · 模型 %s · 端点 %s' % (conf.get('name'), conf.get('model'), conf.get('endpoint')))
        except Exception as e:
            lines.append('余额查询失败：%s: %s' % (type(e).__name__, e))
    else:
        lines.append('未找到 LLM 配置')

    try:
        start = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        start_ms = int(start.timestamp() * 1000)
        db = sqlite3.connect(db_path)
        cur = db.cursor()
        cur.execute('SELECT message_json FROM conversation_entry WHERE timestamp >= ?', (start_ms,))
        tin = tout = treason = tcache = 0
        turns = 0
        for (mj,) in cur.fetchall():
            m = json.loads(mj)
            if 'Assistant' in (m.get('type') or ''):
                u = (m.get('message') or {}).get('usage') or {}
                if u:
                    turns += 1
                    tin += u.get('inputTokens') or 0
                    tout += u.get('outputTokens') or 0
                    treason += u.get('reasoningTokens') or 0
                    tcache += u.get('cacheReadTokens') or 0
        db.close()
        lines.append('今日用量：%d 轮 · 输入 %s · 输出 %s · 思考 %s · 缓存读 %s' % (
            turns, format(tin, ','), format(tout, ','), format(treason, ','), format(tcache, ',')))
        if detailed:
            lines.append('（思考与缓存读按各自计价规则计费，缓存命中通常远低于标准输入价）')
    except Exception as e:
        lines.append('今日用量统计失败：%s' % e)

    tz = datetime.timezone(datetime.timedelta(hours=8))
    now8 = datetime.datetime.now(tz)
    mins = now8.hour * 60 + now8.minute
    if 30 <= mins < 510:
        lines.append('时段：低谷期（北京时间 00:30–08:30 内），适用折扣的模型此刻更省')
    else:
        lines.append('时段：标准期（DeepSeek 低谷折扣时段为北京时间 00:30–08:30）')

    print('\n'.join(lines))