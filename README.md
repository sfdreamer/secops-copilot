# 复试作品徐慧超 · SecOps Copilot

SOC 多类型安全告警智能研判 Demo。将重复的 SSH 暴力破解告警聚合为安全事件，结合规则分析与 SOC Triage Agent 按需取证，辅助分析师研判，并支持人工审核与效果评估。

> 设计基线：**V1.3** — `AGENTS.md`、`docs/PRD.md`、`docs/ARCHITECTURE.md`、`docs/DATABASE.md`、`docs/API.md`
> 当前进度：**阶段一至四已完成；2026-11-19 完成交付前审查整改与真实模型闭环适配**。
> 真实模型闭环**已跑通**：密钥属于硅基流动 / 轨迹流动平台（OpenAI 兼容协议，非 DashScope 原生），已新增 `OpenAiCompatibleAdapter` 并选用 `Qwen/Qwen3-30B-A3B-Instruct-2507`；独立集成测试 `mvn -Dtest=DashScopeLiveIT test` 通过，产出 `backend/target/dashscope-live-result.json`（`validationStatus=VALID`、`executionMode=TOOL_ENABLED`、工具轨迹 `SUCCESS`）。演示主线采用 [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) 的**路线 A（真实 Function Calling 闭环）**；路线 B 保留为现场断网 / 模型不可用时的兜底。

## 交付说明（面向复试评审）

### 命名与选题

- **交付名称**：复试作品徐慧超（对应题目「命名规则【复试作品+姓名】」）。交付目录 / 压缩包请一并命名为 `复试作品徐慧超`。
- **选题**：`AI研发工程师复试题目-1.pdf`「改善 SOC 告警处理体验」。另有一份题目 2「文件保险箱」为不同选题，本项目不覆盖。
- **交付形态**：可本地运行的交互式 Demo（启动方式见下）+ [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) 演示脚本 + `screenshots/` 界面截图。

### AI 开发工具（工具 / 使用方式 / 效果）

| 工具 | 使用方式 | 效果 |
|---|---|---|
| OpenAI Codex（阶段一至三） | 以 `AGENTS.md` + 四份设计文档为硬约束，按「先输出实施计划与冲突 → 分阶段生成 → 每阶段 `mvn test` / `npm run build` 校验 → 人工逐文件复核」推进；不擅自扩大范围、不重写基线 | 交付后端主干：统一 Alert 接入与去重、SSH 聚合 / 特征 / 规则评分、有界 ReAct Agent、只读工具白名单、结构化输出与证据校验、人工审核；71 项自动化测试（均为 Mock LLM / 模拟 HTTP 层） |
| WorkBuddy（阶段四及交付前审查） | 会话式生成前端页面与后端接口、Docker 单机部署、界面长截图；本次按题目 + 四份文档逐模块只读审查，再按审查结论整改并复跑构建测试；用脱敏探测定位密钥归属（`/v1/models` 返回 200），实测三个候选模型的 Function Calling 与严格 JSON 输出后选型，新增 OpenAI 兼容适配层 | 5 个业务页面、`/api/alerts` `/api/dashboard` `/api/evaluation` 三组接口、Docker Compose 两容器、6 张 1440 宽截图、演示脚本、进度文档同步、评估指标诚实化与评估模块测试；**真实模型闭环从 401 失败变为可验证通过** |

共同约束：不引入 MySQL / Redis / MQ / Multi-Agent / 权限系统；`scenarioId` 与 Ground Truth 只在评估模块可见；模型密钥仅由后端环境变量读取。**AI 只辅助实现，不改变文档基线；所有结论以实际执行的测试与运行产物为准，不把 Mock 或静态数据说成真实模型调用。**

### 实际投入时间

> 原交付记录为 **4 小时**（含了解背景、准备数据、开发、整理说明）。本次最终审查后的整改为追加工作，应另行如实说明实际投入；未完成部分见下文「未完成事项」。

### 10–15 分钟演示脚本

- **本次实际采用路线 A（真实 Function Calling 闭环）**：完整脚本见 [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md)，含逐分钟话术、诚实性约定、常见问题与局限；路线 B 作为兜底保留。
- 时间轴摘要：0–2 背景与问题 → 2–3 系统状态 → 3–5 Dashboard → 5–8 告警 / 事件双 Tab 与 SSH 聚合 → 8–10 特征与规则命中 → 10–12.5 **触发 Agent 研判**（真实 `query_login_history` 调用 + SUCCESS 轨迹 + 结构化报告与证据）→ 12.5–14 人工审核与记录 → 14–15 评估页与「如何判断有效」。
- 讲解要点：**帮助谁**（SOC 分析师）**解决什么问题**（重复告警、误报、上下文分散）见脚本第 1 节；**如何判断方案有效**（PRD 第 6 节五层口径、算不出来的一律留空）见脚本第 2 节。
- 现场若模型服务不可用，立即切换到脚本路线 B：**只演示告警 / 事件 / 规则 / 审核 / 评估，明确说明 AI 研判失败**，不得展示任何伪装的成功报告。

