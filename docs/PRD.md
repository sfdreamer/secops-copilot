# PRD.md — SecOps Copilot

> **产品名称：** SecOps Copilot（SOC 多类型安全告警智能研判平台）  
> **项目交付名称：** 复试作品徐慧超  
> **文档版本：** V1.3  
> **状态：** 开发基线  
> **项目性质：** AI 研发工程师复试 Demo  
> **开发约束：** 建议实际投入不超过 4 小时

## 1. 产品概述

### 1.1 背景与问题

SOC 分析师需要处理来自 SIEM、IDS/IPS、WAF、EDR、防火墙和主机日志等来源的大量安全告警。告警通常存在重复、误报、上下文分散和人工研判成本高等问题。

本项目聚焦一个具体场景：**将重复的 SSH 暴力破解告警聚合为安全事件，结合规则分析和 Agent 按需取证，辅助分析师判断事件是否可疑。**

### 1.2 产品定位

面向 SOC 安全分析师的轻量级告警管理与 AI 辅助研判工作台。

核心方案：

**多类型告警统一管理 → SSH 告警聚合 → 特征提取与规则评分 → SOC Triage Agent 按需取证 → 结构化研判 → 人工审核。**

本期支持多类型告警的统一加载、展示和筛选；仅对 `SSH_BRUTE_FORCE` 实现完整研判。端口扫描、SQL 注入和恶意软件告警只用于展示和扩展能力说明，不生成虚假的研判结果。

### 1.3 目标用户

- **SOC 安全分析师：** 快速查看告警、理解事件、获取证据和处置建议、记录审核结论。
- **复试评审人员：** 了解问题定义、AI 技术应用、工程实现和效果评估。

## 2. 产品目标与范围

### 2.1 本期目标

1. 加载多类型模拟告警，并转换为统一 `Alert` 模型。
2. 支持告警分类、搜索和筛选，明确展示策略是否支持。
3. 对 SSH 暴力破解告警进行关联聚合、特征提取和规则风险评分。
4. 通过 SOC Triage Agent 按需查询历史登录或授权运维信息。
5. 输出结构化 AI 研判报告，并校验证据引用。
6. 支持分析师对聚合事件进行人工审核。
7. 使用模拟 Ground Truth 和实际运行记录计算可用的评估指标。

### 2.2 功能优先级

| 优先级 | 功能 | 验收重点 |
|---|---|---|
| P0 | 多类型告警加载、列表、详情和筛选 | 四类告警可统一展示；未支持类型不出现研判入口 |
| P0 | SSH 聚合、特征提取和规则评分 | 结果正确、规则命中可解释 |
| P0 | 单个 SOC Triage Agent | 能够执行有界工具调用并输出结构化结果 |
| P0 | 至少一个真实可用的只读工具 | 工具由后端执行，轨迹可追溯 |
| P0 | AI 输出与证据校验 | 无效引用不能作为有效证据展示 |
| P0 | 人工审核 | 三种审核结果能更新事件状态并留痕 |
| P1 | 第二个只读工具 | 授权运维查询能正常调用和展示 |
| P1 | 基础评估 | 数据、规则、AI、工具指标按实际数据计算 |
| P2 | 完整混淆矩阵、扩展评估和 UI 精细化 | 不影响核心闭环时再实现 |

P0 是最小可交付范围。P1/P2 不得阻塞 P0。若时间不足，优先保证一个真实 Agent 工具调用闭环，不得使用静态数据伪装真实调用。

### 2.3 不在本期范围

- 接入真实 SIEM、IDS/IPS、WAF、EDR 或生产日志。
- 实现端口扫描、SQL 注入、恶意软件的完整研判策略。
- 自动封禁 IP、隔离主机、修改防火墙等写操作。
- Multi-Agent、Supervisor、完整 RAG、向量数据库。
- 微服务、消息队列、生产级权限、高可用和灾备。
- 将模型隐藏思维链作为产品功能展示。

## 3. 告警分类与模拟数据

