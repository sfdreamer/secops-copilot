# API.md — SecOps Copilot

> **API 版本：** V1.3  
> **对应文档：** `PRD.md`、`ARCHITECTURE.md`、`DATABASE.md` V1.3  
> **基础路径：** `/api`  
> **协议：** HTTP + JSON  
> **部署：** 本地前后端分离单体应用

## 1. 通用约定

### 1.1 基础约定

| 项目 | 约定 |
|---|---|
| 后端默认地址 | `http://localhost:8080` |
| Base Path | `/api` |
| Content-Type | `application/json; charset=utf-8` |
| 时间 | ISO 8601，必须包含时区 |
| 枚举 | 大写下划线 |
| 分页 | `page` 从 1 开始；`pageSize` 默认 20，最大 100 |

模型 API Key 只由后端读取环境变量，前端不得直接请求 DashScope。

### 1.2 统一成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {}
}
```

### 1.3 统一失败响应

```json
{
  "success": false,
  "message": "当前告警类型暂未启用分析策略",
  "error": {
    "code": "STRATEGY_NOT_SUPPORTED",
    "details": null
  },
  "data": null
}
```

### 1.4 HTTP 状态码与错误码

| HTTP | 错误码 | 场景 |
|---:|---|---|
| 400 | `INVALID_ARGUMENT` / `VALIDATION_ERROR` | 参数或字段校验失败 |
| 404 | `ALERT_NOT_FOUND` / `INCIDENT_NOT_FOUND` | 资源不存在 |
| 409 | `ANALYSIS_IN_PROGRESS` / `INVALID_INCIDENT_STATUS` | 并发研判或状态冲突 |
| 422 | `STRATEGY_NOT_SUPPORTED` | 当前告警类型没有分析策略 |
| 500 | `INTERNAL_ERROR` | 未预期的后端错误 |
| 503 | `MODEL_SERVICE_ERROR` / `AGENT_EXECUTION_ERROR` / `AI_OUTPUT_INVALID` | 模型不可用、Agent 最终执行失败或输出无法解析/校验 |

单个工具调用失败不一定导致整个研判失败。如果 Agent 基于已有证据仍能完成有效研判，接口返回 200，并在 `toolCallTraces` 和 `uncertainties` 中体现失败与信息缺口。错误响应不得包含 API Key、堆栈、完整 Prompt 或敏感凭据。

## 2. 枚举

字段枚举以 `DATABASE.md` 第 4 节为准。事件列表筛选使用 `ruleRiskLevel`，明确表示筛选的是规则风险等级，而不是 AI `riskLevel`。

## 3. 公共接口列表

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/dashboard` | 安全态势总览 |
| GET | `/api/alerts` | 原始告警列表 |
| GET | `/api/alerts/{id}` | 原始告警详情 |
| GET | `/api/incidents` | 聚合事件列表 |
| GET | `/api/incidents/{id}` | 事件详情 |
| POST | `/api/incidents/{id}/analyze` | 触发 Agent 研判 |
| POST | `/api/incidents/{id}/review` | 提交人工审核 |
| GET | `/api/reviews` | 审核记录列表 |
| GET | `/api/evaluation` | 全链路评估结果 |

Function Calling 工具是后端内部能力，不暴露为公共 HTTP API。

## 4. 健康检查

### 请求