界面截图（`screenshots/`，静态长图，作讲解与断网兜底；现场优先演示运行中的真实页面）：

| 截图 | 页面 |
|---|---|
| `screenshots/01_安全态势总览_dashboard.png` | Dashboard |
| `screenshots/02_告警与事件_alerts.png` | 告警与事件双 Tab |
| `screenshots/03_事件详情与研判_incident.png` | 事件详情与研判 |
| `screenshots/04_审核记录_reviews.png` | 审核记录 |
| `screenshots/05_效果评估_evaluation.png` | 效果评估 |
| `screenshots/06_系统状态_status.png` | 系统状态（已随 `/status` 页同步重截） |

### 最终汇报（对应 AGENTS.md 第 8 节）

**已完成内容**：多类型模拟告警统一加载 / 筛选 / 详情；策略注册（仅 `SSH_BRUTE_FORCE`）；SSH 聚合、特征提取、规则风险评分；单个有界 ReAct Agent + 白名单只读工具 + 结构化输出与证据引用校验；人工审核与记录；基于隔离 Ground Truth 的全链路评估；五个业务页面与 Docker Compose 单机部署。

**主要变更文件**：

- 后端：`backend/src/main/java/com/secops/{ingestion,engine,strategy,service,controller,repository,ai,agent,tools,evaluation,config,common}/`、`backend/src/main/resources/mock/*.json`、`backend/src/main/resources/application.yml`、`backend/pom.xml`。
- 前端：`frontend/src/{views,api,types,layouts,components,utils,styles,router}/`、`frontend/{Dockerfile,nginx.conf}`。
- 工程与文档：`docker-compose.yml`、`README.md`、`docs/{PRD,ARCHITECTURE,DATABASE,API,DEV_PROGRESS,DEMO_SCRIPT}.md`、`AGENTS.md`、`.env.example`、`scripts/generate_fixtures.py`、`screenshots/`。

**使用的 AI Coding 工具**：见上表（OpenAI Codex、WorkBuddy）。

**实际执行的测试及结果**（2026-11-19 13:10 后本机复测，非沿用旧记录）：

| 命令 | 结果 |
|---|---|
| `mvn -B test`（backend） | **71/71 通过**，0 失败 / 0 错误 / 0 跳过；含授权运维工具边界测试及 5 项 `EvaluationServiceTest`，均为 Mock LLM / MockMvc / 模拟 HTTP 层 |
| `mvn -B -Dtest=DashScopeLiveIT test`（backend） | **连跑 3 次全部通过**（29.96s / 23.06s / 31.54s）。真实硅基流动 `Qwen/Qwen3-30B-A3B-Instruct-2507` 普通调用 + Function Calling；产出 `backend/target/dashscope-live-result.json`：`validationStatus=VALID`、`executionMode=TOOL_ENABLED`、工具轨迹 `SUCCESS`，证据含 ALERT + TOOL 两类 |
| `npm run build`（frontend） | **通过**（`vue-tsc --noEmit` + `vite build`）；主 chunk 约 2.23 MB，仍有大 chunk 警告 |
| `docker compose config -q` | 通过；`eclipse-temurin:17-jre-jammy` 实测自带 curl / wget，两个 healthcheck 可用 |

**本次最终审查后的运行复测（2026-11-19，追加于上表的历史验收）：** 最新 Docker 镜像已重建并启动，两容器 healthy；`/api/evaluation` 返回模型 `Qwen/Qwen3-30B-A3B-Instruct-2507`、Prompt `ssh-triage-v4`。对 `SCENARIO-SSH-003` 所属事件发起真实 HTTP 研判：首次请求在两次工具成功后遇模型读取异常，返回 503 并保留失败轨迹；从 `ANALYSIS_FAILED` 重试成功，`query_login_history` 与 `query_authorized_operations` 均为 `SUCCESS`，报告 `LIKELY_BENIGN`、`validationStatus=VALID`，有效证据包含告警与两种工具引用。浏览器实测 Dashboard、告警/事件 Tab、事件详情与人工审核；提交 `FALSE_POSITIVE` 后，事件状态与审核记录均更新。模型服务有波动，演示前应预检并保留失败兜底路线。以上为本次实际请求，不代表模型结论始终正确。

