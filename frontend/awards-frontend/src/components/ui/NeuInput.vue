<script setup lang="ts">
const model = defineModel<string>()

defineProps<{
  placeholder?: string
  type?: 'text' | 'password' | 'email' | 'number'
  disabled?: boolean
  size?: 'small' | 'default' | 'large'
}>()
</script>

<template>
  <div class="apple-input-wrapper" :class="[size, { disabled }]">
    <input
      v-model="model"
      :type="type || 'text'"
      :placeholder="placeholder"
      :disabled="disabled"
      class="apple-input"
    />
  </div>
</template>

<style scoped>
.apple-input-wrapper {
  position: relative;
  display: inline-flex;
  width: 100%;
}

.apple-input {
  width: 100%;
  background: var(--apple-bg-secondary);
  border: 1px solid var(--apple-border);
  border-radius: var(--apple-radius-sm);
  color: var(--apple-text);
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: all 0.2s ease;
}

.apple-input::placeholder {
  color: var(--apple-text-quaternary);
}

.apple-input-wrapper.size-small .apple-input {
  padding: 8px 12px;
  font-size: 13px;
}

.apple-input-wrapper.size-default .apple-input {
  padding: 10px 14px;
}

.apple-input-wrapper.size-large .apple-input {
  padding: 14px 18px;
  font-size: 16px;
}

.apple-input:hover:not(:disabled) {
  border-color: rgba(0, 0, 0, 0.1);
}

.apple-input:focus {
  background: var(--apple-bg);
  border-color: var(--apple-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.15);
}

.apple-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.apple-input-wrapper.disabled {
  opacity: 0.5;
}
</style>
