<template>
  <div class="stat-card" :style="{ '--tone': tone }">
    <div class="icon-wrap">
      <slot name="icon" />
    </div>
    <div class="body">
      <div class="label">{{ label }}<span v-if="sub" class="sub">{{ sub }}</span></div>
      <div class="value mono">{{ value }}</div>
      <div v-if="hint" class="hint">{{ hint }}</div>
    </div>
    <div class="glow" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ label: string; sub?: string; value: string | number; hint?: string; tone?: string }>(),
  { sub: '', hint: '', tone: '#38c6f1' },
)
</script>

<style scoped>
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 12px;
  background: var(--soc-panel);
  border: 1px solid var(--soc-border);
  overflow: hidden;
  transition: border-color 0.25s, transform 0.25s;
}
.stat-card:hover {
  border-color: var(--soc-border-bright);
  transform: translateY(-1px);
}
.icon-wrap {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: var(--tone);
  background: color-mix(in srgb, var(--tone) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--tone) 35%, transparent);
}
.label {
  font-size: 12px;
  color: var(--soc-muted);
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.sub {
  font-size: 10px;
  color: var(--soc-faint);
  letter-spacing: 0.4px;
}
.value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 4px;
  line-height: 1.1;
}
.hint {
  font-size: 11px;
  color: var(--soc-faint);
  margin-top: 4px;
}
.glow {
  position: absolute;
  right: -30px;
  top: -30px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--tone) 16%, transparent), transparent 70%);
  pointer-events: none;
}
</style>