**超时口径调整（本次实测驱动）**：原 `app.agent.total-timeout-ms=30000`，同一模型连跑出现 29.42s 通过 / 33.0s、32.33s 超时失败，现场演示存在偶发失败风险。经用户确认后放宽至 **45000ms**，单次模型读超时 25000→40000ms，代码硬上限 30000→60000ms；前端研判请求 60s、Nginx `proxy_read_timeout` 90s 均留有余量。放宽后 IT 连跑 3 次全通过，其中 31.54s 那次在旧上限下必然失败。已同步 `application.yml`、`SocTriageAgent`、`OpenAiCompatibleAdapter`、`DashScopeAdapter` 与 `ARCHITECTURE.md` / `API.md` / `DEMO_SCRIPT.md` 的口径描述。

**模型选型说明**（不输出密钥值）：密钥属于硅基流动 / 轨迹流动（OpenAI 兼容），DashScope 中国大陆与国际两个 endpoint 均返回 401 `invalid_api_key`。平台 91 个模型按真实链路实测后仍选用 `Qwen/Qwen3-30B-A3B-Instruct-2507`：`deepseek-ai/DeepSeek-V3.2` 会用 ```json 代码块包裹输出被判无效；Qwen3.6/3.8、DeepSeek-V4-Pro、GLM-5.3、Kimi-K2.6 等更强模型第二轮普遍 45s 无响应而撞超时；`DeepSeek-V4-Flash` 在真实集成测试中判字段无效，`GLM-4.5-Air` 仅 `PARTIALLY_VALID`。唯一可用升级 `Qwen/Qwen3.5-35B-A3B`（VALID、研判更审慎）因更慢、且在 45s 下收益有限，本次未切换。

**启动方式**：见下文「启动」与「Docker 部署」。

**环境变量**：`SECOPS_API_KEY`（兼容旧名 `SECOPS_COPILOT_API_KEY`），仅由后端读取；未配置时 `/api/health` 返回 `modelConfigured=false`，告警 / 事件 / 特征 / 规则结果仍可查看。详见「模型配置」。

**未完成事项**：见下文「未完成事项」节。证据语义支持率、效率提升率与引用可追溯率本期不可计算；前端主 chunk 约 2.2 MB，非阻断。P1 授权运维工具已实现，但 AI 结论仍需人工复核。

**与四份设计文档不一致的地方**：

1. `API.md` 13 的 `ruleEvaluation` 原结构缺少 `UNCERTAIN` 单独计数字段，与 `PRD.md` 6「`UNCERTAIN` 单独统计」冲突 —— 按 `AGENTS.md` 2.1 报告后做最小补齐：新增 `uncertainSampleCount`，已同步 `API.md` 13、`EvaluationService.java`、`EvaluationServiceTest.java`、`frontend/src/types/evaluation.ts`、`EvaluationView.vue`。
1b. `API.md` 13 的 `evidenceEvaluation` 新增 `traceabilityStatus`（`NOT_COMPUTABLE`），用于如实说明「过滤前引用总数未持久化、分母不可还原」，避免输出恒为 1.0 的失真值；已同步后端 record、前端类型与页面。
2. 未知路径返回 HTTP 404 + `INVALID_ARGUMENT`，而 `API.md` 1.4 中该错误码对应 400 —— 既有偏差，本次保留未改。
3. `API.md` 10 的 `AIAnalysis` 响应示例字段滞后于 `DATABASE.md` 5.5（示例未列 `analysisStatus` / `incidentId` / `toolCallCount` / `failureReason`），代码与前端类型一致，示例未同步。
4. `query_authorized_operations` 已按 `API.md` 14.2 实现，保留 P1 优先级；工具只返回时间范围内已批准或已完成的运维记录。
5. **模型平台与 `AGENTS.md` 4 / `ARCHITECTURE.md` 10 的「Spring AI Alibaba + DashScope」约定不一致**：用户提供的密钥实际属于硅基流动 / 轨迹流动（OpenAI 兼容协议），在 DashScope 上恒返回 401 `invalid_api_key`。按用户明确指示做最小适配：新增 `OpenAiCompatibleAdapter`（RestClient + Jackson），由 `app.model.provider=openai-compatible` 切换；DashScope 原生路径保留但非默认。`ARCHITECTURE.md` 10 已同步说明。SDK 类型仍只集中在 `ai` 适配层。
6. `ApplicationSmokeTest` 的密钥泄露断言同步覆盖新旧两个环境变量名；`/api/health` 响应体不含任何环境变量名或凭据。
7. **Agent 总耗时上限由 `ARCHITECTURE.md` 4 / `API.md` 10、14 的 30000ms 调整为 45000ms**：按 `AGENTS.md` 2.1 先报告、经用户确认后做最小变更，并已同步 `ARCHITECTURE.md`（正文建议值与 9 节配置示例）、`API.md`（两处）、`DEMO_SCRIPT.md`、前端注释与 `application.yml`。理由与实测数据见上文「超时口径调整」。单工具 3000ms、2 轮 / 4 次调用预算未变。

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17+（本机为 JDK 21，编译目标 `release=17`） |
| Maven | 3.9+ |
| Node / npm | 22+ / 10+ |

## 模型配置

- 环境变量：**`SECOPS_API_KEY`** —— 硅基流动 / 轨迹流动平台密钥（OpenAI 兼容协议），**仅由后端读取**；兼容别名 `SECOPS_COPILOT_API_KEY`，候选名与优先级由 `app.model.api-key-env` 配置。
- 接入方式：`app.model.provider=openai-compatible`，`base-url=https://api.siliconflow.cn/v1`，模型 `Qwen/Qwen3-30B-A3B-Instruct-2507`（实测 Function Calling 准确、严格 JSON 无思维链、MoE 响应快；备选 `Qwen/Qwen2.5-72B-Instruct`）。DashScope 原生 SDK 适配路径保留，可用 `provider=dashscope` 切回。
- **选型依据**：实测三个候选模型的 Function Calling 与「工具结果回填后输出严格 JSON」；`deepseek-ai/DeepSeek-V3.2` 会用 ```json 代码块包裹输出，会被 `AgentResultValidator` 判无效，因此未选用。
- 禁止写入前端、提交到 Git、输出到日志或任何响应体。`.env.example` 仅为变量说明；Spring Boot 不自动读取 `.env`，需由启动进程注入环境变量。
- 未配置时 `/api/health` 返回 `modelConfigured=false`，Agent 研判不可用；**告警、事件、特征和规则评分仍可正常查看**，不会伪造 AI 结果。
- `/api/health` 的 `modelConfigured` 只表示是否配置了密钥，不表示模型当前一定可用；真实可用性以独立集成测试 `mvn -B -Dtest=DashScopeLiveIT test` 为准（该测试以 `IT` 结尾，不进入默认 `mvn test`）。
- 真实集成测试成功时生成 `backend/target/dashscope-live-result.json`，包含实际结构化报告与 `SUCCESS` 工具轨迹；该文件是本次 P0 验收证据。

## 启动

```bash
# 后端 http://localhost:8080
cd backend
mvn spring-boot:run

