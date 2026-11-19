<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="brand-mark">SC</div>
        <div>
          <div class="brand-name">SECOPS</div>
          <div class="brand-sub">COPILOT</div>
        </div>
      </div>
      <div class="side-label">工作台</div>
      <RouterLink to="/status" class="side-item active">系统状态</RouterLink>
      <RouterLink to="/dashboard" class="side-item">安全态势总览</RouterLink>
      <RouterLink to="/alerts" class="side-item">告警与事件</RouterLink>
      <RouterLink to="/reviews" class="side-item">审核记录</RouterLink>
      <RouterLink to="/evaluation" class="side-item">效果评估</RouterLink>
      <div class="side-foot">分析师 / DEMO</div>
    </aside>

    <main class="main">
      <div class="topbar">
        <span class="crumb">工作台 / 系统状态</span>
        <span class="env-badge">演示环境 · 模拟数据</span>
      </div>

      <div class="hero">
        <div class="eyebrow">01 / SYSTEM STATUS</div>
        <h1>系统状态与开发进度</h1>
        <p>确认后端服务、模拟数据与模型配置的可用状态。所有数据均为虚构演示数据。</p>
      </div>

      <section class="cards">
        <div class="card">
          <div class="card-label">后端服务</div>
          <div v-if="loading" class="card-value muted">检测中…</div>
          <div v-else-if="error" class="card-value bad">不可用</div>
          <div v-else class="card-value good">{{ health?.status ?? '—' }}</div>
          <div class="card-hint">GET /api/health</div>
        </div>
        <div class="card">
          <div class="card-label">模拟数据</div>
          <div v-if="loading" class="card-value muted">—</div>
          <div v-else-if="health?.mockDataLoaded" class="card-value good">已加载</div>
          <div v-else class="card-value bad">未加载</div>
          <div class="card-hint">只读 JSON fixtures</div>
        </div>
        <div class="card">
          <div class="card-label">模型配置</div>
          <div v-if="loading" class="card-value muted">—</div>
          <div v-else-if="health?.modelConfigured" class="card-value good">已配置</div>
          <div v-else class="card-value warn">未配置</div>
          <div class="card-hint">仅表示密钥存在，不保证可用</div>
        </div>
        <div class="card">
          <div class="card-label">当前阶段</div>
          <div class="card-value accent">阶段四 / 四</div>
          <div class="card-hint">已完成 · 交付前审查整改</div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">连通性检查</div>
        <div v-if="loading" class="state-line muted">正在连接后端 …</div>
        <div v-else-if="error" class="state-error">
          <el-alert :title="error" type="error" :closable="false" show-icon />
          <el-button class="retry" size="small" @click="load">重新检测</el-button>
        </div>
        <div v-else-if="!health" class="state-line muted">暂无数据</div>
        <div v-else class="state-line">
          <el-tag type="success" effect="dark" size="small">已连接</el-tag>
          <span>
            状态 {{ health.status }} · 模拟数据 {{ health.mockDataLoaded ? '已加载' : '未加载' }} ·
            模型 {{ health.modelConfigured ? '已配置' : '未配置' }}
          </span>
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">开发阶段</div>
        <ol class="phases">
          <li class="done">
            <b>阶段一 · 骨架与数据</b>
            <span>前后端工程、统一 Alert 模型、JSON fixtures、策略注册、健康检查</span>
          </li>
          <li class="done">
            <b>阶段二 · 聚合 / 特征 / 规则</b>
            <span>SSH 聚合、特征提取、规则评分与事件 API</span>
          </li>
          <li class="done">
            <b>阶段三 · Agent / 工具 / 校验</b>
            <span>OpenAI 兼容模型适配、真实 Function Calling、有界循环、证据校验</span>
          </li>
          <li class="done">
            <b>阶段四 · 审核 / 评估 / 前端</b>
            <span>人工审核、评估服务、业务页面与演示脚本</span>
          </li>
        </ol>
        <p class="note">
          真实模型 Function Calling 已通过独立集成测试验证；不把 Mock、静态数据或单元测试输出伪装为真实调用。
          模型不可用时仍可查看告警与规则结果。运行期内存数据在应用重启后重置。
        </p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHealth } from '@/api/health'
