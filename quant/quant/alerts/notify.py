"""IM 多通道推送：飞书 webhook + 企业微信机器人 + pushplus（微信直达兜底）。

通道分级（plan v1.2）:
    - level="urgent"  -> 所有启用通道（双通道轰炸）
    - level="regular" -> 仅 tier=regular 的通道（保护 pushplus 免费额度）
单通道失败不影响其他通道；每通道失败重试 1 次。
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import time
from pathlib import Path

import requests

from ..data.store import DATA_DIR

logger = logging.getLogger("quant.notify")

DEFAULT_CONFIG = DATA_DIR / "notify.yaml"
TIMEOUT = 10


def _feishu_sign(secret: str, timestamp: int) -> str:
    string_to_sign = f"{timestamp}\n{secret}"
    digest = hmac.new(string_to_sign.encode(), digestmod=hashlib.sha256).digest()
    return base64.b64encode(digest).decode()


class Notifier:
    def __init__(self, config_path: Path | str | None = None):
        self.config_path = Path(config_path) if config_path else DEFAULT_CONFIG
        self.channels: list[dict] = []
        self._load()

    def _load(self) -> None:
        import yaml

        if not self.config_path.exists():
            logger.warning("notify 配置不存在: %s（推送将静默跳过）", self.config_path)
            return
        cfg = yaml.safe_load(self.config_path.read_text()) or {}
        self.channels = [c for c in cfg.get("channels", []) if c.get("enabled", True)]

    # ---------- 通道发送 ----------
    def _send_feishu(self, ch: dict, title: str, content: str) -> dict:
        body: dict = {"msg_type": "text", "content": {"text": f"{title}\n{content}"}}
        secret = ch.get("secret")
        if secret:
            ts = int(time.time())
            body["timestamp"] = str(ts)
            body["sign"] = _feishu_sign(secret, ts)
        resp = requests.post(ch["webhook"], json=body, timeout=TIMEOUT)
        resp.raise_for_status()
        return resp.json()

    def _send_wecom(self, ch: dict, title: str, content: str) -> dict:
        body = {"msgtype": "markdown", "markdown": {"content": f"**{title}**\n{content}"}}
        resp = requests.post(ch["webhook"], json=body, timeout=TIMEOUT)
        resp.raise_for_status()
        return resp.json()

    def _send_pushplus(self, ch: dict, title: str, content: str) -> dict:
        body = {"token": ch["token"], "title": title, "content": content, "template": "markdown"}
        resp = requests.post("https://www.pushplus.plus/send", json=body, timeout=TIMEOUT)
        resp.raise_for_status()
        return resp.json()

    _SENDERS = {"feishu_webhook": _send_feishu, "wecom_webhook": _send_wecom,
                "pushplus": _send_pushplus}

    # ---------- 对外接口 ----------
    def send(self, title: str, content: str, level: str = "regular") -> dict[str, bool]:
        """发送消息，返回各通道成功与否。level: regular / urgent。"""
        targets = [c for c in self.channels
                   if level == "urgent" or c.get("tier", "regular") == "regular"]
        results: dict[str, bool] = {}
        for ch in targets:
            sender = self._SENDERS.get(ch["type"])
            if sender is None:
                logger.warning("未知通道类型: %s", ch["type"])
                results[ch.get("name", ch["type"])] = False
                continue
            ok = False
            for attempt in range(2):  # 失败重试 1 次
                try:
                    resp = sender(self, ch, title, content)
                    ok = _is_success(ch["type"], resp)
                    if ok:
                        break
                    logger.warning("通道 %s 返回失败: %s", ch.get("name"), resp)
                except Exception as exc:
                    logger.warning("通道 %s 第 %d 次发送异常: %s", ch.get("name"), attempt + 1, exc)
            results[ch.get("name", ch["type"])] = ok
        return results


def _is_success(channel_type: str, resp: dict) -> bool:
    if channel_type == "feishu_webhook":
        return resp.get("code") == 0 or resp.get("StatusCode") == 0
    if channel_type == "wecom_webhook":
        return resp.get("errcode") == 0
    if channel_type == "pushplus":
        return resp.get("code") == 200
    return True


def send_message(title: str, content: str, level: str = "regular",
                 config_path: Path | str | None = None) -> dict[str, bool]:
    """模块级便捷入口（CLI 用）。"""
    return Notifier(config_path).send(title, content, level)
