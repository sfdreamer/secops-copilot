<template>
  <div v-loading="loading" element-loading-text="正在加载态势数据…">
    <div class="hero">
      <div class="eyebrow">01 / SECURITY OPERATIONS</div>
      <h1>安全态势总览</h1>
      <p>从告警噪声中识别真正需要关注的事件，全面掌握安全态势，驱动高效处置。本页统计由后端基于模拟数据实时计算。</p>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-bar">
      <el-button size="small" @click="load">重新加载</el-button>
    </el-alert>

    <template v-else-if="data">
      <section class="stat-grid">
        <StatCard label="原始告警" sub="Raw Alerts" :value="data.alertTotal" hint="统一加载的多类型模拟告警" tone="#38c6f1">
          <template #icon><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M12 3 2.5 20h19L12 3Z"/><path d="M12 10v4m0 3.5v.01" stroke-linecap="round"/></svg></template>
        </StatCard>
        <StatCard label="已聚合事件" sub="Incidents" :value="data.supportedIncidentTotal" hint="SSH 暴力破解聚合生成" tone="#6ee7ff">
          <template #icon><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 7h16M4 12h16M4 17h10"/></svg></template>
        </StatCard>
        <StatCard label="高风险事件" sub="High Risk" :value="data.highRiskIncidentTotal" hint="规则风险 HIGH / CRITICAL" tone="#ff5c74">
          <template #icon><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/></svg></template>
        </StatCard>
        <StatCard label="待人工审核" sub="Pending Review" :value="data.pendingReviewTotal" hint="AI 研判完成等待结论" tone="#f5b544">
          <template #icon><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5"/></svg></template>
        </StatCard>
        <StatCard label="未支持策略告警" sub="Unsupported" :value="data.unsupportedAlertTotal" hint="仅展示，不生成研判" tone="#8aa6c6">
          <template #icon><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.4 2.3c-.8.4-.9 1-.9 1.7m0 3h.01"/></svg></template>
        </StatCard>
      </section>

      <section class="chart-row">
        <div class="panel chart-panel">
          <div class="panel-title">事件风险分布<span class="en">Incident Risk</span></div>
          <ChartBox v-if="hasIncidents" :option="riskOption" :height="238" />
          <div v-else class="empty-hint">暂无事件数据</div>
        </div>
        <div class="panel chart-panel">
          <div class="panel-title">告警类型分布<span class="en">Alert Type</span></div>
          <ChartBox :option="typeOption" :height="238" />
        </div>
        <div class="panel chart-panel">
          <div class="panel-title">告警类别分布<span class="en">Alert Category</span></div>
          <ChartBox :option="categoryOption" :height="238" />
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">
          近期安全事件<span class="en">Recent Incidents</span>
          <RouterLink to="/alerts?tab=incidents" class="more">查看全部 →</RouterLink>
        </div>
        <el-table v-if="data.recentIncidents.length" :data="data.recentIncidents"
          @row-click="(row: IncidentItem) => goDetail(row.incidentId)" class="clickable">
          <el-table-column label="事件编号" min-width="170">
            <template #default="{ row }"><span class="mono id-text">{{ row.incidentId }}</span></template>
          </el-table-column>
          <el-table-column label="告警类型" min-width="130">
            <template #default="{ row }"><UiTag :text="labelOf(ALERT_TYPE_LABEL, row.alertType)" color="#38c6f1" /></template>
          </el-table-column>
          <el-table-column label="来源 IP" min-width="120">
            <template #default="{ row }"><span class="mono">{{ row.sourceIp ?? '—' }}</span></template>
          </el-table-column>
          <el-table-column prop="targetAsset" label="目标资产" min-width="110">
            <template #default="{ row }">{{ row.targetAsset ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="alertCount" label="告警数" width="80" align="center" />
          <el-table-column label="规则风险" min-width="130">
            <template #default="{ row }">
              <span class="mono score" :style="{ color: RISK_COLOR[row.ruleRiskLevel] }">{{ row.ruleRiskScore }}</span>
              <UiTag :text="row.ruleRiskLevel" :color="RISK_COLOR[row.ruleRiskLevel]" />
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="110">
            <template #default="{ row }">
              <UiTag :text="labelOf(INCIDENT_STATUS_LABEL, row.status)" :color="INCIDENT_STATUS_COLOR[row.status]" />
            </template>
          </el-table-column>
          <el-table-column label="首次发现" min-width="150">
            <template #default="{ row }"><span class="mono time">{{ formatTime(row.firstSeen) }}</span></template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-hint">暂无事件</div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import UiTag from '@/components/UiTag.vue'