# 前端 http://localhost:5173（/api 已代理到 8080）
cd frontend
npm install
npm run dev
```

构建与测试：

```bash
cd backend && mvn test
cd frontend && npm run build
```

## Docker 部署（推荐）

前置：Docker Desktop（含 Compose v2）；宿主机 **8080 / 8081 端口空闲**。后端和前端镜像均多阶段构建：
`backend/Dockerfile`（Maven 打 fat jar → 仅 JRE 运行）、`frontend/Dockerfile`（Node 构建 → Nginx 托管静态文件并反代 `/api`）。

```bash
# 在项目根目录（docker-compose.yml 所在目录）执行
docker compose up -d --build   # 首次构建需联网下载基础镜像与 Maven/npm 依赖
```

| 入口 | 地址 | 说明 |
|---|---|---|
| Web 页面 | `http://127.0.0.1:8080` | 仅绑定本机回环；Nginx 托管前端并反代 `/api` |
| 公网 HTTPS | `https://secops.eu.cc` | Cloudflare Tunnel 连接到前端容器 |
| 后端调试 | `http://127.0.0.1:8081/api/health` | 仅绑定本机回环，不对外暴露 |
| 健康检查 | `/api/health` | 后端和前端配置 healthcheck；Tunnel 在前端 healthy 后启动 |

要点：

- **密钥**：把 `SECOPS_API_KEY` 写入系统环境变量，或复制 `.env.example` 为项目根目录 `.env`（已被 `.gitignore` 忽略）。compose 将 `SECOPS_API_KEY` 或兼容名 `SECOPS_COPILOT_API_KEY` **只注入 backend 服务**；未配置时 `/api/health` 返回 `modelConfigured=false`。不写入镜像、compose 文件或日志。
- **AI 研判超时**：Nginx 的 `proxy_read_timeout` 设为 90s，大于 Agent 总耗时 45s 上限。
- **SPA 路由**：`nginx.conf` 用 `try_files ... /index.html` 支持 Vue Router history 模式；`/assets/` 带内容 hash，长缓存。
- **运行期数据仍在内存**：`docker compose restart` / `down` 后事件、AI 研判、工具轨迹、审核记录、评估结果全部重置，fixtures 会重新加载。
- **构建器**：本次已实际使用 Docker Compose 默认 BuildKit 成功构建两个镜像；若网络无法下载基础镜像或依赖，先检查 Docker 网络。

