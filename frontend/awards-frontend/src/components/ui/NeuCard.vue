<script setup lang="ts">
defineProps<{
  variant?: 'glass' | 'solid' | 'flat'
  hoverable?: boolean
  padding?: string
}>()
</script>

<template>
  <div
    class="apple-card"
    :class="[variant, { hoverable }]"
    :style="{ padding: padding || '20px' }"
  >
    <slot />
  </div>
</template>

<style scoped>
.apple-card {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-radius: var(--apple-radius-lg);
  border: 1px solid var(--apple-border);
  box-shadow: var(--apple-shadow);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.apple-card.glass {
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
}

.apple-card.solid {
  background: var(--apple-bg);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.apple-card.flat {
  background: var(--apple-bg-secondary);
  border: none;
  box-shadow: none;
}

.apple-card.hoverable:hover {
  background: var(--apple-glass-hover);
  box-shadow: var(--apple-shadow-md);
  transform: translateY(-1px);
}

.apple-card.hoverable:active {
  background: var(--apple-glass-active);
  box-shadow: var(--apple-shadow);
  transform: translateY(0);
}
</style>
