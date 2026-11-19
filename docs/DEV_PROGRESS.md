# SecOps Copilot 开发进度

更新时间：2026-11-19。当前状态：**阶段四已完成，P1 授权运维工具已补充；最终运行验收以最新部署和本次调用轨迹为准**。

> 本文件是最新接续记录。README 下半部分的「阶段一～阶段四实现与验收」为各阶段当时的**历史快照**，其中的「当前」「未通过」「未执行」描述当时状态；最终状态以本文件与 README 开头「交付说明 / 模型配置 / 未完成事项」为准。

## 一、本次核心结论：P0 真实闭环从 401 变为通过

上一轮 `DashScopeLiveIT` 报 401 `InvalidApiKey`，结论是「真实模型闭环未验收通过」。本轮定位到**根因不是密钥失效，而是平台/协议不匹配**：

- 脱敏探测：`SECOPS_API_KEY`（= `SECOPS_COPILOT_API_KEY` = `SILICONFLOW_API_KEY`，三者值相同）在 DashScope 中国大陆与国际两个 endpoint 均返回 401 `invalid_api_key`；在 `https://api.siliconflow.cn/v1/models` 返回 **HTTP 200**。密钥属于硅基流动 / 轨迹流动平台（OpenAI 兼容协议）。
- 处理：新增 `OpenAiCompatibleAdapter`（`RestClient` + Jackson，协议细节集中在 `ai` 适配层），由 `app.model.provider` 切换；`DashScopeAdapter` 保留但加 `@ConditionalOnProperty` 变为非默认。
- 模型选型：实测 `Qwen/Qwen2.5-72B-Instruct`、`Qwen/Qwen3-30B-A3B-Instruct-2507`、`deepseek-ai/DeepSeek-V3.2` 的 Function Calling 与「工具结果回填后输出严格 JSON」。三个模型 FC 均正常，但 DeepSeek-V3.2 会用 ```json 代码块包裹最终输出，会被 `AgentResultValidator` 判无效；两个 Qwen 输出干净。选用 **Qwen3-30B-A3B-Instruct-2507**（MoE 响应快），Qwen2.5-72B 备选。
- 密钥来源统一：`ModelKeyProvider` 支持逗号分隔的候选环境变量名（主名 `SECOPS_API_KEY`，兼容别名 `SECOPS_COPILOT_API_KEY`），两个适配器与 `/api/health` 都从它取；密钥值不出现在日志、响应体、Git 或前端。

**实测产物** `backend/target/dashscope-live-result.json`：`validationStatus=VALID`、`executionMode=TOOL_ENABLED`、`verdict=SUSPICIOUS`、`riskLevel=CRITICAL`；工具轨迹 `query_login_history` 为 `SUCCESS`（168h 回查得到 3 次历史成功登录），证据含 `ALERT` 与 `TOOL` 两类且引用合法。这是 P0「Agent 至少真实调用一个只读工具」的验收证据。

> 注意：集成测试直接调用 `agent.run(...)`，轨迹保存在测试局部变量，**不写运行期 Repository**；评估页的 AI / Agent 指标需在页面上真实触发研判后才产生，不得把该 JSON 说成评估页数据。

## 二、本次变更文件

| 类别 | 文件 |
|---|---|
| 新增 | `backend/src/main/java/com/secops/ai/OpenAiCompatibleAdapter.java` |
| 新增 | `backend/src/test/java/com/secops/evaluation/EvaluationServiceTest.java`（5 项） |
| 修改 | `backend/src/main/java/com/secops/ai/DashScopeAdapter.java`（条件装配 + 密钥来源统一） |
| 修改 | `backend/src/main/java/com/secops/config/ModelKeyProvider.java`（候选变量名 + `apiKey()`） |
| 修改 | `backend/src/main/resources/application.yml`（provider / base-url / name / api-key-env） |
| 修改 | `backend/src/test/java/com/secops/agent/DashScopeLiveIT.java`（注入 `LlmAdapter` 接口、兼容新旧变量名） |
| 修改 | `backend/src/test/java/com/secops/controller/ApplicationSmokeTest.java`（覆盖新旧变量名泄露断言） |
| 修改 | `backend/src/main/java/com/secops/evaluation/EvaluationService.java`（UNCERTAIN 单列、parseRate 口径、引用可追溯率不可计算） |
| 修改 | `.env.example`、`docker-compose.yml`（`SECOPS_API_KEY`） |
| 修改 | `frontend/src/types/evaluation.ts`、`frontend/src/views/EvaluationView.vue`（新字段与不可计算提示） |
| 修改 | `frontend/src/views/SystemStatusView.vue`（阶段四真实状态、侧栏不再灰掉已实现页面） |
| 修改 | `frontend/src/layouts/AppLayout.vue`（健康检测失败显示「后端不可达」） |
| 修改 | `docs/API.md` 13、`docs/ARCHITECTURE.md` 10/9、`README.md`、`docs/DEMO_SCRIPT.md`、本文件 |

## 三、评估指标诚实化（已完成）

1. **引用可追溯率**：`AgentResultValidator` 在保存前剔除无效引用且未持久化「过滤前引用总数」，分母不可还原；只遍历已保存 `evidence()` 会恒为 1.0。现返回 `traceabilityRate=null` + `traceabilityStatus=NOT_COMPUTABLE`，前端显示「不可计算（过滤前引用数未留存）」。
2. **UNCERTAIN 单独统计**：`ruleEvaluation` 新增 `uncertainSampleCount`，`sampleCount` 仍只含二分类样本；同步 `API.md` 13、前端类型与页面。
3. **解析成功率口径**：改为 `(read - rejected) / read`，重复 `alertId` 被去重但不再被误算成解析失败；`parsedAlertCount` 仍是去重后入库数。
4. **评估模块测试**：新增 5 项，覆盖空样本 / 除零、UNCERTAIN 单列、证据可追溯率恒为 null、重复 ID 不误算、全拒绝记录，全部使用 Mock，不触发真实模型。

## 四、本轮实际测试（非沿用旧结果，13:10 后复测）

| 工作目录 | 命令 | 结果 |
|---|---|---|
| backend | `mvn -B test` | **72/72 通过**，0 失败 / 0 错误 / 0 跳过（含缺失工具绑定字段降级与授权运维工具边界测试） |
| backend | `mvn -B -Dtest=DashScopeLiveIT test` | **连跑 3 次全部通过**（29.96s / 23.06s / 31.54s）；真实 Function Calling + 证据校验，产出 `VALID` / `TOOL_ENABLED` 结果；31.54s 那次在旧 30s 上限下必然失败 |
| frontend | `npm run build` | **通过**（`vue-tsc --noEmit` + `vite build`）；主 chunk 约 2.23 MB，仍有大 chunk 警告 |
| 根目录 | `docker compose config -q` | 通过；`eclipse-temurin:17-jre-jammy` 实测自带 curl / wget，两个 healthcheck 可用 |

## 四之二、超时口径调整（模型选型实测驱动）

- 对平台 91 个模型按真实两轮链路实测：多数更强模型（Qwen3.6/3.8、DeepSeek-V4-Pro、GLM-5.3、Kimi-K2.6 等）第二轮 45s 无响应；`DeepSeek-V3.2` 包 ```json 代码块被判无效；`DeepSeek-V4-Flash` 真实 IT 判字段无效；`GLM-4.5-Air` 仅 `PARTIALLY_VALID`。唯一可用升级 `Qwen3.5-35B-A3B`（VALID）更慢、收益有限，**决定不换模型**。
- 同时发现基线在 30000ms 下抖动：29.42s 通过 / 33.0s、32.33s 超时失败。经用户确认，放宽 `total-timeout-ms` 到 **45000**、单次读超时 25000→40000、代码硬上限 30000→60000；前端研判 60s、Nginx 90s 不变。
- 已同步 `application.yml`、`SocTriageAgent`、`OpenAiCompatibleAdapter`、`DashScopeAdapter`、`ARCHITECTURE.md`、`API.md`、`DEMO_SCRIPT.md`、README、前端注释。单工具 3000ms 与 2 轮 / 4 次预算未变。

