# -*- coding: utf-8 -*-
"""
SecOps Copilot JSON fixtures 生成器（V1.3 基线）

- 完全确定性：按场景模板生成，不使用随机数，重复运行结果一致。
- 输出：backend/src/main/resources/mock/*.json
- 运行：python scripts/generate_fixtures.py

约束依据：
  PRD.md 3.2 / 4.1、DATABASE.md 5.1 / 6 / 7、ARCHITECTURE.md 6.1
  - 模拟 IP 使用文档示例地址段，全部数据为虚构。
  - 一个评估 scenarioId 对应且只对应一个 Incident：
    每个场景使用不同 sourceIp（不同聚合键）且时间窗口互不重叠；
    组内所有告警共享聚合键并落在同一个 5 分钟窗口内。
  - scenarioId 只用于评估反查标签，不参与聚合、Prompt 或工具决策。
"""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

TZ = timezone(timedelta(hours=8))
BASE = datetime(2026, 11, 18, 9, 0, 0, tzinfo=TZ)
OUT = Path(__file__).resolve().parent.parent / "backend" / "src" / "main" / "resources" / "mock"


def ts(dt: datetime) -> str:
    """ISO 8601，带时区。"""
    return dt.isoformat()


class IdGen:
    def __init__(self) -> None:
        self.n = 0

    def next(self) -> str:
        self.n += 1
        return f"ALERT-{self.n:04d}"


IDS = IdGen()

