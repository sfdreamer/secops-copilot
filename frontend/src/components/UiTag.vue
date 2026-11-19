<template>
  <span class="ui-tag" :style="style">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { tint } from '@/utils/labels'

const props = defineProps<{ text: string; color?: string }>()

const style = computed(() => {
  const c = props.color ?? '#8aa6c6'
  return {
    color: c,
    background: tint(c, 0.13),
    border: `1px solid ${tint(c, 0.4)}`,
  }
})
</script>

<style scoped>
.ui-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  white-space: nowrap;
}
</style>
