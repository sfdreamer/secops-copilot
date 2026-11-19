<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="brand-mark">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none">
            <path d="M12 2 4 5.5v5.2c0 5 3.4 9.6 8 10.8 4.6-1.2 8-5.8 8-10.8V5.5L12 2Z"
              stroke="#04121f" stroke-width="2" stroke-linejoin="round" fill="rgba(4,18,31,.18)" />
            <path d="M8.5 12.2l2.4 2.4 4.6-4.8" stroke="#04121f" stroke-width="2.2"
              stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div>
          <div class="brand-name">SECOPS</div>
          <div class="brand-sub">COPILOT</div>
        </div>
      </div>

      <nav class="nav">
        <RouterLink v-for="item in navItems" :key="item.path" :to="item.path" class="nav-item"
          :class="{ active: isActive(item) }">
          <span class="nav-icon" v-html="item.icon" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>

      <div class="side-foot">
        <div class="foot-row">
          <span class="dot" :class="healthClass" />
          <span>{{ healthText }}</span>
        </div>
        <RouterLink to="/status" class="status-link">系统状态</RouterLink>
        <div class="analyst">分析师 / DEMO</div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <span class="crumb">{{ crumb }}</span>
        <div class="top-right">
          <span class="env-badge">演示环境 · 模拟数据</span>
          <span class="model-badge" :class="{ off: !modelOn }">
            <span class="dot" :class="healthClass" />模型服务 · {{ modelOn ? '已配置' : '未配置' }}
          </span>
          <span class="clock mono">{{ clock }}</span>
        </div>
      </header>
      <div class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.fullPath" />
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchHealth } from '@/api/health'
import type { HealthDto } from '@/types/api'

const route = useRoute()

const ICONS = {
  dashboard:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 13h8V3H3v10Zm0 8h8v-6H3v6Zm10 0h8V11h-8v10Zm0-18v6h8V3h-8Z"/></svg>',
  alerts:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2.5 20h19L12 3Z"/><path d="M12 10v4m0 3.5v.01"/></svg>',
  reviews:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="m8.5 12.5 2.5 2.5 4.5-5"/></svg>',
  evaluation:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 20V10m6 10V4m6 16v-7"/><path d="M2 20h20"/></svg>',
}

const navItems = [
  { path: '/dashboard', title: '安全态势总览', icon: ICONS.dashboard },
  { path: '/alerts', title: '告警与事件', icon: ICONS.alerts, match: ['/alerts', '/incidents'] },
  { path: '/reviews', title: '审核记录', icon: ICONS.reviews },
  { path: '/evaluation', title: '效果评估', icon: ICONS.evaluation },
]

function isActive(item: { path: string; match?: string[] }): boolean {
  const prefixes = item.match ?? [item.path]
  return prefixes.some((p) => route.path.startsWith(p))
}

const crumb = computed(() => `工作台 / ${(route.meta.title as string) ?? ''}`)

const health = ref<HealthDto | null>(null)
const healthFailed = ref(false)
const modelOn = computed(() => health.value?.modelConfigured ?? false)
const healthClass = computed(() => {
  if (healthFailed.value) return 'bad'
  return health.value ? (health.value.status === 'UP' ? 'ok' : 'bad') : 'warn'
})
const healthText = computed(() => {
  if (healthFailed.value) return '后端不可达'
  if (!health.value) return '后端检测中'
  return health.value.status === 'UP' ? '服务正常' : '服务异常'
})

const clock = ref('')
let timer: ReturnType<typeof setInterval> | undefined
function tick(): void {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(async () => {
  tick()
  timer = setInterval(tick, 1000)
  try {
    health.value = await fetchHealth()
    healthFailed.value = false
  } catch {
    health.value = null
    healthFailed.value = true
  }
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.side {
  width: 212px;
  flex: 0 0 212px;
  padding: 22px 14px;
  border-right: 1px solid var(--soc-border);
  background: rgba(5, 13, 25, 0.82);
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 2px 6px 22px;
  border-bottom: 1px solid var(--soc-border);
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--soc-accent-deep), var(--soc-accent) 60%, #6ee7ff);
  box-shadow: 0 0 16px rgba(56, 198, 241, 0.45);
}

.brand-name {
  font-weight: 800;
  letter-spacing: 2.5px;
  font-size: 15px;
}

.brand-sub {
  font-size: 10px;
  letter-spacing: 3.5px;
  color: var(--soc-muted);
}

.nav {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 13px;
  border-radius: 9px;
  font-size: 13.5px;
  color: var(--soc-muted);
  text-decoration: none;
  border-left: 3px solid transparent;
  transition: all 0.2s;
}

.nav-item:hover {
  color: var(--soc-text);
  background: rgba(56, 198, 241, 0.07);
}

.nav-item.active {
  background: rgba(56, 198, 241, 0.13);
  border-left-color: var(--soc-accent);
  color: var(--soc-text);
  font-weight: 600;
  box-shadow: inset 0 0 20px rgba(56, 198, 241, 0.05);
}

.nav-icon {
  display: grid;
  place-items: center;
  opacity: 0.9;
}

.side-foot {
  margin-top: auto;
  padding: 14px 8px 2px;
  border-top: 1px solid var(--soc-border);
  font-size: 11.5px;
  color: var(--soc-muted);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.foot-row {
  display: flex;
  align-items: center;
  gap: 7px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.dot.ok { background: var(--soc-green); box-shadow: 0 0 8px var(--soc-green); }
.dot.warn { background: var(--soc-amber); box-shadow: 0 0 8px var(--soc-amber); }
.dot.bad { background: var(--soc-red); box-shadow: 0 0 8px var(--soc-red); }

.status-link {
  color: var(--soc-faint);
  text-decoration: none;
}
.status-link:hover {
  color: var(--soc-accent);
}

.analyst {
  letter-spacing: 0.5px;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 54px;
  flex: 0 0 54px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--soc-border);
  font-size: 12.5px;
  color: var(--soc-muted);
  background: rgba(5, 13, 25, 0.6);
  backdrop-filter: blur(8px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.env-badge {
  padding: 4px 12px;
  border: 1px solid rgba(245, 181, 68, 0.45);
  border-radius: 999px;
  color: var(--soc-amber);
  background: rgba(245, 181, 68, 0.08);
}

.model-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 12px;
  border: 1px solid rgba(61, 220, 151, 0.4);
  border-radius: 999px;
  color: var(--soc-green);
  background: rgba(61, 220, 151, 0.08);
}
.model-badge.off {
  border-color: rgba(245, 181, 68, 0.4);
  color: var(--soc-amber);
  background: rgba(245, 181, 68, 0.08);
}

.clock {
  color: var(--soc-faint);
  font-size: 12px;
}

.content {
  flex: 1;
  padding: 0 32px 44px;
}

@media (max-width: 860px) {
  .side {
    display: none;
  }
  .content,
  .topbar {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