常用运维：

```bash
docker compose ps                 # 状态与健康检查
docker compose logs -f backend    # 后端日志
docker compose restart           # 重启（内存数据重置）
docker compose down              # 停止并移除容器（保留镜像）
docker compose up -d --build      # 代码改动后重新构建并启动
```

### Cloudflare Tunnel 公网访问

- 域名 `secops.eu.cc` 已托管在 Cloudflare；根域名有一条已代理 CNAME，目标为 `a3a612ae-94e7-40aa-b5fd-ddcce01645b8.cfargotunnel.com`。Tunnel 名为 `secops-copilot`，配置见 `cloudflared/config.yml`。
- `docker compose up -d --build` 同时启动 `cloudflared`。它通过 Compose 内网访问 `frontend:80`；前后端宿主机端口均只绑定 `127.0.0.1`。主机须保持 Docker Desktop 与三个容器运行，公网地址才可访问。
- Tunnel 凭据位于本机 `%USERPROFILE%\.cloudflared\a3a612ae-94e7-40aa-b5fd-ddcce01645b8.json`，由 Compose **只读挂载**为 `/etc/cloudflared/credentials.json`，不存放在仓库。`docker-compose.yml` 使用 `${USERPROFILE}` 定位该文件；移到其他主机时，需将凭据安全迁移到新主机并调整挂载源路径。不要提交或分享此文件。
- 验证：`docker compose ps` 确认 backend、frontend 健康且 cloudflared 运行；打开 `https://secops.eu.cc`，再访问 `https://secops.eu.cc/api/health`。当前公网 Demo 无登录鉴权，访问者可以触发 AI 研判和人工审核；长期对外开放时应先配置 Cloudflare Access 或其他访问控制，并关注模型调用费用。

## 数据说明

- `backend/src/main/resources/mock/*.json` 为**只读 JSON fixtures**：`alerts.json`、`assets.json`、`login-history.json`、`authorized-operations.json`、`ground-truth.json`。
- 当前规模：`alerts.json` 共 **125 条**（SSH 85 条，其中 80 条绑定 8 个评估场景；PORT_SCAN / SQL_INJECTION / MALWARE_ACTIVITY 共 40 条展示样本）；资产 6 条、历史登录 12 条、授权运维 3 条、Ground Truth 8 条。
- fixtures 由 `scripts/generate_fixtures.py` 按场景模板**可复现生成**（非随机），重复运行结果一致。
- **运行期数据全部保存在内存**（Incident、AIAnalysis、ToolCallTrace、ReviewRecord、评估结果），**应用重启后重置**。
- 所有数据均为虚构，使用文档示例地址段（`203.0.113.0/24`、`198.51.100.0/24`、`192.0.2.0/24`），不代表真实攻击或真实资产。
- `ground-truth.json` 仅评估模块可访问，不进入 Agent 上下文、工具参数或普通业务 API 响应。

## 本期范围

- P0：多类型告警统一接入与筛选；策略注册（仅 `SSH_BRUTE_FORCE`）；SSH 聚合、特征提取、规则评分；单个 SOC Triage Agent + 有界 ReAct；至少一个真实只读工具；AI 结构化研判与证据校验；人工审核。
- P1：第二个只读工具、基础评估。
- 不实现：真实安全设备接入、自动封禁/隔离、Multi-Agent、完整 RAG、MySQL/Redis/MQ、微服务、权限系统。

## 阶段验收记录（历史快照）

> 以下各阶段记录是**当时的验收快照**，其中的「当前」「未通过」「未执行」「密钥无效」均描述当时状态，不代表交付时的最终状态。最终状态以本文开头「交付说明」「模型配置」「未完成事项」及 [`docs/DEV_PROGRESS.md`](docs/DEV_PROGRESS.md) 为准。

## 阶段一验收（已实际执行）

| 项 | 结果 |
|---|---|
| `cd backend && mvn test` | **18 项测试全部通过**（BUILD SUCCESS） |
| `cd frontend && npm install && npm run build` | **通过**（vue-tsc 类型检查 + vite build） |
| Spring 容器启动 + fixtures 加载 | 125 条读取 / 125 条接受 / 0 重复 / 0 拒绝 |
| `GET /api/health` | 200、`success=true`、`mockDataLoaded=true`、`Content-Type: application/json;charset=UTF-8` |
| 密钥不泄露 | 响应体不含环境变量名、密钥字段或凭据 |
| 重复 ID 去重、非法枚举拒绝、未支持策略 | 单测覆盖并通过 |

