<template>
  <div v-loading="loading" element-loading-text="正在计算评估指标…">
    <div class="hero">
      <div class="eyebrow">05 / EVALUATION</div>
      <h1>全链路效果评估</h1>
      <p>以可复现的模拟数据衡量告警处理链路的质量与效率。所有指标均由后端基于实际运行产物与隔离的 Ground Truth 实时计算，空样本显示为空。</p>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-bar">
      <el-button size="small" @click="load">重新加载</el-button>
    </el-alert>

    <template v-else-if="data">
      <!-- 元信息条 -->
      <section class="meta-bar">
        <div class="meta-item"><span>数据集版本</span><b class="mono">{{ data.meta.datasetVersion }}</b></div>
        <div class="meta-item"><span>有效评估样本</span><b class="mono accent">{{ data.meta.sampleCount }}</b></div>
        <div class="meta-item"><span>模型</span><b class="mono">{{ data.meta.modelName }}</b></div>
        <div class="meta-item"><span>Prompt</span><b class="mono">{{ data.meta.promptVersion }}</b></div>
        <div class="meta-item"><span>评估时间</span><b class="mono">{{ formatTime(data.meta.evaluatedAt) }}</b></div>
      </section>

      <!-- 数据与聚合指标 -->
      <section class="metric-grid">
        <div class="metric">
          <div class="m-label">数据解析成功率<span>PARSE SUCCESS</span></div>
          <div class="m-value mono">{{ formatPercent(data.dataQuality.parseSuccessRate) }}</div>
          <div class="m-hint">{{ data.dataQuality.parsedAlertCount }} / {{ data.dataQuality.inputAlertCount }} 条告警被接受</div>
        </div>
        <div class="metric">
          <div class="m-label">必填字段完整率<span>FIELD COMPLETENESS</span></div>
          <div class="m-value mono">{{ formatPercent(data.dataQuality.requiredFieldCompleteness) }}</div>
          <div class="m-hint">通过必填字段与枚举校验的原始记录占比</div>
        </div>
        <div class="metric">
          <div class="m-label">告警压缩率<span>COMPRESSION</span></div>
          <div class="m-value mono">{{ formatPercent(data.aggregation.compressionRate) }}</div>
          <div class="m-hint">{{ data.aggregation.rawSshAlertCount }} 条 SSH 告警 → {{ data.aggregation.incidentCount }} 个事件</div>
        </div>
        <div class="metric">
          <div class="m-label">规则 F1<span>RULE · 阈值 ≥ 50</span></div>
          <div class="m-value mono">{{ metricOrDash(data.ruleEvaluation.f1) }}</div>
          <div class="m-hint">样本 {{ data.ruleEvaluation.sampleCount }} · UNCERTAIN {{ data.ruleEvaluation.uncertainSampleCount }}（单列） · P {{ metricOrDash(data.ruleEvaluation.precision) }} · R {{ metricOrDash(data.ruleEvaluation.recall) }}</div>
        </div>
        <div class="metric">
          <div class="m-label">AI 有效研判样本<span>AI SAMPLES</span></div>
          <div class="m-value mono accent">{{ data.aiEvaluation.sampleCount }}</div>
          <div class="m-hint">准确率 {{ formatPercent(data.aiEvaluation.accuracy) }} · Macro-F1 {{ metricOrDash(data.aiEvaluation.macroF1) }}</div>
        </div>
      </section>

      <div class="row-2">
        <!-- 研判分类表现 -->
        <section class="panel">
          <div class="panel-title">研判分类表现<span class="en">CLASSIFICATION</span></div>
          <template v-if="hasMatrix">
            <table class="matrix">
              <thead>
                <tr><th>真实 \ 预测</th><th>ATTACK</th><th>BENIGN</th><th>UNCERTAIN</th></tr>
              </thead>
              <tbody>
                <tr v-for="row in LABELS" :key="row">
                  <th>{{ row }}</th>
                  <td v-for="col in LABELS" :key="col"
                    :class="{ hit: row === col && cellValue(row, col) > 0, miss: row !== col && cellValue(row, col) > 0 }">
                    {{ cellValue(row, col) }}
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="matrix-hint mono">
              AI 样本 {{ data.aiEvaluation.sampleCount }} · Accuracy {{ formatPercent(data.aiEvaluation.accuracy) }} ·
              P {{ metricOrDash(data.aiEvaluation.precision) }} · R {{ metricOrDash(data.aiEvaluation.recall) }} ·
              Macro-F1 {{ metricOrDash(data.aiEvaluation.macroF1) }}
            </div>
          </template>
          <div v-else class="empty-hint">
            暂无有效 AI 研判样本。请先在事件详情页触发 Agent 研判，评估只统计实际运行并成功保存的结果。
          </div>
        </section>

        <!-- Agent 工具调用 -->
        <section class="panel">
          <div class="panel-title">Agent 工具调用<span class="en">AGENT TOOL USAGE</span></div>
          <div class="kv-list">
            <div class="kv-row"><span>实际工具调用总数</span><b class="mono">{{ data.agentEvaluation.toolCallCount }}</b></div>
            <div class="kv-row"><span>工具调用成功率</span><b class="mono">{{ formatPercent(data.agentEvaluation.toolSuccessRate) }}</b></div>
            <div class="kv-row"><span>参数有效率</span><b class="mono">{{ formatPercent(data.agentEvaluation.parameterValidityRate) }}</b></div>
            <div class="kv-row"><span>工具计划 F1</span><b class="mono">{{ metricOrDash(data.agentEvaluation.toolPlanF1) }}</b></div>
            <div class="kv-row"><span>计划 Precision / Recall</span>
              <b class="mono">{{ metricOrDash(data.agentEvaluation.toolPlanPrecision) }} / {{ metricOrDash(data.agentEvaluation.toolPlanRecall) }}</b>
            </div>
            <div class="kv-row"><span>平均调用次数 / 研判</span><b class="mono">{{ metricOrDash(data.agentEvaluation.averageToolCalls) }}</b></div>
            <div class="kv-row"><span>平均研判耗时</span><b class="mono">{{ formatDuration(data.agentEvaluation.averageDurationMs) }}</b></div>
          </div>
        </section>
      </div>

      <div class="row-2">
        <!-- 证据质量 -->
        <section class="panel">
          <div class="panel-title">证据质量<span class="en">EVIDENCE QUALITY</span></div>
          <div class="kv-list">
            <div class="kv-row"><span>引用可追溯率</span>
              <b v-if="data.evidenceEvaluation.traceabilityStatus === 'NOT_COMPUTABLE'" class="na">不可计算（过滤前引用数未留存）</b>
              <b v-else class="mono">{{ formatPercent(data.evidenceEvaluation.traceabilityRate) }}</b>
            </div>
            <div class="kv-row"><span>语义支持率</span>
              <b v-if="data.evidenceEvaluation.semanticSupportStatus === 'NOT_EVALUATED'" class="na">未评估（需人工抽样）</b>
              <b v-else class="mono">{{ formatPercent(data.evidenceEvaluation.semanticSupportRate) }}</b>
            </div>
          </div>
          <p class="note">ID 可追溯仅表示引用存在，不代表语义解释必然正确。</p>
        </section>

        <!-- 业务指标 -->
        <section class="panel">
          <div class="panel-title">业务指标<span class="en">BUSINESS</span></div>
          <div class="kv-list">
            <div class="kv-row"><span>AI-审核配对样本</span><b class="mono">{{ data.businessEvaluation.reviewSampleCount }}</b></div>
            <div class="kv-row"><span>AI 结论采纳率</span><b class="mono">{{ formatPercent(data.businessEvaluation.adoptionRate) }}</b></div>
            <div class="kv-row"><span>AI 结论修正率</span><b class="mono">{{ formatPercent(data.businessEvaluation.correctionRate) }}</b></div>
            <div class="kv-row"><span>效率提升率</span>
              <b v-if="data.businessEvaluation.measurementStatus === 'NOT_MEASURED'" class="na">未测量（无人工计时数据）</b>
              <b v-else class="mono">{{ formatPercent(data.businessEvaluation.efficiencyImprovementRate) }}</b>
            </div>
          </div>
        </section>
      </div>

      <!-- 评估边界 -->
      <section class="panel">
        <div class="panel-title">评估边界与说明<span class="en">SCOPE &amp; NOTES</span></div>
        <ol class="notes">
          <li>仅统计实际运行且校验通过的 AI 研判结果；未触发研判的事件不计入 AI 准确率。</li>
          <li>Ground Truth 与 Agent 上下文隔离，仅评估模块可访问；评估接口不触发模型调用。</li>
          <li>规则评估以规则分数 ≥ 50 作为 Demo 高风险阈值；UNCERTAIN 标签单独计数，不纳入二分类。</li>
          <li>引用可追溯率因过滤前引用总数未持久化、分母不可还原，本期返回「不可计算」，不用已保存的有效引用凑出 1.0。</li>
          <li>所有结果仅代表合成数据集测试，不代表生产环境泛化效果；效率指标需真实计时数据，本期未测量。</li>
        </ol>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchEvaluation } from '@/api/evaluation'
