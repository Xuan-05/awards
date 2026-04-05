<template>
  <div class="pc">
    <div v-if="$slots.header" class="pc-header">
      <slot name="header" />
    </div>
    <div class="pc-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.pc {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pc-header {
  padding: 16px 20px;
  border-radius: var(--apple-radius-lg);
  background: var(--apple-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--apple-border);
  box-shadow: var(--apple-shadow);
}

.pc-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