## 五、约束与文档一致性检查

- 未引入 MySQL / Redis / MQ / Milvus / Multi-Agent / 权限系统；JSON fixtures 只读；运行期内存数据重启重置（README 已说明）。
- `scenarioId` 与 Ground Truth 仍只被 `evaluation` 包访问；`AIAnalysisContext` / Prompt / 工具参数未出现 `expectedLabel` / `notes`。
- SDK 类型仍集中在 `ai` 适配层；`OpenAiCompatibleAdapter` 使用 Spring 自带 `RestClient` + Jackson，未编造 SDK 类；`function-calling-enabled=false` 的 `NO_TOOL_FALLBACK` 降级逻辑未改动。
- **已按 `AGENTS.md` 2.1 报告并同步的文档差异**：① `ARCHITECTURE.md` 10 / `AGENTS.md` 4 的 DashScope 原生约定 → 改为 OpenAI 兼容平台（用户明确授权）；② `API.md` 13 新增 `uncertainSampleCount` 与 `traceabilityStatus`；③ Agent 总耗时上限 30s→45s（用户确认，已同步 `ARCHITECTURE.md` 正文与 9 节、`API.md` 两处、`DEMO_SCRIPT.md`）；④ `API.md` 10 的 `AIAnalysis` 响应示例字段仍滞后于 `DATABASE.md` 5.5（既有偏差，本期未改）。
- 未知路径 HTTP 404 + `INVALID_ARGUMENT` 与 `API.md` 1.4 的偏差保留（既有，README「未完成事项」已声明）。