import type { EvaluationDto } from '@/types/evaluation'
import { formatDuration, formatPercent, formatTime } from '@/utils/format'

const LABELS = ['ATTACK', 'BENIGN', 'UNCERTAIN']

const loading = ref(true)
const error = ref('')
const data = ref<EvaluationDto | null>(null)

const hasMatrix = computed(() => {
  const m = data.value?.aiEvaluation.confusionMatrix
  if (!m) return false
  return Object.values(m).some((cols) => Object.values(cols).some((v) => v > 0))
})

function cellValue(row: string, col: string): number {
  return data.value?.aiEvaluation.confusionMatrix?.[row]?.[col] ?? 0
}

function metricOrDash(value: number | null | undefined): string {
  return value === null || value === undefined || Number.isNaN(value) ? '—' : value.toFixed(3)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchEvaluation()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hero { padding: 28px 0 20px; }
.eyebrow { font-size: 11px; letter-spacing: 2.5px; color: var(--soc-accent); }
.hero h1 { margin: 8px 0; font-size: 29px; letter-spacing: 1.5px; }
.hero p { margin: 0; color: var(--soc-muted); font-size: 13px; max-width: 780px; line-height: 1.7; }

.error-bar { margin-bottom: 16px; }

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  border: 1px solid var(--soc-border);
  border-radius: 11px;
  background: var(--soc-panel);
  margin-bottom: 16px;
  overflow: hidden;
}
.meta-item {
  flex: 1 1 180px;
  padding: 13px 18px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  border-right: 1px solid var(--soc-border);
}
.meta-item:last-child { border-right: none; }
.meta-item span { font-size: 11px; color: var(--soc-faint); }
.meta-item b { font-size: 13.5px; }
.meta-item .accent { color: var(--soc-accent); }

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.metric {
  padding: 16px 18px;
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  background: var(--soc-panel);
  transition: border-color 0.25s;
}
.metric:hover { border-color: var(--soc-border-bright); }
.m-label { font-size: 12px; color: var(--soc-muted); display: flex; flex-direction: column; gap: 2px; }
.m-label span { font-size: 9.5px; letter-spacing: 1.5px; color: var(--soc-faint); }
.m-value { font-size: 30px; font-weight: 800; margin: 8px 0 6px; }
.m-value.accent { color: var(--soc-accent); }
.m-hint { font-size: 11px; color: var(--soc-faint); line-height: 1.6; }