### 3.1 告警类型

| `category` | 含义 | 示例 `alertType` |
|---|---|---|
| `AUTHENTICATION` | 身份认证 | `SSH_BRUTE_FORCE` |
| `NETWORK` | 网络攻击 | `PORT_SCAN` |
| `WEB_ATTACK` | Web 攻击 | `SQL_INJECTION` |
| `HOST_SECURITY` | 主机安全 | `MALWARE_ACTIVITY` |
| `DATA_SECURITY` | 数据安全 | 后续扩展 |
| `UNKNOWN` | 未知类型 | `UNKNOWN` |

本期只有 `SSH_BRUTE_FORCE` 注册分析策略。模拟数据直接提供 `alertType` 和 `category`，不将类型映射描述为 AI 分类能力。

### 3.2 数据生成要求

使用场景模板生成本地 JSON fixtures，数据必须可复现，不完全随机。建议准备约 100–200 条告警，其中包含 SSH 高频失败、失败后成功、授权运维、少量密码输错和信息不足等场景；其他类型准备少量展示样本。

- 每条告警具有唯一 `alertId`。
- `scenarioId` 仅用于模拟数据与评估标签关联，不参与聚合和 Agent 决策。
- 一个用于评估的 SSH `scenarioId` 必须对应且只对应一个 `Incident`；同一场景内告警共享聚合键，并落在同一个 5 分钟窗口内。
- 不同评估场景必须避免因相同聚合键和时间窗口而被合并。
- 如果一个 Incident 的关联告警出现多个 `scenarioId`，该事件标记为不符合场景级评估条件，不强行匹配 Ground Truth。
- 模拟 IP 使用文档示例地址段，如 `203.0.113.0/24`、`198.51.100.0/24`、`192.0.2.0/24`。
- 数据全部为虚构，不代表真实攻击或真实资产。

## 4. 核心业务规则

### 4.1 聚合规则

SSH 聚合键：

```text
alertType + sourceIp + targetAsset + attributes.protocol
```

- 只聚合 `SSH_BRUTE_FORCE`。
- 同一聚合键内按时间升序处理，以当前事件第一条告警为窗口起点。
- 时间差 **小于或等于 5 分钟** 的告警归入同一事件；超过 5 分钟开启新事件。
- 缺少来源 IP、目标资产或协议等聚合关键字段时，不与其他告警合并；该告警单独形成事件并标记数据不完整。
- 重复 `alertId` 在接入阶段拒绝或去重并记录原因；一个 Alert 最多归属一个 Incident。
- 原始告警始终保留。

### 4.2 SSH 特征

由后端计算或查询资产数据，至少包含：

- `failedAttempts`、`successfulAttempts`、`distinctUserCount`
- `windowSeconds`、`windowMinutes`；`firstSeen`、`lastSeen` 由后端计算并保存在 Incident，按 C1 裁决不重复放入 SecurityFeatures。
- `failureRate`、`successAfterFailure`
- `publicExposure`、`assetCriticality`

`failureRate = failedAttempts / max(windowSeconds / 60, 1 / 60)`。缺失资产信息时 `publicExposure = null`、`assetCriticality = UNKNOWN`，不得默认认为未暴露。

### 4.3 规则风险评分

| 命中条件 | 分值 |
|---|---:|
| 登录失败 10–29 次 | +15 |
| 登录失败 30–99 次 | +30 |
| 登录失败 ≥100 次 | +45 |
| 失败后出现成功登录 | +30 |
| 不同用户名数量 ≥5 | +10 |
| 目标资产公网暴露 | +10 |

失败次数区间互斥，只取最高匹配区间；其余规则分别累加，最终分数上限为 100。

| 分数 | 规则风险等级 |
|---|---|
| 80–100 | `CRITICAL` |
| 50–79 | `HIGH` |
| 30–49 | `MEDIUM` |
| 0–29 | `LOW` |