已实现：统一响应/异常/CORS、统一 `Alert` 模型与四类枚举、Ingestion 加载校验与去重、`AlertStrategyRegistry`（仅注册 `SSH_BRUTE_FORCE`）、`GET /api/health`、前端工程与集中式 API/类型层、系统状态页（Loading / Error / Empty 三态）。

## 阶段二实现与验证

- 仅 SSH 策略路由到真实实现：固定首条告警起点的 5 分钟窗口（包含边界），缺失聚合键单独成事件，重复 ID 不计数，scenarioId 不参与聚合。
- Java 计算特征、查询资产、输出互斥规则明细 `points` 和总分 `score`；理论规则最高分为 95，保留 100 分截断保护。
- 应用启动后从只读 fixtures 生成 13 个事件：85 条 SSH 告警全部且仅归属一次；40 条其他告警保留，不生成事件。重复调用初始化方法不会重复保存。
- 其中 6 个事件缺聚合关键字段：5 条额外展示告警，以及场景 005 的 ALERT-0059；均单独成事件并标记数据不完整。
- `GET /api/incidents` 支持分页和 API.md 第 8 节全部筛选；`GET /api/incidents/{id}` 返回事件、特征、规则与关联告警，无 AI 时返回 null / 空轨迹。前端只补充 DTO 类型，未开发业务页面。
- `firstSeen/lastSeen` 保存在 Incident；缺字段说明放入 `riskAssessment.reasons`，不增加分值。同一时间戳的失败和成功不推断先后。
- 未知资产返回 `publicExposure=null`、`assetCriticality=UNKNOWN`。单条告警窗口为 0 秒，失败速率以最小 1 秒为分母。
- API 演示：访问 `http://localhost:8080/api/incidents?page=1&pageSize=20`，复制返回的 incidentId 查询详情。本阶段无需模型密钥。
- 使用工具：OpenAI Codex。测试覆盖聚合窗口、去重、缺字段、特征、评分边界、真实 fixtures、HTTP 契约、过滤分页、重复生成、并发归属保护和评估字段隔离。
- 2026-11-19 实际验证：阶段一回归 `mvn -B test` 为 18/18；阶段二最终运行 45/45 通过（0 失败、0 错误、0 跳过）。`npm run build` 的 TypeScript 检查和 Vite 构建通过，仍有既有 chunk >500 kB 提示。接口通过 Spring Boot + MockMvc 测试，未声称已完成浏览器端到端联调。

## 未完成事项

- ~~原始告警列表/详情 API 与多类型展示筛选页面尚未实现~~（2026-11-19 阶段四已完成）。
- ~~真实模型普通调用与 Function Calling 验收~~（2026-11-19 已通过：改用硅基流动 / 轨迹流动 OpenAI 兼容协议 + `Qwen/Qwen3-30B-A3B-Instruct-2507`，独立集成测试产出 `dashscope-live-result.json`）。
- ~~演示脚本、进度同步、评估指标诚实化与评估模块测试~~（2026-11-19 已完成）。
- **P1 的第二个只读工具 `query_authorized_operations` 已实现**；仅当真实模型调用且后端返回 `SUCCESS` 轨迹时，才可声称本次研判查询了工单。
- **引用可追溯率不可计算**：过滤前引用总数未持久化、分母无法还原；返回 `null` + `NOT_COMPUTABLE`，不用有效引用凑 1.0。证据语义支持率需人工抽样（`NOT_EVALUATED`），效率提升率需人工计时（`NOT_MEASURED`）。
- 前端构建提示主 chunk 超过 500 kB（Element Plus + ECharts 全量引入），可做按需引入优化，非阻断。
- 尚无 Git 仓库；建议在全量测试 + 真实集成测试通过后初始化并提交（提交范围见「最终汇报」）。
- 既有通用未知路径返回 HTTP 404 + `INVALID_ARGUMENT`，仍与 API.md 1.4 存在偏差；实际事件不存在已返回 `INCIDENT_NOT_FOUND`。不把未知路径误标为告警不存在。
- 运行期数据全部在内存，重启重置；无数据库、无权限系统、无真实安全处置、无完整浏览器端到端自动化测试（公网页面已做浏览器烟测）。
- **不把 Mock、静态数据或单元测试输出描述为真实模型调用或真实评估结果。**

## 阶段三实现（2026-11-19）

使用 AI Coding 工具：OpenAI Codex。没有删除文件、重建项目或修改 fixtures，没有开发前端页面。

