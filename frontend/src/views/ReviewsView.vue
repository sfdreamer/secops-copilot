<template>
  <div>
    <div class="hero">
      <div class="eyebrow">04 / HUMAN REVIEW</div>
      <h1>审核记录</h1>
      <p>保留人工决策痕迹，让每一次研判都有据可查。审核记录只追加、不覆盖；确认威胁与标记误报为终态。</p>
    </div>

    <section class="panel">
      <div class="filter-row">
        <el-input v-model="query.incidentId" placeholder="事件编号（精确匹配）" clearable class="f-grow"
          @keyup.enter="load(1)" />
        <el-select v-model="query.reviewResult" placeholder="审核结果" clearable class="f-fixed">
          <el-option label="确认威胁" value="CONFIRMED_THREAT" />
          <el-option label="标记误报" value="FALSE_POSITIVE" />
          <el-option label="进一步调查" value="INVESTIGATING" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-bar" />

      <el-table v-loading="loading" :data="page?.items ?? []">
        <el-table-column label="审核编号" min-width="150">
          <template #default="{ row }"><span class="mono id-text">{{ shortId(row.reviewId) }}</span></template>
        </el-table-column>
        <el-table-column label="事件编号" min-width="165">
          <template #default="{ row }">
            <RouterLink :to="`/incidents/${encodeURIComponent(row.incidentId)}`" class="mono link">
              {{ row.incidentId }}
            </RouterLink>
          </template>
        </el-table-column>
        <el-table-column label="AI 研判" min-width="105">
          <template #default="{ row }">
            <UiTag v-if="row.aiVerdict" :text="labelOf(VERDICT_LABEL, row.aiVerdict)" :color="VERDICT_COLOR[row.aiVerdict]" />
            <span v-else class="dim">无 AI 研判</span>
          </template>
        </el-table-column>
        <el-table-column label="人工审核结果" min-width="120">
          <template #default="{ row }">
            <UiTag :text="labelOf(REVIEW_RESULT_LABEL, row.reviewResult)" :color="REVIEW_COLOR[row.reviewResult]" />
          </template>
        </el-table-column>
        <el-table-column prop="reviewer" label="审核人" min-width="100" />
        <el-table-column label="备注" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="审核时间" min-width="155">
          <template #default="{ row }"><span class="mono time">{{ formatTime(row.reviewTime) }}</span></template>
        </el-table-column>
        <template #empty>
          <div class="empty-hint">暂无审核记录。可在事件详情页提交人工审核。</div>
        </template>
      </el-table>

      <div class="pager" v-if="page">
        <span class="total">共 {{ page.total }} 条</span>
        <el-pagination background layout="prev, pager, next" :total="page.total"
          :current-page="page.page" :page-size="page.pageSize"
          @current-change="(p: number) => load(p)" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import UiTag from '@/components/UiTag.vue'
import { fetchReviews } from '@/api/reviews'
import type { PageResult } from '@/types/api'
import type { ReviewRecord } from '@/types/incidents'
import { REVIEW_RESULT_LABEL, VERDICT_COLOR, VERDICT_LABEL, labelOf } from '@/utils/labels'
import { formatTime } from '@/utils/format'

const REVIEW_COLOR: Record<string, string> = {
  CONFIRMED_THREAT: '#ff5c74',
  FALSE_POSITIVE: '#3ddc97',
  INVESTIGATING: '#9a8cff',
}

const query = reactive({ incidentId: '', reviewResult: '' })
const page = ref<PageResult<ReviewRecord> | null>(null)
const loading = ref(false)
const error = ref('')

function shortId(id: string): string {
  return id.length > 18 ? `${id.slice(0, 18)}…` : id
}

async function load(p: number): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    page.value = await fetchReviews({
      page: p,
      pageSize: 20,
      incidentId: query.incidentId || undefined,
      reviewResult: query.reviewResult || undefined,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function reset(): void {
  query.incidentId = ''
  query.reviewResult = ''
  load(1)
}

onMounted(() => load(1))
</script>

<style scoped>
.hero { padding: 28px 0 20px; }
.eyebrow { font-size: 11px; letter-spacing: 2.5px; color: var(--soc-accent); }
.hero h1 { margin: 8px 0; font-size: 29px; letter-spacing: 1.5px; }
.hero p { margin: 0; color: var(--soc-muted); font-size: 13px; max-width: 720px; line-height: 1.7; }

.panel {
  background: var(--soc-panel);
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  padding: 18px 20px;
}
.filter-row { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.f-grow { flex: 1 1 240px; max-width: 360px; }
.f-fixed { flex: 0 0 170px; }
.error-bar { margin-bottom: 14px; }

.id-text { color: var(--soc-accent); }
.link { color: var(--soc-accent); text-decoration: none; }
.link:hover { text-shadow: 0 0 10px rgba(56, 198, 241, 0.6); }
.time { color: var(--soc-muted); font-size: 12px; }
.dim { color: var(--soc-faint); font-size: 12px; }

.pager { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
.total { font-size: 12px; color: var(--soc-faint); }
.empty-hint { padding: 40px 0; text-align: center; color: var(--soc-faint); font-size: 13px; }
</style>
