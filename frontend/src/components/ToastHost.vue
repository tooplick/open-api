<script setup lang="ts">
import { useToasts } from '@/composables/useToast'

const { items, remove } = useToasts()
</script>

<template>
  <div class="toast-host">
    <transition-group name="toast">
      <div
        v-for="t in items"
        :key="t.id"
        :class="['toast', `toast-${t.type}`]"
        @click="remove(t.id)"
      >
        <span class="toast-icon" aria-hidden="true">
          <template v-if="t.type === 'success'">✓</template>
          <template v-else-if="t.type === 'error'">!</template>
          <template v-else>i</template>
        </span>
        <span class="toast-msg">{{ t.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 240px;
  max-width: 380px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: #fff;
  color: var(--color-text);
  box-shadow: var(--shadow-lg);
  border-left: 4px solid var(--color-text-faint);
  cursor: pointer;
  pointer-events: auto;
  font-size: 14px;
}

.toast-success {
  border-left-color: var(--color-success);
}
.toast-error {
  border-left-color: var(--color-danger);
}
.toast-info {
  border-left-color: var(--color-primary);
}

.toast-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.toast-success .toast-icon {
  background: var(--color-success);
}
.toast-error .toast-icon {
  background: var(--color-danger);
}
.toast-info .toast-icon {
  background: var(--color-primary);
}

.toast-msg {
  word-break: break-word;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
