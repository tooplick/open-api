<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ current: number; size: number; total: number }>()
const emit = defineEmits<{ (e: 'update:current', value: number): void }>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

const items = computed<(number | '...')[]>(() => {
  const last = totalPages.value
  const cur = props.current
  if (last <= 7) {
    return Array.from({ length: last }, (_, i) => i + 1)
  }
  const result: (number | '...')[] = [1]
  const start = Math.max(2, cur - 1)
  const end = Math.min(last - 1, cur + 1)
  if (start > 2) result.push('...')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < last - 1) result.push('...')
  result.push(last)
  return result
})

function go(page: number): void {
  if (page >= 1 && page <= totalPages.value && page !== props.current) {
    emit('update:current', page)
  }
}
</script>

<template>
  <div class="pager">
    <span class="pager-total">共 {{ total }} 条</span>
    <div class="pager-btns">
      <button class="pager-btn" type="button" :disabled="current <= 1" @click="go(current - 1)">
        上一页
      </button>
      <template v-for="(it, idx) in items" :key="idx">
        <span v-if="it === '...'" class="pager-gap">…</span>
        <button
          v-else
          type="button"
          :class="['pager-btn', { active: it === current }]"
          @click="go(it)"
        >
          {{ it }}
        </button>
      </template>
      <button
        class="pager-btn"
        type="button"
        :disabled="current >= totalPages"
        @click="go(current + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 4px 4px;
  flex-wrap: wrap;
}

.pager-total {
  font-size: 13px;
  color: var(--color-text-soft);
}

.pager-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager-btn {
  min-width: 34px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  color: var(--color-text);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
}

.pager-btn:hover:not(:disabled):not(.active) {
  background: var(--color-hover);
}

.pager-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
  cursor: default;
}

.pager-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pager-gap {
  color: var(--color-text-faint);
  padding: 0 2px;
}
</style>