```http
GET /api/health
```

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "status": "UP",
    "modelConfigured": true,
    "mockDataLoaded": true
  }
}
```

`modelConfigured` 只表示是否配置模型服务，不表示模型当前一定可用。不得返回密钥或环境变量原文。

## 5. Dashboard

### 请求

```http
GET /api/dashboard
```

### 成功响应结构

```json
{
  "success": true,
  "message": "success",
  "data": {
    "alertTotal": 120,
    "categoryDistribution": {
      "AUTHENTICATION": 80,
      "NETWORK": 15,
      "WEB_ATTACK": 15,
      "HOST_SECURITY": 10,
      "DATA_SECURITY": 0,
      "UNKNOWN": 0
    },
    "alertTypeDistribution": {
      "SSH_BRUTE_FORCE": 80,
      "PORT_SCAN": 15,
      "SQL_INJECTION": 15,
      "MALWARE_ACTIVITY": 10,
      "UNKNOWN": 0
    },
    "supportedIncidentTotal": 8,
    "highRiskIncidentTotal": 3,
    "pendingReviewTotal": 2,
    "unsupportedAlertTotal": 40,
    "riskDistribution": {
      "CRITICAL": 1,
      "HIGH": 2,
      "MEDIUM": 3,
      "LOW": 2
    },
    "recentIncidents": []
  }
}
```

所有数字均为结构示例，实际必须由当前数据计算。

- `categoryDistribution`：按告警大类统计全部原始告警。
- `alertTypeDistribution`：按具体告警类型统计全部原始告警。
- `supportedIncidentTotal`、风险分布：只统计已注册策略生成的事件。
- `highRiskIncidentTotal`：规则风险等级为 `HIGH` 或 `CRITICAL` 的事件数。
- `pendingReviewTotal`：当前状态为 `PENDING_REVIEW` 的事件数。
- `unsupportedAlertTotal`：未注册策略的原始告警数。

## 6. 原始告警列表

### 请求

```http
GET /api/alerts?page=1&pageSize=20&alertType=SSH_BRUTE_FORCE&severity=HIGH
```

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `page` | Integer | 否 | 默认 1 |
| `pageSize` | Integer | 否 | 默认 20，最大 100 |
| `keyword` | String | 否 | 匹配 alertId、sourceIp、targetAsset |
| `alertType` | String | 否 | 具体告警类型 |
| `category` | String | 否 | 告警类别 |
| `severity` | String | 否 | 原始告警等级 |
| `sourceIp` | String | 否 | 来源 IP 精确筛选 |
| `targetAsset` | String | 否 | 目标资产筛选 |

多个筛选条件采用 AND 关系；`keyword` 在指定字段内采用包含匹配。

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "total": 2,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "alertId": "ALERT-0001",
        "alertType": "SSH_BRUTE_FORCE",
        "category": "AUTHENTICATION",
        "timestamp": "2026-11-18T14:01:02+08:00",
        "sourceIp": "203.0.113.10",
        "targetIp": "10.0.0.10",
        "targetAsset": "server-01",
        "severity": "HIGH",
        "sourceSystem": "SIMULATED_SIEM",
        "attributes": {
          "protocol": "SSH",
          "username": "root",
          "action": "LOGIN_FAILED"
        },
        "strategySupport": "SUPPORTED"
      }
    ]
  }
}
```

列表不返回 `rawLog`，详情接口返回。`strategySupport` 由策略注册表动态计算。`scenarioId` 是内部评估关联字段，不向前端暴露；Ground Truth 不得返回。

## 7. 原始告警详情

### 请求

