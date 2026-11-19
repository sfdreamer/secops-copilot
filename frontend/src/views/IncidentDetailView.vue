<template>
  <div v-loading="loading" element-loading-text="正在加载事件…">
    <div class="hero">
      <div class="crumbs">
        <RouterLink to="/alerts?tab=incidents" class="back">告警与事件</RouterLink>
        <span class="sep">/</span><span>事件详情</span>
      </div>
      <div class="eyebrow">03 / INCIDENT ANALYSIS</div>
      <template v-if="detail">
        <h1>{{ labelOf(ALERT_TYPE_LABEL, detail.incident.alertType) }}事件</h1>
        <p>
          事件 <span class="mono accent-text">{{ detail.incident.incidentId }}</span> ·
          聚合 {{ detail.incident.alertCount }} 条告警 ·
          {{ formatTime(detail.incident.firstSeen) }} 至 {{ formatTime(detail.incident.lastSeen) }}
        </p>
      </template>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-bar">
      <el-button size="small" @click="load">重新加载</el-button>
    </el-alert>

    <template v-if="detail">
      <!-- 概览 + 规则评分 -->
      <section class="row-2">
        <div class="panel">
          <div class="panel-title">事件概览<span class="en">INCIDENT OVERVIEW</span>
            <UiTag class="title-tag" :text="labelOf(INCIDENT_STATUS_LABEL, detail.incident.status)"
              :color="INCIDENT_STATUS_COLOR[detail.incident.status]" />
          </div>
          <div class="overview-grid">
            <div class="ov"><span>事件编号</span><b class="mono">{{ detail.incident.incidentId }}</b></div>
            <div class="ov"><span>告警类型</span><b><UiTag :text="labelOf(ALERT_TYPE_LABEL, detail.incident.alertType)" color="#38c6f1" /></b></div>
            <div class="ov"><span>来源 IP</span><b class="mono">{{ detail.incident.sourceIp ?? '—' }}</b></div>
            <div class="ov"><span>目标资产</span><b>{{ detail.incident.targetAsset ?? '—' }}<i v-if="detail.incident.targetIp" class="mono dim">（{{ detail.incident.targetIp }}）</i></b></div>
            <div class="ov"><span>首次发现</span><b class="mono">{{ formatTime(detail.incident.firstSeen) }}</b></div>
            <div class="ov"><span>最后发现</span><b class="mono">{{ formatTime(detail.incident.lastSeen) }}</b></div>
          </div>
          <div class="feature-grid">
            <div class="ft"><span>登录失败</span><b class="mono bad-text">{{ detail.features.failedAttempts }}</b></div>
            <div class="ft"><span>登录成功</span><b class="mono ok-text">{{ detail.features.successfulAttempts }}</b></div>
            <div class="ft"><span>不同用户名</span><b class="mono">{{ detail.features.distinctUserCount }}</b></div>
            <div class="ft"><span>持续时长</span><b class="mono">{{ formatNumber(detail.features.windowMinutes, 2) }}<i class="unit">分钟</i></b></div>
            <div class="ft"><span>失败速率</span><b class="mono">{{ formatNumber(detail.features.failureRate, 1) }}<i class="unit">次/分</i></b></div>
            <div class="ft"><span>失败后成功</span>
              <b><UiTag :text="detail.features.successAfterFailure ? '是' : '否'"
                :color="detail.features.successAfterFailure ? '#ff5c74' : '#3ddc97'" /></b>
            </div>
            <div class="ft"><span>公网暴露</span>
              <b><UiTag :text="exposureText" :color="exposureColor" /></b>
            </div>
            <div class="ft"><span>资产重要性</span><b>{{ detail.features.assetCriticality }}</b></div>
          </div>
        </div>

        <div class="panel risk-panel">
          <div class="panel-title">规则风险评分<span class="en">RULE RISK SCORE</span></div>
          <div class="risk-body">
            <ChartBox :option="gaugeOption" :height="210" />
            <div class="rules">
              <div class="rules-head">命中规则（{{ detail.riskAssessment.matchedRules.length }}）</div>
              <div v-for="rule in detail.riskAssessment.matchedRules" :key="rule.ruleCode" class="rule-item">
                <span class="rule-dot" />
                <span class="rule-desc">{{ rule.description }}</span>
                <span class="mono rule-points">+{{ rule.points }}</span>
              </div>
              <div v-if="!detail.riskAssessment.matchedRules.length" class="empty-hint-sm">未命中任何规则</div>
            </div>
          </div>
        </div>
      </section>

      <!-- Agent + 工具轨迹 -->
      <section class="panel">
        <div class="panel-title">
          SOC TRIAGE AGENT<span class="en">BOUNDED REACT · FUNCTION CALLING</span>
          <div class="title-actions">
            <UiTag v-if="detail.latestAnalysis" :text="labelOf(EXECUTION_MODE_LABEL, detail.latestAnalysis.executionMode)" color="#6ee7ff" />
            <el-button type="primary" :disabled="!canAnalyze" :loading="analyzing" @click="runAnalyze">
              {{ analyzing ? 'Agent 研判中…' : analyzeButtonText }}
            </el-button>
          </div>
        </div>

        <el-alert v-if="analyzeError" :title="analyzeError" type="error" show-icon :closable="false" class="error-bar" />

        <div v-if="detail.toolCallTraces.length" class="traces">
          <div v-for="t in detail.toolCallTraces" :key="t.toolCallId" class="trace">
            <div class="trace-head">
              <span class="mono trace-name">&lt;/&gt; {{ t.toolName }}</span>
              <span class="trace-cn">{{ labelOf(TOOL_NAME_LABEL, t.toolName) }}</span>
              <UiTag :text="labelOf(EXECUTION_STATUS_LABEL, t.executionStatus)" :color="EXECUTION_STATUS_COLOR[t.executionStatus]" />
              <span class="mono trace-meta">耗时 {{ formatDuration(t.durationMs) }}</span>
              <span class="mono trace-meta dim">{{ formatTime(t.calledAt) }}</span>
            </div>
            <div class="trace-body">
              <div class="trace-col">
                <span class="trace-label">调用参数（已与事件绑定）</span>
                <pre class="code-block">{{ prettyJson(t.argumentsSummary) }}</pre>
              </div>
              <div class="trace-col" v-if="t.resultSummary !== null && t.resultSummary !== undefined">
                <span class="trace-label">返回结果摘要</span>
                <pre class="code-block">{{ prettyJson(t.resultSummary) }}</pre>
              </div>
              <div class="trace-col" v-if="t.errorCode">
                <span class="trace-label">错误码</span>
                <pre class="code-block err">{{ t.errorCode }}</pre>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">
          {{ detail.latestAnalysis ? '本次研判未调用工具' : '尚未执行 Agent 研判，点击右上角按钮触发真实工具调用' }}
        </div>
      </section>

      <!-- AI 研判报告 -->
      <section class="panel" v-if="detail.latestAnalysis">
        <div class="panel-title">AI 研判报告<span class="en">AI ANALYSIS REPORT</span>
          <UiTag class="title-tag" :text="labelOf(VALIDATION_STATUS_LABEL, detail.latestAnalysis.validationStatus)"
            :color="detail.latestAnalysis.validationStatus === 'VALID' ? '#3ddc97' : detail.latestAnalysis.validationStatus === 'PARTIALLY_VALID' ? '#f5b544' : '#ff5c74'" />
        </div>

        <template v-if="detail.latestAnalysis.analysisStatus === 'SUCCEEDED'">
          <div class="verdict-row">
            <div class="verdict-box">
              <span class="vb-label">研判结论</span>
              <UiTag v-if="detail.latestAnalysis.verdict" :text="labelOf(VERDICT_LABEL, detail.latestAnalysis.verdict)"
                :color="VERDICT_COLOR[detail.latestAnalysis.verdict]" />
            </div>
            <div class="verdict-box">
              <span class="vb-label">AI 风险等级</span>
              <UiTag v-if="detail.latestAnalysis.riskLevel" :text="detail.latestAnalysis.riskLevel"
                :color="RISK_COLOR[detail.latestAnalysis.riskLevel]" />
              <span v-else class="dim">—</span>
            </div>
            <div class="verdict-box">
              <span class="vb-label">攻击类型</span>
              <b>{{ labelOf(ALERT_TYPE_LABEL, detail.latestAnalysis.attackType) }}</b>
            </div>
            <div class="verdict-box">
              <span class="vb-label">工具调用</span>
              <b class="mono">{{ detail.latestAnalysis.toolCallCount }} 次</b>
            </div>
            <div class="verdict-box">
              <span class="vb-label">总耗时</span>
              <b class="mono">{{ formatDuration(detail.latestAnalysis.durationMs) }}</b>
            </div>
          </div>

          <div class="report-block">
            <div class="rb-title">研判结论摘要</div>
            <p class="rb-text">{{ detail.latestAnalysis.summary }}</p>
          </div>
          <div class="report-block">
            <div class="rb-title">分析摘要<span class="en-note">面向分析师，非模型思维链</span></div>
            <p class="rb-text">{{ detail.latestAnalysis.reasoningSummary }}</p>
          </div>

          <div class="report-block" v-if="detail.latestAnalysis.evidence.length">
            <div class="rb-title">关键证据（{{ detail.latestAnalysis.evidence.length }}）</div>
            <div v-for="(ev, i) in detail.latestAnalysis.evidence" :key="i" class="evidence">
              <UiTag :text="ev.sourceType === 'ALERT' ? '告警证据' : '工具证据'"
                :color="ev.sourceType === 'ALERT' ? '#38c6f1' : '#9a8cff'" />
              <div class="ev-body">
                <div class="ev-fact">{{ ev.fact }}</div>
                <div class="ev-exp">{{ ev.explanation }}</div>
                <div class="mono ev-refs">
                  <span v-for="id in ev.alertIds" :key="id">{{ id }}</span>
                  <span v-for="id in ev.toolCallIds" :key="id">{{ id }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="two-col">
            <div class="report-block" v-if="detail.latestAnalysis.recommendations.length">
              <div class="rb-title">处置建议</div>
              <ul class="rb-list">
                <li v-for="(r, i) in detail.latestAnalysis.recommendations" :key="i">{{ r }}</li>
              </ul>
            </div>
            <div class="report-block" v-if="detail.latestAnalysis.uncertainties.length">
              <div class="rb-title">不确定性</div>
              <ul class="rb-list warn-list">
                <li v-for="(u, i) in detail.latestAnalysis.uncertainties" :key="i">{{ u }}</li>
              </ul>
            </div>
          </div>

          <div class="report-meta mono">
            模型 {{ detail.latestAnalysis.modelName }} · Prompt {{ detail.latestAnalysis.promptVersion }} ·
            研判编号 {{ detail.latestAnalysis.analysisId }} · {{ formatTime(detail.latestAnalysis.analyzedAt) }}
          </div>
        </template>
        <el-alert v-else type="error" show-icon :closable="false"
          :title="`研判失败：${detail.latestAnalysis.failureReason ?? '未知原因'}。事件、特征与规则评分已保留，可稍后重试。`" />
      </section>

      <!-- 关联告警 -->
      <section class="panel">
        <div class="panel-title">关联原始告警<span class="en">RELATED ALERTS · {{ detail.incident.alertCount }}</span></div>
        <el-table :data="detail.relatedAlerts" max-height="320">
          <el-table-column label="告警 ID" min-width="150">
            <template #default="{ row }"><span class="mono id-text">{{ row.alertId }}</span></template>
          </el-table-column>
          <el-table-column label="发生时间" min-width="160">
            <template #default="{ row }"><span class="mono time">{{ formatTime(row.timestamp) }}</span></template>
          </el-table-column>
          <el-table-column label="协议" width="80">
            <template #default="{ row }">{{ row.attributes?.protocol ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="用户名" min-width="110">
            <template #default="{ row }"><span class="mono">{{ row.attributes?.username ?? '—' }}</span></template>
          </el-table-column>
          <el-table-column label="动作" min-width="120">
            <template #default="{ row }">
              <UiTag v-if="row.attributes?.action === 'LOGIN_FAILED'" text="登录失败" color="#ff5c74" />
              <UiTag v-else-if="row.attributes?.action === 'LOGIN_SUCCESS'" text="登录成功" color="#3ddc97" />
              <span v-else>{{ row.attributes?.action ?? '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 人工审核 -->
      <section class="panel review-panel">
        <div class="panel-title">人工审核<span class="en">HUMAN REVIEW · 最终结论以人工为准</span></div>
        <div class="review-row">
          <p class="review-note">
            AI 仅提供辅助判断。允许从「待分析 / 待审核 / 分析失败 / 进一步调查」状态提交审核；确认威胁与标记误报为终态。
          </p>
          <el-button type="primary" :disabled="!canReview" @click="reviewOpen = true">提交人工审核</el-button>
        </div>
      </section>
    </template>

    <!-- 审核对话框 -->
    <el-dialog v-model="reviewOpen" title="提交人工审核" width="480px">
      <el-form label-position="top">
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="reviewForm.reviewResult">
            <el-radio-button value="CONFIRMED_THREAT">确认威胁</el-radio-button>
            <el-radio-button value="FALSE_POSITIVE">标记误报</el-radio-button>
            <el-radio-button value="INVESTIGATING">进一步调查</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核人" required>
          <el-input v-model="reviewForm.reviewer" maxlength="100" placeholder="Demo 审核人标识，如 analyst" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewForm.remark" type="textarea" :rows="3" maxlength="2000"
            placeholder="审核依据、补充说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewOpen = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" :disabled="!reviewForm.reviewer.trim()" @click="submitReview">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import ChartBox from '@/components/ChartBox.vue'
import UiTag from '@/components/UiTag.vue'
import { analyzeIncident, fetchIncidentDetail, reviewIncident } from '@/api/incidents'
import type { IncidentDetail } from '@/types/incidents'
import {
  ALERT_TYPE_LABEL, EXECUTION_MODE_LABEL, EXECUTION_STATUS_COLOR, EXECUTION_STATUS_LABEL,
  INCIDENT_STATUS_COLOR, INCIDENT_STATUS_LABEL, RISK_COLOR, TOOL_NAME_LABEL,
  VALIDATION_STATUS_LABEL, VERDICT_COLOR, VERDICT_LABEL, labelOf,
} from '@/utils/labels'
import { formatDuration, formatNumber, formatTime } from '@/utils/format'

const route = useRoute()
const id = String(route.params.id)

const loading = ref(true)
const error = ref('')
const detail = ref<IncidentDetail | null>(null)

const analyzing = ref(false)
const analyzeError = ref('')

const canAnalyze = computed(() =>
  !!detail.value && ['PENDING_ANALYSIS', 'ANALYSIS_FAILED', 'INVESTIGATING'].includes(detail.value.incident.status) && !analyzing.value,
)
const analyzeButtonText = computed(() => {
  if (!detail.value) return '触发 Agent 研判'
  if (detail.value.incident.status === 'ANALYZING') return '研判中…'
  if (detail.value.incident.status === 'PENDING_REVIEW') return '已完成研判'
  if (['CONFIRMED_THREAT', 'FALSE_POSITIVE'].includes(detail.value.incident.status)) return '事件已审结'
  return detail.value.latestAnalysis ? '重新研判' : '触发 Agent 研判'
})

const canReview = computed(() =>
  !!detail.value &&
  ['PENDING_ANALYSIS', 'PENDING_REVIEW', 'ANALYSIS_FAILED', 'INVESTIGATING'].includes(detail.value.incident.status),
)

const exposureText = computed(() => {
  const v = detail.value?.features.publicExposure
  return v === null || v === undefined ? '未知' : v ? '是' : '否'
})
const exposureColor = computed(() => {
  const v = detail.value?.features.publicExposure
  return v === null || v === undefined ? '#8aa6c6' : v ? '#ff5c74' : '#3ddc97'
})

const gaugeOption = computed<EChartsOption>(() => {
  const score = detail.value?.riskAssessment.score ?? 0
  const level = detail.value?.riskAssessment.level ?? 'LOW'
  const color = RISK_COLOR[level]
  return {
    series: [{
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0,
      max: 100,
      radius: '100%',
      center: ['50%', '62%'],
      progress: {
        show: true,
        width: 14,
        itemStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [{ offset: 0, color: '#1b9fd6' }, { offset: 1, color }],
          },
          shadowColor: color,
          shadowBlur: 12,
        },
      },
      axisLine: { lineStyle: { width: 14, color: [[1, 'rgba(28,53,82,.65)']] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: '#54708e', fontSize: 10, distance: 18 },
      pointer: { show: false },
      anchor: { show: false },
      title: { show: true, offsetCenter: [0, '32%'], color, fontSize: 13, fontWeight: 700 },
      detail: {
        valueAnimation: true,
        offsetCenter: [0, '-2%'],
        fontSize: 40,
        fontWeight: 800,
        color: '#e8f2ff',
        formatter: '{value}',
      },
      data: [{ value: score, name: level }],
    }],
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    detail.value = await fetchIncidentDetail(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function runAnalyze(): Promise<void> {
  analyzing.value = true
  analyzeError.value = ''
  try {
    await analyzeIncident(id)
    ElMessage.success('Agent 研判完成，结果已更新')
    await load()
  } catch (e) {
    analyzeError.value = e instanceof Error ? e.message : '研判失败'
    await load() // 失败也要刷新状态（ANALYSIS_FAILED 与失败轨迹）
  } finally {
    analyzing.value = false
  }
}

/* ---------- 人工审核 ---------- */
const reviewOpen = ref(false)
const reviewing = ref(false)
const reviewForm = reactive({
  reviewResult: 'CONFIRMED_THREAT' as 'CONFIRMED_THREAT' | 'FALSE_POSITIVE' | 'INVESTIGATING',
  reviewer: 'analyst',
  remark: '',
})

async function submitReview(): Promise<void> {
  reviewing.value = true
  try {
    await reviewIncident(id, {
      reviewResult: reviewForm.reviewResult,
      reviewer: reviewForm.reviewer.trim(),
      remark: reviewForm.remark.trim() || undefined,
    })
    ElMessage.success('审核已提交')
    reviewOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '审核提交失败')
  } finally {
    reviewing.value = false
  }
}

function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

onMounted(load)
</script>

<style scoped>
.hero { padding: 24px 0 18px; }
.crumbs { font-size: 12px; color: var(--soc-faint); margin-bottom: 10px; }
.crumbs .back { color: var(--soc-muted); text-decoration: none; }
.crumbs .back:hover { color: var(--soc-accent); }
.crumbs .sep { margin: 0 7px; }
.eyebrow { font-size: 11px; letter-spacing: 2.5px; color: var(--soc-accent); }
.hero h1 { margin: 8px 0; font-size: 27px; letter-spacing: 1.5px; }
.hero p { margin: 0; color: var(--soc-muted); font-size: 13px; }
.accent-text { color: var(--soc-accent); }

.error-bar { margin-bottom: 16px; }

.row-2 {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
}
@media (max-width: 1100px) { .row-2 { grid-template-columns: 1fr; } }

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
  align-items: center;
}
.panel-title .en {
  margin-left: 8px;
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 1.5px;
  color: var(--soc-faint);
}
.title-tag { margin-left: 12px; }
.title-actions { margin-left: auto; display: flex; align-items: center; gap: 12px; }

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.ov {
  padding: 10px 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(8, 20, 36, 0.6);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ov span { font-size: 11px; color: var(--soc-faint); }
.ov b { font-size: 13px; font-weight: 600; }
.ov .dim, .dim { color: var(--soc-faint); font-style: normal; font-weight: 400; }

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.ft {
  padding: 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(8, 20, 36, 0.6);
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.ft span { font-size: 11px; color: var(--soc-faint); }
.ft b { font-size: 19px; font-weight: 700; }
.ft .unit { font-size: 11px; color: var(--soc-faint); font-style: normal; font-weight: 400; margin-left: 3px; }
.bad-text { color: var(--soc-red); }
.ok-text { color: var(--soc-green); }

.risk-panel .risk-body { display: flex; flex-direction: column; }
.rules { margin-top: 4px; }
.rules-head { font-size: 12px; color: var(--soc-muted); margin-bottom: 10px; }
.rule-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(8, 20, 36, 0.6);
  font-size: 12.5px;
}
.rule-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--soc-red);
  box-shadow: 0 0 8px var(--soc-red);
  flex: 0 0 7px;
}
.rule-desc { flex: 1; }
.rule-points { color: var(--soc-red); font-weight: 700; }
.empty-hint-sm { padding: 14px; text-align: center; color: var(--soc-faint); font-size: 12px; }

.traces { display: flex; flex-direction: column; gap: 12px; }
.trace {
  border: 1px solid var(--soc-border);
  border-radius: 10px;
  background: rgba(8, 20, 36, 0.55);
  overflow: hidden;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--soc-border);
  background: rgba(13, 27, 46, 0.7);
}
.trace-name { color: var(--soc-accent); font-weight: 700; font-size: 13px; }
.trace-cn { color: var(--soc-muted); font-size: 12px; }
.trace-meta { margin-left: auto; font-size: 11.5px; color: var(--soc-muted); }
.trace-meta + .trace-meta { margin-left: 0; }
.trace-body {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  flex-wrap: wrap;
}
.trace-col { flex: 1 1 260px; }
.trace-label { display: block; font-size: 11px; color: var(--soc-faint); margin-bottom: 6px; }

.code-block {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: #081424;
  color: #9fd8ef;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 180px;
  overflow: auto;
}
.code-block.err { color: var(--soc-red); }

.verdict-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.verdict-box {
  padding: 12px 14px;
  border: 1px solid var(--soc-border);
  border-radius: 9px;
  background: rgba(8, 20, 36, 0.6);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vb-label { font-size: 11px; color: var(--soc-faint); }

.report-block { margin-bottom: 16px; }
.rb-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
  color: var(--soc-text);
  display: flex;
  align-items: baseline;
}
.rb-title .en-note { margin-left: 8px; font-size: 10.5px; color: var(--soc-faint); font-weight: 400; }
.rb-text { margin: 0; font-size: 13px; color: var(--soc-muted); line-height: 1.8; }
.rb-list { margin: 0; padding-left: 18px; color: var(--soc-muted); font-size: 13px; line-height: 2; }
.warn-list li::marker { color: var(--soc-amber); }

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
@media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }

.evidence {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 8px;
  border: 1px solid var(--soc-border);
  border-radius: 9px;
  background: rgba(8, 20, 36, 0.55);
  align-items: flex-start;
}
.ev-body { flex: 1; min-width: 0; }
.ev-fact { font-size: 13px; font-weight: 600; }
.ev-exp { font-size: 12px; color: var(--soc-muted); margin-top: 4px; line-height: 1.7; }
.ev-refs { margin-top: 7px; display: flex; flex-wrap: wrap; gap: 8px; }
.ev-refs span {
  font-size: 10.5px;
  color: var(--soc-accent);
  background: rgba(56, 198, 241, 0.09);
  border: 1px solid rgba(56, 198, 241, 0.25);
  border-radius: 4px;
  padding: 1px 7px;
}

.report-meta {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed var(--soc-border);
  font-size: 11px;
  color: var(--soc-faint);
}

.review-panel .review-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.review-note { margin: 0; font-size: 12.5px; color: var(--soc-muted); line-height: 1.8; max-width: 640px; }

.id-text { color: var(--soc-accent); }
.time { color: var(--soc-muted); font-size: 12px; }
.empty-hint {
  padding: 34px 0;
  text-align: center;
  color: var(--soc-faint);
  font-size: 13px;
  border: 1px dashed var(--soc-border);
  border-radius: 9px;
}
</style>