import { fetchDashboard } from '@/api/dashboard'
import type { DashboardDto } from '@/types/dashboard'
import type { IncidentItem } from '@/types/incidents'
import {
  ALERT_TYPE_LABEL, CATEGORY_LABEL, INCIDENT_STATUS_COLOR, INCIDENT_STATUS_LABEL,
  RISK_COLOR, labelOf,
} from '@/utils/labels'
import { formatTime } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<DashboardDto | null>(null)

const hasIncidents = computed(() => (data.value?.supportedIncidentTotal ?? 0) > 0)

const AXIS = {
  axisLine: { lineStyle: { color: '#1c3552' } },
  axisLabel: { color: '#7e9ab8', fontSize: 11 },
  splitLine: { lineStyle: { color: 'rgba(28,53,82,.5)' } },
}

const riskOption = computed<EChartsOption>(() => {
  const dist = data.value?.riskDistribution ?? {}
  const order = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  const items = order
    .map((level) => ({ name: `${level} ${dist[level] ?? 0}`, value: dist[level] ?? 0, itemStyle: { color: RISK_COLOR[level] } }))
    .filter((d) => d.value > 0)
  return {
    tooltip: { trigger: 'item', backgroundColor: '#0d1b2e', borderColor: '#1c3552', textStyle: { color: '#e8f2ff' } },
    series: [{
      type: 'pie',
      radius: ['52%', '74%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      label: { color: '#7e9ab8', fontSize: 11 },
      itemStyle: { borderColor: '#071324', borderWidth: 2 },
      data: items.length ? items : [{ name: '暂无数据', value: 1, itemStyle: { color: '#1c3552' } }],
    }],
  }
})

const typeOption = computed<EChartsOption>(() => {
  const dist = data.value?.alertTypeDistribution ?? {}
  const entries = Object.entries(dist).filter(([, v]) => v > 0).sort((a, b) => a[1] - b[1])
  return {
    grid: { left: 8, right: 30, top: 10, bottom: 0, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: '#0d1b2e', borderColor: '#1c3552', textStyle: { color: '#e8f2ff' } },
    xAxis: { type: 'value', ...AXIS },
    yAxis: {
      type: 'category',
      data: entries.map(([k]) => labelOf(ALERT_TYPE_LABEL, k)),
      ...AXIS,
    },
    series: [{
      type: 'bar',
      data: entries.map(([, v]) => v),
      barWidth: 12,
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [{ offset: 0, color: '#1b9fd6' }, { offset: 1, color: '#6ee7ff' }],
        },
      },
      label: { show: true, position: 'right', color: '#38c6f1', fontSize: 11 },
    }],
  }
})

const categoryOption = computed<EChartsOption>(() => {
  const dist = data.value?.categoryDistribution ?? {}
  const palette = ['#38c6f1', '#9a8cff', '#f5b544', '#3ddc97', '#ff9f43', '#5c7a99']
  const items = Object.entries(dist)
    .filter(([, v]) => v > 0)
    .map(([k, v], i) => ({ name: `${labelOf(CATEGORY_LABEL, k)} ${v}`, value: v, itemStyle: { color: palette[i % palette.length] } }))
  return {
    tooltip: { trigger: 'item', backgroundColor: '#0d1b2e', borderColor: '#1c3552', textStyle: { color: '#e8f2ff' } },
    series: [{
      type: 'pie',
      radius: ['0', '68%'],
      roseType: 'radius',
      label: { color: '#7e9ab8', fontSize: 11 },
      itemStyle: { borderColor: '#071324', borderWidth: 2, borderRadius: 4 },
      data: items.length ? items : [{ name: '暂无数据', value: 1, itemStyle: { color: '#1c3552' } }],
    }],
  }
})

function goDetail(id: string): void {
  router.push(`/incidents/${encodeURIComponent(id)}`)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchDashboard()
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
.hero p { margin: 0; color: var(--soc-muted); font-size: 13px; max-width: 720px; line-height: 1.7; }

.error-bar { margin-bottom: 16px; }

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.panel {
  background: var(--soc-panel);
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.chart-panel { margin-bottom: 0; }

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
.more {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--soc-accent);
  text-decoration: none;
}
.more:hover { text-shadow: 0 0 10px rgba(56, 198, 241, 0.6); }

.id-text { color: var(--soc-accent); }
.score { font-weight: 700; margin-right: 8px; font-size: 14px; }
.time { color: var(--soc-muted); font-size: 12px; }

.clickable :deep(.el-table__row) { cursor: pointer; }

.empty-hint {
  padding: 42px 0;
  text-align: center;
  color: var(--soc-faint);
  font-size: 13px;
  border: 1px dashed var(--soc-border);
  border-radius: 9px;
}
</style>