```http
GET /api/alerts/{id}
```

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "alert": {
      "alertId": "ALERT-0001",
      "alertType": "SSH_BRUTE_FORCE",
      "category": "AUTHENTICATION",
      "timestamp": "2026-11-18T14:01:02+08:00",
      "sourceIp": "203.0.113.10",
      "targetIp": "10.0.0.10",
      "targetAsset": "server-01",
      "severity": "HIGH",
      "sourceSystem": "SIMULATED_SIEM",
      "attributes": {
        "protocol": "SSH",
        "username": "root",
        "action": "LOGIN_FAILED"
      },
      "rawLog": "Failed password for root from 203.0.113.10"
    },
    "strategySupport": "SUPPORTED"
  }
}
```

告警不存在返回 404 `ALERT_NOT_FOUND`。未支持策略的告警仍可查看，但前端不得提供研判入口。普通业务响应不返回 `scenarioId` 或 Ground Truth。

## 8. 安全事件列表

### 请求

```http
GET /api/incidents?page=1&pageSize=20&ruleRiskLevel=CRITICAL&status=PENDING_REVIEW
```

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `page` / `pageSize` | Integer | 否 | 分页，规则同上 |
| `keyword` | String | 否 | 匹配 incidentId、sourceIp、targetAsset |
| `alertType` | String | 否 | 告警类型 |
| `ruleRiskLevel` | String | 否 | 规则风险等级 |
| `status` | String | 否 | Incident 业务状态 |
| `sourceIp` | String | 否 | 来源 IP |
| `targetAsset` | String | 否 | 目标资产 |

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "incidentId": "INC-20261118-001",
        "alertType": "SSH_BRUTE_FORCE",
        "category": "AUTHENTICATION",
        "sourceIp": "203.0.113.10",
        "targetAsset": "server-01",
        "firstSeen": "2026-11-18T14:01:02+08:00",
        "lastSeen": "2026-11-18T14:05:58+08:00",
        "alertCount": 120,
        "ruleRiskScore": 95,
        "ruleRiskLevel": "CRITICAL",
        "analysisStatus": "NOT_STARTED",
        "status": "PENDING_ANALYSIS"
      }
    ]
  }
}
```

本接口只返回已注册策略生成的 Incident。`ruleRiskLevel` 筛选不得使用 AI `riskLevel`。

## 9. 安全事件详情

### 请求