执行路径：`POST /api/incidents/{id}/analyze` → 原子检查状态 → 策略构建白名单上下文 → `SocTriageAgent` → DashScope → 函数请求 → 白名单/Incident 参数校验 → 本地只读查询 → 保存真实执行轨迹并回填 TOOL 消息 → 最终 JSON 校验 → 保存 AIAnalysis 并进入待审核。失败保存 FAILED 记录，保留规则结果。详情返回最近一次分析及对应轨迹。

- 固定依赖：`spring-ai-alibaba-core:1.0.0.2`，传递 Spring AI `1.0.0`，原 Spring Boot `3.5.16` 保留。
- 依据 Maven 发布的同版本 sources.jar 核对 `DashScopeApi.builder()`、`DashScopeChatModel.builder().call(Prompt)`、`DashScopeChatOptions.builder().withInternalToolExecutionEnabled(false)`、`withToolCallbacks/setToolCallbacks`、`AssistantMessage.ToolCall` 和 `ToolResponseMessage.ToolResponse`。SDK 类型只在 `ai` 适配层（及适配层测试）使用。
- 官方版本来源：[Alibaba 1.0.0.2](https://github.com/alibaba/spring-ai-alibaba/tree/v1.0.0.2)；[官方 DashScope 文档](https://github.com/spring-ai-alibaba/website/blob/main/integration/chatmodels/dashScope.md)。当前主分支示例不作为旧版本 API 签名依据。
- 关闭 SDK 自动执行工具，防止绕过两轮/四次预算；工具超时 3000ms、Agent 45000ms，通过有界线程池、Future 超时和取消控制。不排队无限积压；超时后台任务不能自行保存结果或覆盖审核状态。SDK 禁止自动重试。
- （当时）密钥仅由适配器读取后端环境变量 `SECOPS_COPILOT_API_KEY`，模型默认 `qwen-plus`；当时进程未配置有效密钥，真实网络调用未完成。**交付时已改为 `SECOPS_API_KEY` + OpenAI 兼容平台，详见开头「模型配置」。**
- `app.model.function-calling-enabled=false` 可显式禁用不支持的工具协议，执行 `NO_TOOL_FALLBACK`，不确定性中强制说明未查询工具。普通模型错误不会自动伪装成降级成功。
- P0 只注册 `query_login_history`，参数严格绑定当前来源 IP、目标资产和 1–168 小时窗口，结束时间排除 firstSeen 本身及未来记录。已确认 `knownSource` 代表窗口内存在成功登录。
- 非法工具名只保存在 REJECTED 轨迹，不执行。工具失败/超时强制追加信息缺口；只有本次成功调用的后端 TOOL ID 可成为证据。
- `AgentResultValidator` 校验 JSON、核心字段、枚举、长度、引用归属；无效证据剔除并标记 PARTIALLY_VALID。ID 可追溯性不等于证据语义正确，语义支持尚未人工评估。
- `POST /api/incidents/{id}/review`、`GET /api/reviews` 已实现；同一共享锁保护研判启动、审核状态更新及记录追加，模型执行不持锁。终态禁止再审核；INVESTIGATING 可继续追加审核。审核关联最近一次成功研判。

验证命令（backend 目录）：

```powershell
mvn -B test
# 单独运行：先普通模型调用成功，再强制实际 Function Calling
mvn -B '-Dtest=DashScopeLiveIT' test
mvn spring-boot:run
```

真实集成测试需要在启动 Maven 的进程环境中配置密钥。不要将密钥写入文件或发送到聊天。成功时产生 `backend/target/dashscope-live-result.json`，包含实际报告与工具轨迹；缺密钥时明确跳过，不会伪造结果。

本地工具实际执行结果（Mock LLM 驱动，不是真实模型调用）：来源 `203.0.113.12`、资产 `server-01`、事件锚点 `2026-11-18T00:00:00Z`、回溯168小时，得到 `totalLoginCount=2`、`successCount=2`、`failedCount=0`、`lastLoginTime=2026-11-17T23:20:00Z`、`knownSource=true`、`hasSuccessfulLogin=true`；08:05 的未来记录被排除。

结构化报告示例（单元测试 Mock 输出，非 DashScope 结果）：

```json
{
  "verdict": "NEEDS_REVIEW",
  "summary": "需要人工核实",
  "attackType": "SSH_BRUTE_FORCE",
  "riskLevel": "LOW",
  "reasoningSummary": "现有证据不足",
  "evidence": [],
  "recommendations": ["核实账号"],
  "uncertainties": []
}
```

主要代码：`backend/src/main/java/com/secops/{ai,agent,tools}`、`AnalysisService`、`ReviewService`、三个新增内存 Repository、`TriageController`；`frontend/src/types/incidents.ts` 仅同步类型。文档补充了用户确认的 knownSource 与拒绝轨迹口径；既有未知路径 404 + INVALID_ARGUMENT 偏差保留。本段为阶段三快照，当时 P1 授权运维工具尚未实现。

阶段三最终实际验证：`mvn -B test` **62/62 通过**（原45项回归 + 17项新增，0失败/0错误/0跳过）；独立 `DashScopeLiveIT` **1项跳过**（无环境密钥），不计为真实集成成功；`npm run build` 类型检查和构建通过（既有 >500 kB chunk 警告）。5个 fixtures SHA-256 与开发前一致。

## 阶段三验收更新（2026-11-19）

以 [DEV_PROGRESS.md](docs/DEV_PROGRESS.md) 为最新接续记录。本轮重新执行：自动化测试 **65/65 通过**，前端构建通过；独立真实 DashScope 测试 **1项错误、0跳过**。与此前环境不同，本轮已能读取密钥，但脱敏诊断为 **HTTP 401 / InvalidApiKey**，普通调用失败，未进入 Function Calling。此前“缺密钥跳过”仅表示上一轮结果。

本轮修正工具执行异常分类、拒绝重复 JSON 字段、保持不确定性数组上限，并排除未使用的 RAG/向量库/OpenNLP 传递依赖。未更改 fixtures 或开发下一阶段。真实集成未通过，因此未创建 Git 提交。

## 阶段四实现（2026-11-19，前端与评估）

使用 AI Coding 工具：WorkBuddy。未删除已有文件、未修改 fixtures 与四份设计文档。

**后端新增（全部按 API.md 口径，无硬编码统计）：**

- `GET /api/alerts`、`GET /api/alerts/{id}`：多类型筛选分页；详情含 `rawLog` 与动态计算的 `strategySupport`；`scenarioId` 经 `AlertView` 投影隔离，不向前端暴露。
- `GET /api/dashboard`：类别/类型分布、事件与风险统计、最近事件，全部由当前内存数据实时计算。
- `GET /api/evaluation`：新增 `evaluation` 模块（`GroundTruthFixtureRepository` 只读加载 `ground-truth.json`，仅评估模块可访问）与 `EvaluationService`，按 API.md 13 节计算数据质量、聚合压缩率、规则 P/R/F1（阈值 ≥ 50）、AI 指标与混淆矩阵、证据可追溯率、Agent 工具指标、业务采纳/修正率；无有效样本返回 `null`，语义支持率 `NOT_EVALUATED`，效率指标 `NOT_MEASURED`。`AlertFixtureLoader` 新增只读 `getReport()` 供数据质量指标使用。
- `application.yml` 新增 `app.fixtures.ground-truth-path`；`vite.config.ts` 代理目标支持 `VITE_API_TARGET` 覆盖（默认 8080）。

**前端重做（暗黑科技风，参考 `产品原型参考/`）：**

- 全局：深海军蓝底 + 青色主色 + 网格纹理、发光描边；Element Plus 深色覆写；等宽数字字体。
- 布局 `AppLayout`：侧边导航（实时健康/模型徽章、时钟）、顶栏面包屑与「演示环境 · 模拟数据」标识。
- 页面：`DashboardView`（5 张统计卡 + 风险环形/类型条形/类别玫瑰图 + 近期事件表）、`AlertsIncidentsView`（原始告警/安全事件双 Tab、全量筛选、分页、告警详情抽屉含 attributes 与 rawLog、未支持策略显示「策略待扩展」且无研判入口）、`IncidentDetailView`（事件概览、特征网格、规则评分仪表盘 + 命中规则、Agent 工具调用轨迹、AI 研判报告含证据/建议/不确定性、触发研判 Loading 禁重、人工审核对话框）、`ReviewsView`（筛选 + 记录表）、`EvaluationView`（元信息条、指标卡、3×3 混淆矩阵、工具/证据/业务指标、评估边界说明；null 显示「—/未测量」）。
- 所有页面处理 Loading / Empty / Error 三态；研判请求超时放宽至 60s（后端 Agent 上限 45s）。

**实际验证（2026-11-19）：** `mvn -q compile` 通过；`mvn test` 65/65 通过；`npm run build`（vue-tsc + vite）通过。启动真实后端实例（18090 端口，因本机 8080 被 Docker Desktop 占用）逐一 curl 验证：health UP 且 modelConfigured=true；dashboard 返回 125 告警 / 13 事件 / 风险分布真实计算；alerts 筛选分页与详情正常且**响应中已无 scenarioId**；evaluation 返回 8 个有效场景样本、规则 P=1.0/R=0.33/F1=0.5、AI/Agent/业务指标在无运行记录时正确返回 null；前端 dev server 代理联调 200。**未执行真实 DashScope 调用，未声称真实模型联调通过。**