该分数是 Demo 启发式规则，不代表攻击概率或生产安全标准。规则风险等级与 AI 风险等级、AI 结论分开保存和展示。

### 4.4 AI 研判

Agent 接收后端构建的结构化事件上下文，判断是否需要额外信息，并按需调用只读工具。AI 输出至少包括：

- `verdict`：`SUSPICIOUS`、`LIKELY_BENIGN`、`NEEDS_REVIEW`
- `summary`、`attackType`、`riskLevel`、`reasoningSummary`
- `evidence`、`recommendations`、`uncertainties`

`reasoningSummary` 是面向分析师的分析摘要，不是隐藏思维链。AI 不得修改规则分数或直接更新最终人工审核状态。

### 4.5 人工审核与状态

事件业务状态：

`PENDING_ANALYSIS`、`ANALYZING`、`PENDING_REVIEW`、`ANALYSIS_FAILED`、`CONFIRMED_THREAT`、`FALSE_POSITIVE`、`INVESTIGATING`。

AI 分析状态：`NOT_STARTED`、`RUNNING`、`SUCCEEDED`、`FAILED`。

- 新事件：`PENDING_ANALYSIS` / `NOT_STARTED`。
- Agent 执行：`ANALYZING` / `RUNNING`。
- 研判成功：`PENDING_REVIEW` / `SUCCEEDED`。
- 研判失败：`ANALYSIS_FAILED` / `FAILED`。
- 人工审核结果分别映射为 `CONFIRMED_THREAT`、`FALSE_POSITIVE`、`INVESTIGATING`。
- 允许审核的初始业务状态：`PENDING_ANALYSIS`、`PENDING_REVIEW`、`ANALYSIS_FAILED`、`INVESTIGATING`；`ANALYZING` 不允许审核。
- `CONFIRMED_THREAT` 和 `FALSE_POSITIVE` 在本期视为终态，不支持直接修改或重新打开。

## 5. 页面需求

### 5.1 Dashboard

展示：原始告警总数、按 `category` 的分布、按 `alertType` 的分布、已支持事件数、高风险事件数、待审核事件数、未支持策略告警数、最近事件。统计由后端计算，明确标识“模拟数据 / 演示环境”。

### 5.2 告警与事件管理

原始告警与聚合事件分开展示。

- 原始告警支持按 `alertType`、`category`、`severity`、`sourceIp`、`targetAsset` 和关键词筛选；详情展示 `attributes` 与 `rawLog`。
- 事件列表支持按 `alertType`、`ruleRiskLevel`、`status`、`sourceIp`、`targetAsset` 和关键词筛选。
- 未支持策略的告警可查看，但显示“策略待扩展”，不显示研判入口。

### 5.3 事件详情与研判

展示事件基本信息、关联原始告警、特征及其来源、规则命中明细、Agent 实际工具调用轨迹、AI 研判报告、证据、建议和不确定性。研判期间显示 Loading 并禁用重复提交。

### 5.4 人工审核与记录

支持确认威胁、标记误报、进一步调查，并保存审核人、备注、关联 AI 研判记录和审核时间。Demo 不实现真实身份认证，`reviewer` 仅为演示字段。

### 5.5 效果评估

展示有效样本数、数据集版本和实际可计算指标。没有有效样本或没有真实计时数据时显示“暂无数据”或“未测量”，不得使用硬编码准确率。

## 6. 效果评估口径