```http
GET /api/incidents/{id}
```

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "incident": {
      "incidentId": "INC-20261118-001",
      "alertType": "SSH_BRUTE_FORCE",
      "category": "AUTHENTICATION",
      "sourceIp": "203.0.113.10",
      "targetIp": "10.0.0.10",
      "targetAsset": "server-01",
      "firstSeen": "2026-11-18T14:01:02+08:00",
      "lastSeen": "2026-11-18T14:05:58+08:00",
      "alertIds": ["ALERT-0001", "ALERT-0002"],
      "alertCount": 2,
      "analysisStatus": "NOT_STARTED",
      "status": "PENDING_ANALYSIS"
    },
    "features": {
      "failedAttempts": 2,
      "successfulAttempts": 0,
      "distinctUserCount": 2,
      "windowSeconds": 2,
      "windowMinutes": 0.0333,
      "failureRate": 60.0,
      "successAfterFailure": false,
      "publicExposure": true,
      "assetCriticality": "HIGH"
    },
    "riskAssessment": {
      "score": 10,
      "level": "LOW",
      "matchedRules": [
        {"ruleCode": "PUBLIC_EXPOSURE", "description": "目标资产公网暴露", "points": 10, "supportingFacts": {"publicExposure": true}}
      ],
      "reasons": ["目标资产公网暴露"]
    },
    "relatedAlerts": [
      {"alertId": "ALERT-0001", "timestamp": "2026-11-18T14:01:02+08:00", "attributes": {"protocol": "SSH", "username": "root", "action": "LOGIN_FAILED"}},
      {"alertId": "ALERT-0002", "timestamp": "2026-11-18T14:01:04+08:00", "attributes": {"protocol": "SSH", "username": "admin", "action": "LOGIN_FAILED"}}
    ],
    "latestAnalysis": null,
    "toolCallTraces": []
  }
}
```

`relatedAlerts` 返回关联原始告警；`latestAnalysis` 无研判时为 null；`toolCallTraces` 返回最近一次研判对应的轨迹。不得返回 Ground Truth。

## 10. 触发 Agent 研判

### 请求

```http
POST /api/incidents/{id}/analyze
```

本期无请求 Body。事件上下文由后端根据 Incident、关联 Alert、特征和规则评分构建。

### 执行流程

1. 查询 Incident 和策略。
2. 检查策略支持状态及事件当前状态。
3. 原子地将事件置为 `ANALYZING/RUNNING`，阻止并发研判。
4. 构建当前 Incident 的 AI 上下文并调用 Agent。
5. Agent 按需请求白名单只读工具；后端校验参数、Incident 绑定和查询范围。
6. 执行工具并记录 ToolCallTrace；工具结果回填模型。
7. 解析最终 JSON，校验字段、枚举和证据引用。
8. 保存 AIAnalysis 和 ToolCallTrace，更新事件状态。

### 成功响应

```json
{
  "success": true,
  "message": "analysis completed",
  "data": {
    "incidentId": "INC-20261118-002",
    "analysisStatus": "SUCCEEDED",
    "status": "PENDING_REVIEW",
    "analysis": {
      "analysisId": "ANL-example",
      "verdict": "SUSPICIOUS",
      "summary": "短时间内出现大量 SSH 登录失败，存在暴力破解迹象。",
      "attackType": "SSH_BRUTE_FORCE",
      "riskLevel": "CRITICAL",
      "reasoningSummary": "结合登录行为和历史记录，发现异常登录特征。",
      "evidence": [
        {
          "sourceType": "ALERT",
          "alertIds": ["ALERT-0101"],
          "toolCallIds": [],
          "fact": "短时间内出现多次登录失败",
          "explanation": "符合高频密码尝试特征"
        }
      ],
      "recommendations": ["核实成功登录账号是否属于授权运维人员。"],
      "uncertainties": [],
      "modelName": "configured-model",
      "promptVersion": "ssh-triage-v4",
      "executionMode": "TOOL_ENABLED",
      "validationStatus": "VALID",
      "durationMs": 4200,
      "analyzedAt": "2026-11-18T14:10:00+08:00"
    },
    "toolCallTraces": [
      {
        "toolCallId": "TOOL-example",
        "toolName": "query_login_history",
        "argumentsSummary": {
          "sourceIp": "203.0.113.10",
          "targetAsset": "server-01",
          "lookbackHours": 168
        },
        "executionStatus": "SUCCESS",
        "resultSummary": {
          "knownSource": false,
          "successfulLoginCount": 0
        },
        "durationMs": 12
      }
    ],
    "agentMetrics": {
      "toolCallCount": 1,
      "agentDurationMs": 4200
    }
  }
}
```

字段值仅为结构示例。实际响应必须来自本次模型调用与工具执行。

### 执行限制与状态

- 仅允许从 `PENDING_ANALYSIS`、`ANALYSIS_FAILED`、`INVESTIGATING` 触发研判；`ANALYZING` 返回 `ANALYSIS_IN_PROGRESS`，`PENDING_REVIEW` 本期不重复研判。
- 最多 2 轮工具调用循环、最多 4 次工具调用。
- 单工具超时建议 3000ms，Agent 总耗时上限建议 45000ms。
- Agent 无需工具时可直接输出结果，`executionMode=TOOL_ENABLED`。
- Function Calling 不可用但普通结构化生成可用时，可无工具降级，`executionMode=NO_TOOL_FALLBACK`，并在 `uncertainties` 中说明。
- 工具单次失败但 Agent 最终输出有效报告：HTTP 200，保留失败轨迹并说明信息缺口。
- 模型不可用、Agent 最终失败或结构化输出无法解析：返回 503；事件状态为 `ANALYSIS_FAILED`，`analysisStatus=FAILED`，保留规则结果。

### 失败码

| HTTP | 错误码 | 场景 |
|---:|---|---|
| 404 | `INCIDENT_NOT_FOUND` | 事件不存在 |
| 422 | `STRATEGY_NOT_SUPPORTED` | 没有对应策略 |
| 409 | `ANALYSIS_IN_PROGRESS` | 事件正在研判 |
| 409 | `INVALID_INCIDENT_STATUS` | 当前事件状态不允许研判 |
| 503 | `MODEL_SERVICE_ERROR` | 模型服务不可用 |
| 503 | `AGENT_EXECUTION_ERROR` | Agent 最终执行失败 |
| 503 | `AI_OUTPUT_INVALID` | 最终输出无法解析或核心字段校验失败 |

本接口为同步接口，前端请求超时应大于后端 Agent 总耗时上限。

## 11. 提交人工审核

### 请求

```http
POST /api/incidents/{id}/review
Content-Type: application/json
```

```json
{
  "reviewResult": "CONFIRMED_THREAT",
  "reviewer": "analyst",
  "remark": "已核查，存在异常登录行为。"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `reviewResult` | String | 是 | `CONFIRMED_THREAT` / `FALSE_POSITIVE` / `INVESTIGATING` |
| `reviewer` | String | 是 | Demo 审核人标识 |
| `remark` | String | 否 | 审核备注 |

允许从 `PENDING_ANALYSIS`、`PENDING_REVIEW`、`ANALYSIS_FAILED`、`INVESTIGATING` 提交。`ANALYZING` 不允许审核；`CONFIRMED_THREAT` 和 `FALSE_POSITIVE` 为终态，不允许直接修改。

### 成功响应

```json
{
  "success": true,
  "message": "review submitted",
  "data": {
    "reviewId": "REV-example",
    "incidentId": "INC-20261118-002",
    "analysisId": "ANL-example",
    "aiVerdict": "SUSPICIOUS",
    "reviewResult": "CONFIRMED_THREAT",
    "reviewer": "analyst",
    "remark": "已核查，存在异常登录行为。",
    "reviewTime": "2026-11-18T14:30:00+08:00",
    "incidentStatus": "CONFIRMED_THREAT"
  }
}
```

若此前没有成功 AI 研判，`analysisId` 和 `aiVerdict` 返回 null。审核状态更新与 ReviewRecord 追加保存必须在同一业务操作中完成。Demo 不实现真实身份认证。

## 12. 审核记录列表

### 请求

```http
GET /api/reviews?page=1&pageSize=20&incidentId=INC-20261118-001&reviewResult=CONFIRMED_THREAT
```

支持 `page`、`pageSize`、`incidentId`、`reviewResult`。

### 成功响应

```json
{
  "success": true,
  "message": "success",
  "data": {
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "reviewId": "REV-example",
        "incidentId": "INC-20261118-002",
        "analysisId": "ANL-example",
        "aiVerdict": "SUSPICIOUS",
        "reviewResult": "CONFIRMED_THREAT",
        "reviewer": "analyst",
        "remark": "已核查，存在异常登录行为。",
        "reviewTime": "2026-11-18T14:30:00+08:00"
      }
    ]
  }
}
```

## 13. 全链路评估

### 请求

```http
GET /api/evaluation
```

### 成功响应结构

```json
{
  "success": true,
  "message": "success",
  "data": {
    "meta": {
      "datasetVersion": "demo-v1",
      "sampleCount": 40,
      "evaluatedAt": "2026-11-18T15:00:00+08:00",
      "modelName": "configured-model",
      "promptVersion": "ssh-triage-v4"
    },
    "dataQuality": {
      "inputAlertCount": 120,
      "parsedAlertCount": 120,
      "parseSuccessRate": 1.0,
      "requiredFieldCompleteness": 1.0
    },
    "aggregation": {
      "rawSshAlertCount": 80,
      "incidentCount": 8,
      "compressionRate": 0.9,
      "precision": null,
      "recall": null
    },
    "ruleEvaluation": {
      "sampleCount": 40,
      "uncertainSampleCount": 0,
      "precision": null,
      "recall": null,
      "f1": null
    },
    "aiEvaluation": {
      "sampleCount": 0,
      "accuracy": null,
      "precision": null,
      "recall": null,
      "macroF1": null,
      "confusionMatrix": {}
    },
    "evidenceEvaluation": {
      "traceabilityRate": null,
      "traceabilityStatus": "NOT_COMPUTABLE",
      "semanticSupportRate": null,
      "semanticSupportStatus": "NOT_EVALUATED"
    },
    "agentEvaluation": {
      "toolCallCount": 0,
      "toolSuccessRate": null,
      "parameterValidityRate": null,
      "toolPlanPrecision": null,
      "toolPlanRecall": null,
      "toolPlanF1": null,
      "averageToolCalls": null,
      "averageDurationMs": null
    },
    "businessEvaluation": {
      "reviewSampleCount": 0,
      "adoptionRate": null,
      "correctionRate": null,
      "manualAverageDurationMs": null,
      "aiAssistedAverageDurationMs": null,
      "efficiencyImprovementRate": null,
      "measurementStatus": "NOT_MEASURED"
    }
  }
}
```

以上数值为结构示例，实际结果必须由数据计算。

### 指标口径

- `aggregation.compressionRate = (参与聚合的 SSH 原始告警数 - SSH Incident 数) / 参与聚合的 SSH 原始告警数`；分母为 0 时返回 null。
- 聚合 Precision/Recall 只有在准备独立关联标注时才计算。
- 规则评估以 `ruleRiskScore >= 50` 作为 Demo 高风险阈值，与 `ATTACK` 标签比较；`UNCERTAIN` 不纳入二分类，`sampleCount` 只含二分类有效样本，`uncertainSampleCount` 单独统计 `UNCERTAIN` 样本数（PRD 6）。
- AI 评估只统计有成功且有效 AIAnalysis、并能匹配唯一 Ground Truth 的事件。未实际运行 Agent 的样本不得计入 AI 准确率。
- AI 标签映射：`SUSPICIOUS -> ATTACK`、`LIKELY_BENIGN -> BENIGN`、`NEEDS_REVIEW -> UNCERTAIN`。
- 工具计划评估按每个场景实际调用工具集合与 `expectedToolPlan` 比较，累计计算工具集合 Precision/Recall/F1；多余调用计为 FP，漏调计为 FN。没有有效计划样本时返回 null。
- 工具成功率 = 成功工具调用数 / 实际工具调用总数；参数有效率 = 参数校验通过数 / 实际工具调用总数。
- 证据可追溯率本期**不可计算**：`AgentResultValidator` 在保存前剔除无效引用且未持久化「过滤前引用总数」，分母无法还原；只遍历已保存的 `evidence()` 会恒为 1.0，属于失真指标，因此返回 `traceabilityRate=null` 且 `traceabilityStatus=NOT_COMPUTABLE`。语义支持率需要人工抽样，未抽样时返回 null 并标记 `NOT_EVALUATED`。
- 采纳率/修正率需要 AI 结果与人工审核结果配对：`SUSPICIOUS -> CONFIRMED_THREAT`、`LIKELY_BENIGN -> FALSE_POSITIVE`、`NEEDS_REVIEW -> INVESTIGATING`；映射一致计为采纳，不一致计为修正。效率提升率需要真实计时数据，本期没有计时数据时返回 null 和 `NOT_MEASURED`。
- `sampleCount` 必须说明对应指标的有效样本数。评估接口只读取实际运行产物，不自动触发 LLM。

## 14. Function Calling 内部工具契约

工具由后端白名单注册，不作为公共 HTTP API 暴露。模型返回的函数名和参数必须由后端校验后执行。

### 14.1 `query_login_history`

用途：查询当前事件来源 IP 在目标资产上的历史登录行为。

参数 Schema：

```json
{
  "type": "object",
  "properties": {
    "sourceIp": {"type": "string", "description": "登录来源 IP，必须与当前事件一致"},
    "targetAsset": {"type": "string", "description": "目标资产，必须与当前事件一致"},
    "lookbackHours": {"type": "integer", "minimum": 1, "maximum": 168}
  },
  "required": ["sourceIp", "targetAsset", "lookbackHours"],
  "additionalProperties": false
}
```

后端必须校验来源 IP 和目标资产与当前 Incident 完全一致。查询结束时间固定为 `incident.firstSeen`，查询区间为 `[firstSeen - lookbackHours, firstSeen)`，不得使用工具执行时的当前时间，也不得查询事件发生之后的数据。

返回字段：`totalLoginCount`、`successCount`、`failedCount`、`lastLoginTime`、`knownSource`、`hasSuccessfulLogin`。查询无记录与查询失败必须区分。

### 14.2 `query_authorized_operations`

用途：查询当前事件附近是否存在授权运维记录。

参数 Schema：

```json
{
  "type": "object",
  "properties": {
    "targetAsset": {"type": "string", "description": "目标资产，必须与当前事件一致"},
    "startTime": {"type": "string", "description": "ISO 8601，带时区"},
    "endTime": {"type": "string", "description": "ISO 8601，带时区"}
  },
  "required": ["targetAsset", "startTime", "endTime"],
  "additionalProperties": false
}
```

后端必须校验：`targetAsset` 与当前 Incident 一致；`startTime <= endTime`；查询区间必须位于 `[firstSeen - 5分钟, lastSeen + 5分钟]` 内；查询跨度不得超过 30 分钟。

返回字段：`matched`、`operations[]`；单条运维记录包含 `ticketId`、`operator`、`startTime`、`endTime`、`status`。只有符合时间范围且状态符合授权规则的记录才能作为有效授权证据。
返回的 `startTime`、`endTime` 统一转换为 UTC `Z` 表示，便于与 Incident 时间比较；原始 fixture 不修改。

### 14.3 工具执行限制

- 最多 2 轮工具调用循环、最多 4 次工具调用。
- 单工具超时建议 3000ms，Agent 总耗时上限建议 45000ms。
- 只读取对应模拟数据；不允许任意 SQL、Shell、URL、文件路径或安全处置动作。
- 参数校验失败返回 `REJECTED`；工具异常和超时分别记录 `FAILED`、`TIMEOUT`。
- 工具失败以结构化错误反馈 Agent，并保存 ToolCallTrace；不得伪装成“未发现异常”。
- Ground Truth 不得通过任何工具访问。

## 15. 前后端职责边界

| 能力 | 前端 | 后端 |
|---|---|---|
| 告警/事件列表 | 展示、筛选交互 | 查询和筛选 |
| 特征与规则 | 展示 | 计算和评分 |
| Agent 研判 | 触发、Loading、结果展示 | 执行 Agent、工具和校验 |
| 工具轨迹 | 展示真实轨迹 | 白名单注册、参数校验、执行和记录 |
| 证据 | 展示来源 | 校验 Alert ID / Tool Call ID |
| 人工审核 | 提交表单 | 校验状态、更新 Incident、追加记录 |
| 评估 | 展示指标 | 读取 Ground Truth 并计算 |

## 16. 接口验收清单

- [ ] Dashboard 同时返回类别分布和具体类型分布。
- [ ] 告警列表支持类型、类别、等级、来源 IP 和目标资产筛选。
- [ ] 未支持策略的告警可查询，但没有研判入口。
- [ ] 事件列表使用 `ruleRiskLevel` 筛选规则风险。
- [ ] 事件详情返回特征、规则、关联告警、最近 AI 结果和工具轨迹。
- [ ] Agent 至少真实执行一个只读工具；工具轨迹可查看。
- [ ] AI 输出经过结构校验和证据引用校验。
- [ ] 工具失败不会伪装成查询成功。
- [ ] 审核接口更新事件状态并追加审核记录。
- [ ] 评估指标只统计符合条件的实际样本，空指标返回 null。
- [ ] Ground Truth 不会进入 Agent 上下文或普通业务 API。

## 阶段三口径补充（2026-11-19，用户确认）

- 历史登录工具的 `knownSource` 在本 Demo 中表示查询窗口内至少有一次成功登录；与 `hasSuccessfulLogin` 同值，不代表生产系统的可信来源认证。
- 可执行工具仍受后端白名单限制。非法工具请求不执行，但允许其请求名称（最长128字符）出现在 `REJECTED` 轨迹，参数摘要为空；该轨迹不能作为成功证据。
- 阶段三先启用 `query_login_history`；后续已按本文件 14.2 实现 P1 的 `query_authorized_operations`。结构校验字符串上限2000字符，数组上限20项，最终输出上限50000字符；代表日志最多3条、每条500字符。

**文档结束**
