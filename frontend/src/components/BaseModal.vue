<script setup lang="ts">
import { onUnmounted, watch } from 'vue'

const props = withDefaults(
  defineProps<{ open: boolean; title?: string; width?: string }>(),
  { title: '', width: '480px' },
)
const emit = defineEmits<{ (e: 'close'): void }>()

function onKey(e: KeyboardEvent): void {
  if (e.key === 'Escape' && props.open) emit('close')
}

watch(
  () => props.open,
  (v) => {
    if (v) document.addEventListener('keydown', onKey)
    else document.removeEventListener('keydown', onKey)
  },
)

onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <teleport to="body">
    <transition name="modal">
      <div v-if="open" class="modal-overlay" @mousedown.self="emit('close')">
        <div class="modal" :style="{ width }">
          <div class="modal-head">
            <h3 class="modal-title">{{ title }}</h3>
            <button class="modal-x" type="button" aria-label="关闭" @click="emit('close')">×</button>
          </div>
          <div class="modal-body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="modal-foot">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(27, 28, 28, 0.4);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 64px 16px 32px;
  z-index: 2000;
  overflow-y: auto;
}

.modal {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  max-width: 100%;
  display: flex;
  flex-direction: column;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
}

.modal-x {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--color-text-faint);
  cursor: pointer;
  padding: 0 4px;
}
.modal-x:hover {
  color: var(--color-text);
}

.modal-body {
  padding: 24px;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .modal,
.modal-leave-active .modal {
  transition: transform 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from .modal,
.modal-leave-to .modal {
  transform: translateY(-12px);
}
</style>