| 层次 | 指标与口径 |
|---|---|
| 数据质量 | 解析成功率、必填字段完整率、类型映射覆盖率；类型映射覆盖率基于模拟数据中预设类型统计，不代表 AI 分类准确率 |
| 聚合 | 告警压缩率；有独立关联标注时计算聚合 Precision/Recall |
| 规则 | 以 `ruleRiskScore >= 50` 作为 Demo 高风险阈值，与 `ATTACK` 比较，计算 Precision/Recall/F1；`UNCERTAIN` 单独统计 |
| AI | 将 `SUSPICIOUS` 映射为 `ATTACK`、`LIKELY_BENIGN` 映射为 `BENIGN`、`NEEDS_REVIEW` 映射为 `UNCERTAIN`；只统计有成功且有效 AI 结果的评估事件 |
| 证据 | 引用可追溯率 = 有效 Alert/Tool 引用 ID 数 / 全部引用 ID 数；语义支持率通过人工抽样统计，未抽样时为未评估 |
| Agent | 根据实际工具调用与 `expectedToolPlan` 计算工具集合 Precision/Recall/F1；同时统计调用成功率、参数有效率、平均调用次数和耗时 |
| 业务 | 采纳率/修正率需有 AI 结果与人工审核配对记录；`SUSPICIOUS` 对应 `CONFIRMED_THREAT`、`LIKELY_BENIGN` 对应 `FALSE_POSITIVE`、`NEEDS_REVIEW` 对应 `INVESTIGATING`，一致计为采纳、不一致计为修正；效率提升率需有人工与 AI 辅助计时数据，否则显示未测量 |

评估数据边界：规则指标可对全部符合评估条件的模拟事件计算；AI 指标只统计实际运行并成功保存的 AI 研判；Agent 指标只统计真实发生的工具调用。评估接口不自动触发模型调用。所有结果仅代表合成数据集测试，不代表生产环境泛化效果。

## 7. 非功能与安全要求

- 本地运行优先，页面以可读性和演示稳定性为主。
- 模型 API Key 只由后端环境变量读取，不进入前端或日志。
- 原始日志和工具返回内容均视为不可信数据，不能作为系统指令。
- 工具白名单、参数校验、查询范围限制、超时和调用次数限制必须在后端执行。
- 本期工具只读，不提供真实安全处置。
- Ground Truth 只能由评估模块访问，不得进入 Agent、Prompt 或工具上下文。
- 模型失败时保留告警、事件、特征和规则评分，不伪造 AI 结果。

## 8. 验收标准

### P0 验收

- [ ] 四类模拟告警可加载、筛选和查看详情。
- [ ] 未支持类型不生成 Incident 或虚假研判。
- [ ] SSH 告警按规则聚合，窗口边界和缺失关键字段处理正确。
- [ ] 特征与风险分数正确，命中规则可解释。
- [ ] Agent 能真实调用至少一个只读工具，工具轨迹可查看。
- [ ] AI 结果为结构化数据，证据引用通过后端校验。
- [ ] 人工审核可更新事件状态并追加审核记录。
- [ ] 模型不可用时仍能查看基础告警和规则结果。

### P1/P2 验收

- [ ] 第二个只读工具可用并记录轨迹。
- [ ] 基础评估指标来自真实运行记录和隔离的 Ground Truth。
- [ ] 无效样本、未测量指标正确显示为空或未测量。
- [ ] 完整评估图表和视觉优化不影响核心闭环。

## 9. 演示主线

1. 展示多类型告警统一管理。
2. 打开一个 SSH 事件，说明多条告警如何聚合。
3. 展示后端特征和规则评分。
4. 触发 Agent，展示真实 Function Calling 工具调用。
5. 展示结构化研判、证据来源和不确定性。
6. 提交人工审核并查看记录。
7. 展示实际可计算的评估指标，并说明模拟数据局限。

## 阶段三口径补充（2026-11-19，用户确认）

- 历史登录工具的 `knownSource` 在本 Demo 中表示查询窗口内至少有一次成功登录；与 `hasSuccessfulLogin` 同值，不代表生产系统的可信来源认证。
- 可执行工具仍受后端白名单限制。非法工具请求不执行，但允许其请求名称（最长128字符）出现在 `REJECTED` 轨迹，参数摘要为空；该轨迹不能作为成功证据。
- 阶段三先启用 `query_login_history`；授权运维工具属于 P1，后续已按 API.md 14.2 实现。结构校验字符串上限2000字符，数组上限20项，最终输出上限50000字符；代表日志最多3条、每条500字符。

**文档结束**
