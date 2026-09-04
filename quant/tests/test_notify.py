"""notify: 多通道推送（AC-6）——mock 网络，断言 payload/分级/重试/隔离。"""


import pytest

from quant.alerts import notify as notify_mod
from quant.alerts.notify import Notifier

CONFIG = """
channels:
  - {name: feishu, type: feishu_webhook, webhook: "https://f.example/hook", enabled: true, tier: regular}
  - {name: wecom, type: wecom_webhook, webhook: "https://w.example/hook", enabled: true, tier: regular}
  - {name: pushplus, type: pushplus, token: "tok123", enabled: true, tier: urgent}
  - {name: disabled-one, type: pushplus, token: "x", enabled: false, tier: regular}
"""


@pytest.fixture
def notifier(tmp_path):
    cfg = tmp_path / "notify.yaml"
    cfg.write_text(CONFIG)
    return Notifier(cfg)


@pytest.fixture
def calls(monkeypatch):
    recorded: list[dict] = []

    def fake_post(url, json=None, timeout=None):
        recorded.append({"url": url, "json": json})
        if "f.example" in url:
            return _Resp({"code": 0})
        if "w.example" in url:
            return _Resp({"errcode": 0})
        return _Resp({"code": 200})

    monkeypatch.setattr(notify_mod.requests, "post", fake_post)
    return recorded


class _Resp:
    def __init__(self, data):
        self._data = data

    def json(self):
        return self._data

    def raise_for_status(self):
        pass


def test_regular_level_skips_urgent_only_channels(notifier, calls):
    results = notifier.send("标题", "内容", level="regular")
    # pushplus(tier=urgent) 与 disabled 通道不参与
    assert set(results) == {"feishu", "wecom"}
    assert all(results.values())
    assert len(calls) == 2


def test_urgent_level_hits_all_enabled_channels(notifier, calls):
    results = notifier.send("紧急", "内容", level="urgent")
    assert set(results) == {"feishu", "wecom", "pushplus"}
    assert all(results.values())
    assert len(calls) == 3


def test_channel_payloads(notifier, calls):
    notifier.send("标题", "内容", level="urgent")
    by_url = {c["url"]: c["json"] for c in calls}
    assert by_url["https://f.example/hook"]["msg_type"] == "text"
    assert "标题" in by_url["https://f.example/hook"]["content"]["text"]
    assert by_url["https://w.example/hook"]["msgtype"] == "markdown"
    assert by_url["https://w.example/hook"]["markdown"]["content"].startswith("**标题**")
    assert by_url["https://www.pushplus.plus/send"]["token"] == "tok123"
    assert by_url["https://www.pushplus.plus/send"]["template"] == "markdown"


def test_retry_and_isolation(notifier, monkeypatch):
    state = {"n": 0}

    def flaky_post(url, json=None, timeout=None):
        if "w.example" in url:
            state["n"] += 1
            if state["n"] == 1:
                raise ConnectionError("boom")  # 第一次失败，重试成功
            return _Resp({"errcode": 0})
        return _Resp({"code": 0})

    monkeypatch.setattr(notify_mod.requests, "post", flaky_post)
    results = notifier.send("t", "c", level="regular")
    assert results == {"feishu": True, "wecom": True}
    assert state["n"] == 2  # 重试了 1 次


def test_permanent_failure_does_not_break_other_channels(notifier, monkeypatch):
    def bad_post(url, json=None, timeout=None):
        if "w.example" in url:
            raise ConnectionError("down")
        return _Resp({"code": 0})

    monkeypatch.setattr(notify_mod.requests, "post", bad_post)
    results = notifier.send("t", "c", level="regular")
    assert results == {"feishu": True, "wecom": False}  # 单通道失败不互斥


def test_missing_config_silent(notifier, tmp_path):
    empty = Notifier(tmp_path / "nope.yaml")
    assert empty.send("t", "c") == {}


def test_feishu_signature(monkeypatch, tmp_path):
    import time as time_mod

    cfg = tmp_path / "notify.yaml"
    cfg.write_text(
        "channels:\n"
        "  - {name: f, type: feishu_webhook, webhook: 'https://f.example/hook', secret: 'sec', enabled: true}\n"
    )
    captured = []

    def fake_post(url, json=None, timeout=None):
        captured.append(json)
        return _Resp({"code": 0})

    monkeypatch.setattr(notify_mod.requests, "post", fake_post)
    monkeypatch.setattr(time_mod, "time", lambda: 1700000000)
    Notifier(cfg).send("t", "c")
    body = captured[0]
    assert body["timestamp"] == "1700000000"
    assert "sign" in body  # 签名已生成
    # 验证签名可复现
    import base64
    import hashlib
    import hmac as hmac_mod

    s = "1700000000\nsec"
    expect = base64.b64encode(hmac_mod.new(s.encode(), digestmod=hashlib.sha256).digest()).decode()
    assert body["sign"] == expect