.row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
@media (max-width: 1000px) { .row-2 { grid-template-columns: 1fr; } }

.panel {
  background: var(--soc-panel);
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.panel-title {
  font-size: 14.5px;
  font-weight: 700;
  margin-bottom: 16px;
  padding-left: 11px;
  border-left: 3px solid var(--soc-accent);
  display: flex;
  align-items: baseline;
}
.panel-title .en {
  margin-left: 8px;
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 1.5px;
  color: var(--soc-faint);
}

.matrix {
  width: 100%;
  border-collapse: collapse;
  text-align: center;
  font-size: 13px;
}
.matrix th, .matrix td {
  border: 1px solid var(--soc-border);
  padding: 10px 8px;
}
.matrix th { color: var(--soc-muted); font-weight: 600; font-size: 11.5px; background: rgba(8, 20, 36, 0.7); }
.matrix td { font-family: Consolas, monospace; font-weight: 700; color: var(--soc-faint); }
.matrix td.hit { color: var(--soc-green); background: rgba(61, 220, 151, 0.09); }
.matrix td.miss { color: var(--soc-red); background: rgba(255, 92, 116, 0.08); }
.matrix-hint { margin-top: 12px; font-size: 11px; color: var(--soc-faint); }

.kv-list { display: flex; flex-direction: column; }
.kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 2px;
  border-bottom: 1px dashed var(--soc-border);
  font-size: 13px;
}
.kv-row:last-child { border-bottom: none; }
.kv-row span { color: var(--soc-muted); }
.kv-row b { font-size: 14px; }
.kv-row .na { color: var(--soc-amber); font-size: 12px; font-weight: 600; }

.note { margin: 12px 0 0; font-size: 11.5px; color: var(--soc-faint); }
.notes { margin: 0; padding-left: 20px; color: var(--soc-muted); font-size: 13px; line-height: 2.1; }
.notes li::marker { color: var(--soc-accent); font-weight: 700; }

.empty-hint {
  padding: 36px 16px;
  text-align: center;
  color: var(--soc-faint);
  font-size: 13px;
  line-height: 1.9;
  border: 1px dashed var(--soc-border);
  border-radius: 9px;
}
</style>