import type { HealthDto } from '@/types/api'

const loading = ref(true)
const error = ref('')
const health = ref<HealthDto | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  health.value = null
  try {
    health.value = await fetchHealth()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '连接后端失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.side {
  width: 210px;
  flex: 0 0 210px;
  padding: 22px 16px;
  border-right: 1px solid var(--soc-border);
  background: rgba(6, 16, 30, 0.72);
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--soc-border);
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 14px;
  color: #04121f;
  background: linear-gradient(135deg, var(--soc-accent), #6ee7ff);
}

.brand-name {
  font-weight: 800;
  letter-spacing: 2px;
  font-size: 15px;
}

.brand-sub {
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--soc-muted);
}

.side-label {
  margin: 24px 10px 10px;
  font-size: 11px;
  color: var(--soc-muted);
  letter-spacing: 1px;
}

.side-item {
  display: block;
  text-decoration: none;
  padding: 10px 12px;
  margin: 2px 0;
  border-radius: 8px;
  font-size: 13px;
  color: var(--soc-muted);
  transition: color 0.2s, background 0.2s;
}

.side-item:hover {
  color: var(--soc-text);
  background: rgba(56, 198, 241, 0.1);
}

.side-item.active {
  background: rgba(56, 198, 241, 0.14);
  border-left: 3px solid var(--soc-accent);
  color: var(--soc-text);
  font-weight: 600;
}

.side-foot {
  margin-top: auto;
  padding-top: 20px;
  font-size: 11px;
  color: var(--soc-muted);
}

.main {
  flex: 1;
  padding: 0 34px 40px;
  min-width: 0;
}

.topbar {
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--soc-border);
  font-size: 12px;
  color: var(--soc-muted);
}

.env-badge {
  padding: 4px 12px;
  border: 1px solid rgba(61, 220, 151, 0.4);
  border-radius: 999px;
  color: var(--soc-green);
  background: rgba(61, 220, 151, 0.08);
}

.hero {
  padding: 30px 0 22px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--soc-accent);
}

.hero h1 {
  margin: 8px 0 8px;
  font-size: 30px;
  letter-spacing: 1px;
}

.hero p {
  margin: 0;
  color: var(--soc-muted);
  font-size: 13px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.card,
.panel {
  background: rgba(14, 29, 49, 0.86);
  border: 1px solid var(--soc-border);
  border-radius: 12px;
  padding: 18px 20px;
}

.card-label {
  font-size: 12px;
  color: var(--soc-muted);
}

.card-value {
  font-size: 26px;
  font-weight: 700;
  margin: 8px 0 4px;
}

.card-hint {
  font-size: 11px;
  color: var(--soc-muted);
}

.good {
  color: var(--soc-green);
}

.bad {
  color: var(--soc-red);
}

.warn {
  color: var(--soc-amber);
}

.accent {
  color: var(--soc-accent);
}

.muted {
  color: var(--soc-muted);
}

.panel {
  margin-bottom: 16px;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 14px;
  padding-left: 10px;
  border-left: 3px solid var(--soc-accent);
}

.state-line {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--soc-text);
}

.state-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.retry {
  margin-top: 2px;
}

.phases {
  list-style: none;
  counter-reset: phase;
  margin: 0;
  padding: 0;
}

.phases li {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 11px 14px;
  margin-bottom: 8px;
  border: 1px solid var(--soc-border);
  border-radius: 9px;
  font-size: 13px;
  background: rgba(8, 20, 36, 0.6);
}

.phases li b {
  font-weight: 600;
}

.phases li span {
  color: var(--soc-muted);
  font-size: 12px;
}

.phases li.done {
  border-color: rgba(61, 220, 151, 0.45);
}

.phases li.done b {
  color: var(--soc-green);
}

.note {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--soc-muted);
  line-height: 1.7;
}

@media (max-width: 760px) {
  .side {
    display: none;
  }
  .main {
    padding: 0 16px 30px;
  }
}
</style>
