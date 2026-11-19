<template>
  <div>
    <div class="hero">
      <div class="eyebrow">02 / ALERTS &amp; INCIDENTS</div>
      <h1>告警与事件管理</h1>
      <p>统一查看原始告警，聚合高关联安全事件，提升威胁处置效率。未注册策略的告警仅用于展示，不提供研判入口。</p>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab-btn" :class="{ active: tab === t.key }" @click="switchTab(t.key)">
        {{ t.label }}
      </button>
    </div>

    <!-- ================= 原始告警 ================= -->
    <section v-if="tab === 'alerts'" class="panel">
      <div class="filter-row">
        <el-input v-model="alertQuery.keyword" placeholder="关键词（告警 ID / IP / 资产）" clearable class="f-grow"
          @keyup.enter="loadAlerts(1)" />
        <el-select v-model="alertQuery.alertType" placeholder="告警类型" clearable class="f-fixed">
          <el-option v-for="t in ALERT_TYPES" :key="t" :label="labelOf(ALERT_TYPE_LABEL, t)" :value="t" />
        </el-select>
        <el-select v-model="alertQuery.category" placeholder="告警类别" clearable class="f-fixed">
          <el-option v-for="c in ALERT_CATEGORIES" :key="c" :label="labelOf(CATEGORY_LABEL, c)" :value="c" />
        </el-select>
        <el-select v-model="alertQuery.severity" placeholder="严重等级" clearable class="f-fixed-sm">
          <el-option v-for="s in SEVERITIES" :key="s" :label="labelOf(SEVERITY_LABEL, s)" :value="s" />
        </el-select>
        <el-input v-model="alertQuery.sourceIp" placeholder="来源 IP" clearable class="f-fixed" @keyup.enter="loadAlerts(1)" />
        <el-input v-model="alertQuery.targetAsset" placeholder="目标资产" clearable class="f-fixed" @keyup.enter="loadAlerts(1)" />
        <el-button type="primary" @click="loadAlerts(1)">查询</el-button>
        <el-button @click="resetAlertQuery">重置</el-button>
      </div>

      <el-alert v-if="alertError" :title="alertError" type="error" show-icon :closable="false" class="error-bar" />

      <el-table v-loading="alertLoading" :data="alertPage?.items ?? []">
        <el-table-column label="告警 ID" min-width="150">
          <template #default="{ row }"><span class="mono id-text">{{ row.alertId }}</span></template>
        </el-table-column>
        <el-table-column label="告警类型" min-width="140">
          <template #default="{ row }"><UiTag :text="labelOf(ALERT_TYPE_LABEL, row.alertType)" color="#38c6f1" /></template>
        </el-table-column>
        <el-table-column label="类别" min-width="100">
          <template #default="{ row }">{{ labelOf(CATEGORY_LABEL, row.category) }}</template>
        </el-table-column>
        <el-table-column label="来源 IP" min-width="125">
          <template #default="{ row }"><span class="mono">{{ row.sourceIp ?? '—' }}</span></template>
        </el-table-column>
        <el-table-column label="目标资产" min-width="105">
          <template #default="{ row }">{{ row.targetAsset ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="严重等级" width="95">
          <template #default="{ row }">
            <UiTag :text="labelOf(SEVERITY_LABEL, row.severity)" :color="SEVERITY_COLOR[row.severity]" />
          </template>
        </el-table-column>
        <el-table-column label="发生时间" min-width="155">
          <template #default="{ row }"><span class="mono time">{{ formatTime(row.timestamp) }}</span></template>
        </el-table-column>
        <el-table-column label="策略支持" width="115">
          <template #default="{ row }">
            <UiTag v-if="row.strategySupport === 'SUPPORTED'" text="已支持" color="#3ddc97" />
            <UiTag v-else text="策略待扩展" color="#8aa6c6" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAlert(row.alertId)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><div class="empty-hint">暂无符合条件的告警</div></template>
      </el-table>

      <div class="pager" v-if="alertPage">
        <span class="total">共 {{ alertPage.total }} 条</span>
        <el-pagination background layout="prev, pager, next" :total="alertPage.total"
          :current-page="alertPage.page" :page-size="alertPage.pageSize"
          @current-change="(p: number) => loadAlerts(p)" />
      </div>
    </section>

    <!-- ================= 安全事件 ================= -->
    <section v-else class="panel">
      <div class="filter-row">
        <el-input v-model="incidentQuery.keyword" placeholder="关键词（事件 ID / IP / 资产）" clearable class="f-grow"
          @keyup.enter="loadIncidents(1)" />
        <el-select v-model="incidentQuery.alertType" placeholder="告警类型" clearable class="f-fixed">
          <el-option v-for="t in ALERT_TYPES" :key="t" :label="labelOf(ALERT_TYPE_LABEL, t)" :value="t" />
        </el-select>
        <el-select v-model="incidentQuery.ruleRiskLevel" placeholder="规则风险" clearable class="f-fixed-sm">
          <el-option v-for="r in RISK_LEVELS" :key="r" :label="r" :value="r" />
        </el-select>
        <el-select v-model="incidentQuery.status" placeholder="事件状态" clearable class="f-fixed">
          <el-option v-for="s in INCIDENT_STATUSES" :key="s" :label="labelOf(INCIDENT_STATUS_LABEL, s)" :value="s" />
        </el-select>
        <el-input v-model="incidentQuery.sourceIp" placeholder="来源 IP" clearable class="f-fixed" @keyup.enter="loadIncidents(1)" />
        <el-input v-model="incidentQuery.targetAsset" placeholder="目标资产" clearable class="f-fixed" @keyup.enter="loadIncidents(1)" />
        <el-button type="primary" @click="loadIncidents(1)">查询</el-button>
        <el-button @click="resetIncidentQuery">重置</el-button>
      </div>

      <el-alert v-if="incidentError" :title="incidentError" type="error" show-icon :closable="false" class="error-bar" />

      <el-table v-loading="incidentLoading" :data="incidentPage?.items ?? []"
        @row-click="(row: IncidentItem) => goDetail(row.incidentId)" class="clickable">
        <el-table-column label="事件编号" min-width="165">
          <template #default="{ row }"><span class="mono id-text">{{ row.incidentId }}</span></template>
        </el-table-column>
        <el-table-column label="告警类型" min-width="130">
          <template #default="{ row }"><UiTag :text="labelOf(ALERT_TYPE_LABEL, row.alertType)" color="#38c6f1" /></template>
        </el-table-column>
        <el-table-column label="来源 IP" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.sourceIp ?? '—' }}</span></template>
        </el-table-column>
        <el-table-column label="目标资产" min-width="100">
          <template #default="{ row }">{{ row.targetAsset ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="alertCount" label="告警数" width="75" align="center" />
        <el-table-column label="规则风险" min-width="125">
          <template #default="{ row }">
            <span class="mono score" :style="{ color: RISK_COLOR[row.ruleRiskLevel] }">{{ row.ruleRiskScore }}</span>
            <UiTag :text="row.ruleRiskLevel" :color="RISK_COLOR[row.ruleRiskLevel]" />
          </template>
        </el-table-column>
        <el-table-column label="AI 研判" width="90">
          <template #default="{ row }">
            <span class="analysis" :class="row.analysisStatus.toLowerCase()">
              {{ labelOf(ANALYSIS_STATUS_LABEL, row.analysisStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="105">
          <template #default="{ row }">
            <UiTag :text="labelOf(INCIDENT_STATUS_LABEL, row.status)" :color="INCIDENT_STATUS_COLOR[row.status]" />
          </template>
        </el-table-column>
        <el-table-column label="首次发现" min-width="150">
          <template #default="{ row }"><span class="mono time">{{ formatTime(row.firstSeen) }}</span></template>
        </el-table-column>
        <template #empty><div class="empty-hint">暂无符合条件的事件</div></template>
      </el-table>

      <div class="pager" v-if="incidentPage">
        <span class="total">共 {{ incidentPage.total }} 条</span>
        <el-pagination background layout="prev, pager, next" :total="incidentPage.total"
          :current-page="incidentPage.page" :page-size="incidentPage.pageSize"
          @current-change="(p: number) => loadIncidents(p)" />
      </div>
    </section>

    <!-- ================= 告警详情抽屉 ================= -->
    <el-drawer v-model="drawerOpen" title="原始告警详情" size="520px">
      <div v-loading="drawerLoading">
        <template v-if="drawerData">
          <div class="drawer-head">
            <span class="mono drawer-id">{{ drawerData.alert.alertId }}</span>
            <UiTag v-if="drawerData.strategySupport === 'SUPPORTED'" text="策略已支持" color="#3ddc97" />
            <UiTag v-else text="策略待扩展 · 不提供研判入口" color="#8aa6c6" />
          </div>
          <div class="kv-grid">
            <div class="kv"><span>告警类型</span><b>{{ labelOf(ALERT_TYPE_LABEL, drawerData.alert.alertType) }}</b></div>
            <div class="kv"><span>告警类别</span><b>{{ labelOf(CATEGORY_LABEL, drawerData.alert.category) }}</b></div>
            <div class="kv"><span>严重等级</span>
              <b><UiTag :text="labelOf(SEVERITY_LABEL, drawerData.alert.severity)" :color="SEVERITY_COLOR[drawerData.alert.severity]" /></b>
            </div>
            <div class="kv"><span>来源系统</span><b>{{ drawerData.alert.sourceSystem ?? '—' }}</b></div>
            <div class="kv"><span>来源 IP</span><b class="mono">{{ drawerData.alert.sourceIp ?? '—' }}</b></div>
            <div class="kv"><span>目标 IP</span><b class="mono">{{ drawerData.alert.targetIp ?? '—' }}</b></div>
            <div class="kv"><span>目标资产</span><b>{{ drawerData.alert.targetAsset ?? '—' }}</b></div>
            <div class="kv"><span>发生时间</span><b class="mono">{{ formatTime(drawerData.alert.timestamp) }}</b></div>
          </div>

          <div class="drawer-title">类型化属性<span class="en">ATTRIBUTES</span></div>
          <pre class="code-block">{{ prettyJson(drawerData.alert.attributes) }}</pre>

          <div class="drawer-title">原始日志<span class="en">RAW LOG · 不可信文本</span></div>
          <pre class="code-block raw">{{ drawerData.alert.rawLog ?? '（无）' }}</pre>
        </template>
        <el-alert v-else-if="drawerError" :title="drawerError" type="error" show-icon :closable="false" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UiTag from '@/components/UiTag.vue'
import { fetchAlertDetail, fetchAlerts } from '@/api/alerts'
import { fetchIncidents } from '@/api/incidents'
import type { PageResult } from '@/types/api'
import type { AlertDetail, AlertItem } from '@/types/alerts'
import type { IncidentItem } from '@/types/incidents'
import {
  ALERT_CATEGORIES, ALERT_TYPES, INCIDENT_STATUSES, RISK_LEVELS, SEVERITIES,
} from '@/types/enums'
import {
  ALERT_TYPE_LABEL, ANALYSIS_STATUS_LABEL, CATEGORY_LABEL, INCIDENT_STATUS_COLOR,
  INCIDENT_STATUS_LABEL, RISK_COLOR, SEVERITY_COLOR, SEVERITY_LABEL, labelOf,
} from '@/utils/labels'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'alerts', label: '原始告警' },
  { key: 'incidents', label: '安全事件' },
] as const
type TabKey = (typeof tabs)[number]['key']
const tab = ref<TabKey>(route.query.tab === 'incidents' ? 'incidents' : 'alerts')

function switchTab(key: TabKey): void {
  tab.value = key
  router.replace({ query: key === 'incidents' ? { tab: 'incidents' } : {} })
}

/* ---------- 原始告警 ---------- */
const alertQuery = reactive({ keyword: '', alertType: '', category: '', severity: '', sourceIp: '', targetAsset: '' })
const alertPage = ref<PageResult<AlertItem> | null>(null)
const alertLoading = ref(false)
const alertError = ref('')

function clean<T extends Record<string, string>>(q: T): Partial<T> {
  return Object.fromEntries(Object.entries(q).filter(([, v]) => v)) as Partial<T>
}

async function loadAlerts(page: number): Promise<void> {
  alertLoading.value = true
  alertError.value = ''
  try {
    alertPage.value = await fetchAlerts({ page, pageSize: 20, ...clean(alertQuery) })
  } catch (e) {
    alertError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    alertLoading.value = false
  }
}

function resetAlertQuery(): void {
  Object.keys(alertQuery).forEach((k) => ((alertQuery as Record<string, string>)[k] = ''))
  loadAlerts(1)
}

/* ---------- 安全事件 ---------- */
const incidentQuery = reactive({ keyword: '', alertType: '', ruleRiskLevel: '', status: '', sourceIp: '', targetAsset: '' })
const incidentPage = ref<PageResult<IncidentItem> | null>(null)
const incidentLoading = ref(false)
const incidentError = ref('')

async function loadIncidents(page: number): Promise<void> {
  incidentLoading.value = true
  incidentError.value = ''
  try {
    incidentPage.value = await fetchIncidents({ page, pageSize: 20, ...clean(incidentQuery) })
  } catch (e) {
    incidentError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    incidentLoading.value = false
  }
}

function resetIncidentQuery(): void {
  Object.keys(incidentQuery).forEach((k) => ((incidentQuery as Record<string, string>)[k] = ''))
  loadIncidents(1)
}

function goDetail(id: string): void {
  router.push(`/incidents/${encodeURIComponent(id)}`)
}

/* ---------- 告警详情抽屉 ---------- */
const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerError = ref('')
const drawerData = ref<AlertDetail | null>(null)

async function openAlert(id: string): Promise<void> {
  drawerOpen.value = true
  drawerLoading.value = true
  drawerError.value = ''
  drawerData.value = null
  try {
    drawerData.value = await fetchAlertDetail(id)
  } catch (e) {
    drawerError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    drawerLoading.value = false
  }
}

function prettyJson(value: unknown): string {
  if (value === null || value === undefined) return '（无）'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

onMounted(() => {
  loadAlerts(1)
  loadIncidents(1)
})
</script>

<style scoped>
.hero { padding: 28px 0 20px; }
.eyebrow { font-size: 11px; letter-spacing: 2.5px; color: var(--soc-accent); }
.hero h1 { margin: 8px 0; font-size: 29px; letter-spacing: 1.5px; }
.hero p { margin: 0; color: var(--soc-muted); font-size: 13px; max-width: 720px; line-height: 1.7; }

.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 16px;
  border: 1px solid var(--soc-border);
  border-radius: 10px;
  background: rgba(8, 20, 36, 0.7);
}
.tab-btn {
  padding: 7px 22px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--soc-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-btn:hover { color: var(--soc-text); }
.tab-btn.active {
  background: linear-gradient(135deg, rgba(27, 159, 214, 0.3), rgba(56, 198, 241, 0.22));
  color: var(--soc-accent);
  font-weight: 700;
  box-shadow: inset 0 0 0 1px var(--soc-border-bright), 0 0 14px rgba(56, 198, 241, 0.15);
}

.panel {
  background: var(--soc-panel);
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  padding: 18px 20px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.f-grow { flex: 1 1 220px; max-width: 340px; }
.f-fixed { flex: 0 0 150px; }
.f-fixed-sm { flex: 0 0 120px; }

.error-bar { margin-bottom: 14px; }

.id-text { color: var(--soc-accent); }
.score { font-weight: 700; margin-right: 8px; font-size: 14px; }
.time { color: var(--soc-muted); font-size: 12px; }

.analysis.not_started { color: var(--soc-faint); }
.analysis.running { color: var(--soc-accent); }
.analysis.succeeded { color: var(--soc-green); }
.analysis.failed { color: var(--soc-red); }

.clickable :deep(.el-table__row) { cursor: pointer; }

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}
.total { font-size: 12px; color: var(--soc-faint); }

.empty-hint {
  padding: 40px 0;
  text-align: center;
  color: var(--soc-faint);
  font-size: 13px;
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 18px;
}
.drawer-id { font-size: 15px; color: var(--soc-accent); font-weight: 700; }

.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 20px;
}
.kv {
  padding: 10px 12px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: rgba(8, 20, 36, 0.6);
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.kv span { font-size: 11px; color: var(--soc-faint); }
.kv b { font-size: 13px; font-weight: 600; }

.drawer-title {
  font-size: 13px;
  font-weight: 700;
  margin: 16px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--soc-accent);
}
.drawer-title .en {
  margin-left: 8px;
  font-size: 10px;
  letter-spacing: 1.5px;
  color: var(--soc-faint);
  font-weight: 500;
}

.code-block {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--soc-border);
  border-radius: 8px;
  background: #081424;
  color: #9fd8ef;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}
.code-block.raw { color: #c9b458; }
</style>
