<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
  size?: 'small' | 'default' | 'large'
  disabled?: boolean
  loading?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    class="apple-btn"
    :class="[variant, size]"
    :disabled="disabled || loading"
    @click="$emit('click')"
  >
    <span v-if="loading" class="spinner" />
    <slot />
  </button>
</template>

<style scoped>
.apple-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: var(--apple-radius-sm);
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  background: var(--apple-bg-secondary);
  color: var(--apple-text);
  box-shadow: var(--apple-shadow);
}

.apple-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.apple-btn.size-small {
  padding: 6px 14px;
  font-size: 13px;
}

.apple-btn.size-default {
  padding: 10px 20px;
  font-size: 14px;
}

.apple-btn.size-large {
  padding: 14px 28px;
  font-size: 16px;
}

.apple-btn:hover:not(:disabled) {
  background: var(--apple-bg-tertiary);
  box-shadow: var(--apple-shadow-md);
}

.apple-btn:active:not(:disabled) {
  background: var(--apple-bg-secondary);
  box-shadow: var(--apple-shadow-inset);
  transform: scale(0.98);
}

.apple-btn.variant-primary {
  background: var(--apple-primary);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 122, 255, 0.3), 0 1px 2px rgba(0, 0, 0, 0.04);
}

.apple-btn.variant-primary:hover:not(:disabled) {
  background: var(--apple-primary-hover);
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.35), 0 2px 4px rgba(0, 0, 0, 0.06);
}

.apple-btn.variant-primary:active:not(:disabled) {
  background: var(--apple-primary-active);
  box-shadow: var(--apple-shadow-inset);
}

.apple-btn.variant-success {
  background: var(--apple-success);
  color: white;
  box-shadow: 0 2px 8px rgba(52, 199, 89, 0.3);
}

.apple-btn.variant-success:hover:not(:disabled) {
  background: #2db84e;
}

.apple-btn.variant-warning {
  background: var(--apple-warning);
  color: white;
  box-shadow: 0 2px 8px rgba(255, 149, 0, 0.3);
}

.apple-btn.variant-warning:hover:not(:disabled) {
  background: #e68a00;
}

.apple-btn.variant-danger {
  background: var(--apple-danger);
  color: white;
  box-shadow: 0 2px 8px rgba(255, 59, 48, 0.3);
}

.apple-btn.variant-danger:hover:not(:disabled) {
  background: #e6352b;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