# ---------------------------------------------------------------------------
# SSH 评估场景：8 个，每个场景独占一个聚合键与一个时间窗口
# ---------------------------------------------------------------------------
SSH_SCENARIOS = [
    {
        "scenarioId": "SCENARIO-SSH-001",
        "sourceIp": "203.0.113.10", "targetAsset": "server-01", "targetIp": "10.0.0.10",
        "offsetMin": 0, "count": 32, "failed": 30, "success": 2,
        "users": ["root", "admin", "test", "guest", "oracle", "postgres", "deploy", "backup"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "ATTACK", "tools": ["query_login_history"],
        "note": "短时间高频登录失败且失败后出现成功登录，目标资产公网暴露",
    },
    {
        "scenarioId": "SCENARIO-SSH-002",
        "sourceIp": "203.0.113.11", "targetAsset": "server-02", "targetIp": "10.0.0.11",
        "offsetMin": 10, "count": 12, "failed": 12, "success": 0,
        "users": ["root", "admin"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "ATTACK", "tools": ["query_login_history"],
        "note": "持续登录失败，无明显成功登录，资产公网暴露",
    },
    {
        "scenarioId": "SCENARIO-SSH-003",
        "sourceIp": "203.0.113.12", "targetAsset": "server-01", "targetIp": "10.0.0.10",
        "offsetMin": 20, "count": 8, "failed": 2, "success": 6,
        "users": ["ops-admin"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "BENIGN", "tools": [],
        "note": "授权运维窗口内的少量输错后正常登录，存在已审批运维单",
    },
    {
        "scenarioId": "SCENARIO-SSH-004",
        "sourceIp": "203.0.113.13", "targetAsset": "server-03", "targetIp": "10.0.0.12",
        "offsetMin": 30, "count": 6, "failed": 2, "success": 4,
        "users": ["dba"],
        "order": "SUCCESS_THEN_FAIL",
        "label": "BENIGN", "tools": [],
        "note": "少量密码输错，无失败后成功序列，资产未公网暴露",
    },
    {
        "scenarioId": "SCENARIO-SSH-005",
        "sourceIp": None, "targetAsset": "server-02", "targetIp": "10.0.0.11",
        "offsetMin": 40, "count": 1, "failed": 1, "success": 0,
        "users": ["root"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "UNCERTAIN", "tools": [],
        "note": "缺少来源 IP，聚合关键字段不完整，单独成事件",
    },
    {
        "scenarioId": "SCENARIO-SSH-006",
        "sourceIp": "198.51.100.20", "targetAsset": "server-01", "targetIp": "10.0.0.10",
        "offsetMin": 50, "count": 10, "failed": 10, "success": 0,
        "users": ["root", "admin", "test", "guest", "oracle"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "ATTACK", "tools": ["query_login_history"],
        "note": "多用户名尝试，公网暴露资产，未出现成功登录",
    },
    {
        "scenarioId": "SCENARIO-SSH-007",
        "sourceIp": "198.51.100.21", "targetAsset": "server-02", "targetIp": "10.0.0.11",
        "offsetMin": 60, "count": 6, "failed": 5, "success": 1,
        "users": ["root", "admin", "test", "guest", "deploy"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "UNCERTAIN", "tools": [],
        "note": "失败后出现成功登录但证据有限，需人工复核",
    },
    {
        "scenarioId": "SCENARIO-SSH-008",
        "sourceIp": "198.51.100.22", "targetAsset": "server-03", "targetIp": "10.0.0.12",
        "offsetMin": 70, "count": 5, "failed": 5, "success": 0,
        "users": ["dba"],
        "order": "FAIL_THEN_SUCCESS",
        "label": "BENIGN", "tools": [],
        "note": "单一用户少量失败，非公网资产，历史登录可信",
    },
]


def ssh_severity(scenario: dict) -> str:
    failed = scenario["failed"]
    if failed >= 30:
        return "CRITICAL"
    if failed >= 10:
        return "HIGH"
    if scenario["label"] == "ATTACK":
        return "HIGH"
    if scenario["label"] == "UNCERTAIN":
        return "MEDIUM"
    return "LOW"


def ssh_actions(scenario: dict) -> list[str]:
    failed = ["LOGIN_FAILED"] * scenario["failed"]
    success = ["LOGIN_SUCCESS"] * scenario["success"]
    if scenario["order"] == "SUCCESS_THEN_FAIL":
        return success + failed
    return failed + success


def ssh_raw_log(action: str, username: str, source_ip: str | None, port: int) -> str:
    ip = source_ip or "UNKNOWN"
    if action == "LOGIN_FAILED":
        return f"Failed password for {username} from {ip} port {port} ssh2"
    return f"Accepted password for {username} from {ip} port {port} ssh2"


def build_ssh_scenario_alerts(scenario: dict) -> list[dict]:
    alerts: list[dict] = []
    start = BASE + timedelta(minutes=scenario["offsetMin"])
    actions = ssh_actions(scenario)
    count = len(actions)
    interval = 290.0 / max(count - 1, 1)
    severity = ssh_severity(scenario)
    users = scenario["users"]

    for idx, action in enumerate(actions):
        timestamp = start + timedelta(seconds=round(idx * interval))
        alert: dict = {
            "alertId": IDS.next(),
            "scenarioId": scenario["scenarioId"],
            "alertType": "SSH_BRUTE_FORCE",
            "category": "AUTHENTICATION",
            "timestamp": ts(timestamp),
            "targetIp": scenario["targetIp"],
            "targetAsset": scenario["targetAsset"],
            "severity": severity,
            "sourceSystem": "SIMULATED_SIEM",
            "attributes": {
                "protocol": "SSH",
                "username": users[idx % len(users)],
                "action": action,
            },
            "rawLog": ssh_raw_log(action, users[idx % len(users)], scenario["sourceIp"], 50000 + idx),
        }
        # 聚合关键字段缺失时真正省略该键，不写 null
        if scenario["sourceIp"] is not None:
            alert["sourceIp"] = scenario["sourceIp"]
        alerts.append(alert)
    return alerts


# ---------------------------------------------------------------------------
# 聚合关键字段缺失的展示告警：不带 scenarioId，不纳入场景级评估
# 每条因缺字段单独成事件（PRD 4.1）
# ---------------------------------------------------------------------------
def build_incomplete_alerts() -> list[dict]:
    specs = [
        ("sourceIp", "198.51.100.23", "server-01", "10.0.0.10"),
        ("targetAsset", "203.0.113.20", None, "10.0.0.11"),
        ("targetAsset", "203.0.113.21", None, "10.0.0.12"),
        ("protocol", "192.0.2.20", "server-02", "10.0.0.11"),
        ("protocol", "192.0.2.21", "server-03", "10.0.0.12"),
    ]
    alerts: list[dict] = []
    for idx, (missing, ip, asset, target_ip) in enumerate(specs):
        timestamp = BASE + timedelta(minutes=80 + idx)
        alert: dict = {
            "alertId": IDS.next(),
            "alertType": "SSH_BRUTE_FORCE",
            "category": "AUTHENTICATION",
            "timestamp": ts(timestamp),
            "sourceIp": ip,
            "targetIp": target_ip,
            "severity": "MEDIUM",
            "sourceSystem": "SIMULATED_SIEM",
            "attributes": {"protocol": "SSH", "username": "root", "action": "LOGIN_FAILED"},
            "rawLog": f"Failed password for root from {ip} port 51000 ssh2 (incomplete telemetry)",
        }
        if asset is not None:
            alert["targetAsset"] = asset
        if missing == "protocol":
            alert["attributes"] = {"username": "root", "action": "LOGIN_FAILED"}
        if missing == "sourceIp":
            alert.pop("sourceIp", None)
        alerts.append(alert)
    return alerts


# ---------------------------------------------------------------------------
# 其他类型：仅用于展示与筛选，不注册策略，不生成 Incident
# ---------------------------------------------------------------------------
OTHER_SPECS = [
    {
        "alertType": "PORT_SCAN", "category": "NETWORK", "count": 15,
        "targetAsset": "fw-01", "targetIp": "10.0.0.1",
        "sourcePrefix": "192.0.2", "severities": ["HIGH", "MEDIUM", "LOW"],
        "attributes": {"protocol": "TCP", "scannedPorts": "22,80,443,8080"},
        "rawLog": "TCP SYN scan detected from {ip} to 10.0.0.1 on multiple ports",
    },
    {
        "alertType": "SQL_INJECTION", "category": "WEB_ATTACK", "count": 15,
        "targetAsset": "web-01", "targetIp": "10.0.0.20",
        "sourcePrefix": "198.51.100", "severities": ["CRITICAL", "HIGH", "MEDIUM"],
        "attributes": {"protocol": "HTTP", "payload": "' OR 1=1--", "uri": "/api/login"},
        "rawLog": "WAF blocked SQL injection pattern from {ip} on /api/login",
    },
    {
        "alertType": "MALWARE_ACTIVITY", "category": "HOST_SECURITY", "count": 10,
        "targetAsset": "pc-023", "targetIp": "10.0.0.23",
        "sourcePrefix": "203.0.113", "severities": ["CRITICAL", "HIGH"],
        "attributes": {"processName": "suspicious.exe", "action": "DETECTED"},
        "rawLog": "EDR detected suspicious process on {asset}: suspicious.exe",
    },
]


def build_other_alerts() -> list[dict]:
    alerts: list[dict] = []
    minute_cursor = 90
    for spec in OTHER_SPECS:
        for idx in range(spec["count"]):
            ip = f"{spec['sourcePrefix']}.{10 + idx}"
            timestamp = BASE + timedelta(minutes=minute_cursor + idx)
            alerts.append({
                "alertId": IDS.next(),
                "alertType": spec["alertType"],
                "category": spec["category"],
                "timestamp": ts(timestamp),
                "sourceIp": ip,
                "targetIp": spec["targetIp"],
                "targetAsset": spec["targetAsset"],
                "severity": spec["severities"][idx % len(spec["severities"])],
                "sourceSystem": "SIMULATED_SIEM",
                "attributes": dict(spec["attributes"]),
                "rawLog": spec["rawLog"].format(ip=ip, asset=spec["targetAsset"]),
            })
        minute_cursor += spec["count"]
    return alerts


# ---------------------------------------------------------------------------
# 资产、历史登录、授权运维、Ground Truth
# ---------------------------------------------------------------------------
ASSETS = [
    {"assetId": "ASSET-01", "assetName": "server-01", "ipAddress": "10.0.0.10",
     "publicExposure": True, "assetCriticality": "HIGH", "owner": "ops-team"},
    {"assetId": "ASSET-02", "assetName": "server-02", "ipAddress": "10.0.0.11",
     "publicExposure": True, "assetCriticality": "MEDIUM", "owner": "ops-team"},
    {"assetId": "ASSET-03", "assetName": "server-03", "ipAddress": "10.0.0.12",
     "publicExposure": False, "assetCriticality": "HIGH", "owner": "db-team"},
    {"assetId": "ASSET-04", "assetName": "fw-01", "ipAddress": "10.0.0.1",
     "publicExposure": True, "assetCriticality": "MEDIUM", "owner": "net-team"},
    {"assetId": "ASSET-05", "assetName": "web-01", "ipAddress": "10.0.0.20",
     "publicExposure": True, "assetCriticality": "HIGH", "owner": "web-team"},
    {"assetId": "ASSET-06", "assetName": "pc-023", "ipAddress": "10.0.0.23",
     "publicExposure": False, "assetCriticality": "LOW", "owner": "office"},
]


def build_login_history() -> list[dict]:
    """所有记录时间早于任一 SSH 场景 firstSeen，供 [firstSeen-lookback, firstSeen) 查询。"""
    records: list[dict] = []
    seeds = [
        ("203.0.113.10", "server-01", "root", "LOGIN_FAILED", 120),
        ("203.0.113.10", "server-01", "admin", "LOGIN_FAILED", 96),
        ("203.0.113.10", "server-01", "root", "LOGIN_FAILED", 74),
        ("203.0.113.11", "server-02", "root", "LOGIN_FAILED", 110),
        ("203.0.113.12", "server-01", "ops-admin", "LOGIN_SUCCESS", 150),
        ("203.0.113.12", "server-01", "ops-admin", "LOGIN_SUCCESS", 100),
        ("203.0.113.12", "server-01", "ops-admin", "LOGIN_SUCCESS", 55),
        ("203.0.113.13", "server-03", "dba", "LOGIN_SUCCESS", 130),
        ("198.51.100.20", "server-01", "root", "LOGIN_FAILED", 88),
        ("198.51.100.21", "server-02", "deploy", "LOGIN_SUCCESS", 66),
        ("198.51.100.22", "server-03", "dba", "LOGIN_SUCCESS", 140),
        ("198.51.100.22", "server-03", "dba", "LOGIN_SUCCESS", 45),
    ]
    for idx, (ip, asset, user, action, minutes_before) in enumerate(seeds, start=1):
        records.append({
            "recordId": f"LOG-{idx:04d}",
            "sourceIp": ip,
            "targetAsset": asset,
            "username": user,
            "action": action,
            "timestamp": ts(BASE - timedelta(minutes=minutes_before)),
        })
    return records


def build_authorized_operations() -> list[dict]:
    # CHG-001 覆盖 SCENARIO-SSH-003 的事件窗口 [firstSeen-5min, lastSeen+5min]
    return [
        {
            "ticketId": "CHG-20261118-001",
            "targetAsset": "server-01",
            "operator": "ops-admin",
            "startTime": ts(BASE + timedelta(minutes=18)),
            "endTime": ts(BASE + timedelta(minutes=25)),
            "status": "APPROVED",
            "description": "计划内 SSH 证书轮换与巡检",
        },
        {
            "ticketId": "CHG-20261118-002",
            "targetAsset": "server-02",
            "operator": "net-admin",
            "startTime": ts(BASE + timedelta(minutes=58)),
            "endTime": ts(BASE + timedelta(minutes=64)),
            "status": "CANCELLED",
            "description": "已取消的防火墙维护窗口",
        },
        {
            "ticketId": "CHG-20261118-003",
            "targetAsset": "web-01",
            "operator": "web-admin",
            "startTime": ts(BASE + timedelta(minutes=100)),
            "endTime": ts(BASE + timedelta(minutes=115)),
            "status": "COMPLETED",
            "description": "Web 应用版本发布",
        },
    ]


def build_ground_truth() -> list[dict]:
    return [
        {
            "scenarioId": s["scenarioId"],
            "expectedLabel": s["label"],
            "expectedToolPlan": list(s["tools"]),
            "notes": s["note"],
        }
        for s in SSH_SCENARIOS
    ]


def write_json(name: str, data) -> None:
    path = OUT / name
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    alerts: list[dict] = []
    for scenario in SSH_SCENARIOS:
        alerts.extend(build_ssh_scenario_alerts(scenario))
    alerts.extend(build_incomplete_alerts())
    alerts.extend(build_other_alerts())

    write_json("alerts.json", alerts)
    write_json("assets.json", ASSETS)
    write_json("login-history.json", build_login_history())
    write_json("authorized-operations.json", build_authorized_operations())
    write_json("ground-truth.json", build_ground_truth())

    total_ssh = sum(1 for a in alerts if a["alertType"] == "SSH_BRUTE_FORCE")
    print(f"alerts total          : {len(alerts)}")
    print(f"  SSH_BRUTE_FORCE     : {total_ssh}")
    print(f"  unsupported (others): {len(alerts) - total_ssh}")
    print(f"  evaluation scenarios: {len(SSH_SCENARIOS)}")
    print(f"  scenario-bound SSH  : {sum(1 for a in alerts if a.get('scenarioId'))}")
    print(f"assets                : {len(ASSETS)}")
    print(f"login history         : {len(build_login_history())}")
    print(f"authorized operations : {len(build_authorized_operations())}")
    print(f"ground truth          : {len(build_ground_truth())}")
    print(f"output dir            : {OUT}")


if __name__ == "__main__":
    main()