## 六、仍未解决的问题

1. P1 的第二个只读工具 `query_authorized_operations` 已实现。是否在单次研判中被调用，由模型按需决定；没有本次 `SUCCESS` 轨迹就不能声称查询过授权运维记录。
2. `AnalysisService` 失败记录 `executionMode` 硬编码 `TOOL_ENABLED`，无工具降级失败后元数据无法区分。
3. `agentEvaluation` 的 `toolCallCount` 分母含 `REJECTED`（明确未执行的调用），与「只统计真实发生的工具调用」口径仍有歧义；本期未改数值，仅记录。
4. 证据语义支持率未人工抽样（`NOT_EVALUATED`）；效率提升率无人工计时（`NOT_MEASURED`）。
5. 前端主 chunk 约 2.2 MB（Element Plus / ECharts 全量引入）；已做一次浏览器实际交互验证，尚无长期维护的浏览器自动化测试套件。
6. 运行期数据在内存，重启重置；无数据库、无权限系统、无真实安全处置。
7. 尚无 Git 仓库。本轮全量测试与真实集成测试均已通过，可初始化仓库并提交（提交范围：源码、只读 fixtures、测试、构建配置、文档；不纳入面试 PDF、截图、原型参考、IDE 元数据、密钥、node_modules、target、dist）。

## 七、本次最终审查整改复测（2026-11-19）

- P1 授权运维只读工具已按 `API.md` 14.2 实现，参数绑定当前事件资产和时间窗口，只返回 `APPROVED` / `COMPLETED` 且时间相交的记录；返回时间统一为 UTC。Prompt 升级为 `ssh-triage-v4`，上下文新增从告警提取的用户名集合，不读取 Ground Truth。
- 最新 Docker 镜像重建并替换旧 `qwen-plus` 服务；两容器 healthy，运行模型为 `Qwen/Qwen3-30B-A3B-Instruct-2507`。`mvn -B test` 为 72/72，`npm run build` 通过。
- 真实 HTTP 研判同一授权运维事件：第一次在两项工具成功后模型读取异常，返回 503；第二次重试成功，报告 `LIKELY_BENIGN` / `VALID`，两项工具轨迹均 `SUCCESS`。浏览器提交 `FALSE_POSITIVE` 后，事件状态和审核记录同步更新。现场演示仍需预检，不保证模型服务每次可用。
- 演示脚本改为根据本次实际轨迹讲解，不保证模型在任意事件必定调用工具。未修改或删除原始 fixtures。

## 八、AI Coding 工具使用记录

- OpenAI Codex：阶段一至三后端主干。
- WorkBuddy：阶段四前端 / 评估 / Docker / 截图；本次逐模块只读审查、密钥归属脱敏诊断、模型 FC 与严格 JSON 实测选型、`OpenAiCompatibleAdapter` 实现、评估指标诚实化与测试、文档同步。
- 每阶段均以 `mvn test` / `npm run build` 校验后人工复核；不把 Mock / 静态数据描述为真实模型调用或真实评估结果。
