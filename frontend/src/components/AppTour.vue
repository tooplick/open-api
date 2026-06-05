<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useTour } from '@/composables/useTour'

const router = useRouter()
const {
  isActive,
  currentStep,
  step,
  totalSteps,
  targetRect,
  start,
  next,
  prev,
  skip,
  isFirst,
  isLast,
} = useTour(router)

defineExpose({ start })

/* ---- 卡片位置计算 ---- */

const cardStyle = computed(() => {
  if (!isActive.value) return { display: 'none' }

  const rect = targetRect.value
  const GAP = 16
  const CARD_MAX_H = 280

  if (!rect) {
    // 无目标元素 → 居中
    return {
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
    }
  }

  const vw = window.innerWidth
  const vh = window.innerHeight

  // 优先放下方
  const spaceBelow = vh - rect.bottom - GAP
  const spaceAbove = rect.top - GAP
  let top: number

  if (spaceBelow >= CARD_MAX_H) {
    top = rect.bottom + GAP
  } else if (spaceAbove >= CARD_MAX_H) {
    top = rect.top - CARD_MAX_H - GAP
  } else {
    // 空间不足,居中
    return {
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
    }
  }

  // 水平方向:与目标左对齐,不超出视口
  const CARD_W = 400
  let left = rect.left
  if (left + CARD_W + GAP > vw) left = vw - CARD_W - GAP
  if (left < GAP) left = GAP

  return {
    left: `${left}px`,
    top: `${top}px`,
  }
})

/* ---- 高亮区域 ---- */

const highlightStyle = computed(() => {
  const rect = targetRect.value
  if (!rect) return { display: 'none' }
  const PAD = 6
  return {
    left: `${rect.left - PAD}px`,
    top: `${rect.top - PAD}px`,
    width: `${rect.width + PAD * 2}px`,
    height: `${rect.height + PAD * 2}px`,
  }
})

/* ---- SVG 遮罩层路径 ---- */

const overlayPath = computed(() => {
  const vw = typeof window !== 'undefined' ? window.innerWidth : 1920
  const vh = typeof window !== 'undefined' ? window.innerHeight : 1080
  const R = 10

  if (!targetRect.value) {
    // 无目标 → 全屏遮罩,无镂空
    return `M0,0H${vw}V${vh}H0Z`
  }

  const PAD = 6
  const rect = targetRect.value
  const x = rect.left - PAD
  const y = rect.top - PAD
  const w = rect.width + PAD * 2
  const h = rect.height + PAD * 2
  const r = Math.min(R, w / 2, h / 2)

  // 外层矩形(顺时针) + 内层圆角矩形(逆时针) → evenodd 镂空
  return (
    `M0,0H${vw}V${vh}H0Z`
    + `M${x + r},${y}`
    + `H${x + w - r}A${r},${r},0,0,1,${x + w},${y + r}`
    + `V${y + h - r}A${r},${r},0,0,1,${x + w - r},${y + h}`
    + `H${x + r}A${r},${r},0,0,1,${x},${y + h - r}`
    + `V${y + r}A${r},${r},0,0,1,${x + r},${y}Z`
  )
})
</script>

<template>
  <Teleport to="body">
    <Transition name="tour-fade">
      <div v-if="isActive" class="tour-overlay" @click.self="skip">
        <!-- SVG 遮罩:镂空高亮区域 -->
        <svg
          class="tour-mask"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
        >
          <path
            :d="overlayPath"
            fill="var(--foreground)"
            fill-opacity="0.45"
            fill-rule="evenodd"
            pointer-events="auto"
            @click.self="skip"
          />
        </svg>

        <!-- 高亮边框 -->
        <div
          v-if="targetRect"
          class="tour-highlight"
          :style="highlightStyle"
        />

        <!-- 教程卡片 -->
        <div class="tour-card" :style="cardStyle">
          <!-- 头部:图标 + 标题 -->
          <div class="tour-card-header">
            <span class="tour-card-icon">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="size-5"
              >
                <circle cx="12" cy="12" r="10" />
                <path d="M12 16v-4" />
                <path d="M12 8h.01" />
              </svg>
            </span>
            <div class="tour-card-title-group">
              <h3 class="tour-card-title">
                {{ step?.title }}
              </h3>
              <p class="tour-card-desc">
                {{ step?.description }}
              </p>
            </div>
          </div>

          <!-- 底部:进度 + 跳过 + 导航按钮 -->
          <div class="tour-card-footer">
            <div class="tour-card-footer-left">
              <span class="tour-progress">{{ currentStep + 1 }} / {{ totalSteps }}</span>
              <button class="tour-btn tour-btn-skip" @click="skip">
                跳过教程
              </button>
            </div>
            <div class="tour-card-footer-right">
              <button
                v-if="!isFirst"
                class="tour-btn tour-btn-prev"
                @click="prev"
              >
                上一步
              </button>
              <button
                class="tour-btn tour-btn-next"
                @click="next"
              >
                {{ isLast ? '完成' : '下一步' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ---- 遮罩层 ---- */

.tour-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  pointer-events: auto;
}

.tour-mask {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* ---- 高亮边框 ---- */

.tour-highlight {
  position: fixed;
  z-index: 10001;
  pointer-events: none;
  border-radius: var(--radius-lg, 0.75rem);
  box-shadow:
    0 0 0 3px var(--primary),
    0 0 16px 2px color-mix(in oklab, var(--primary) 30%, transparent);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ---- 教程卡片 ---- */

.tour-card {
  position: fixed;
  z-index: 10002;
  width: 400px;
  max-width: calc(100vw - 2rem);
  background: var(--card);
  color: var(--card-foreground);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl, 1rem);
  box-shadow:
    0 20px 25px -5px rgb(0 0 0 / 0.1),
    0 8px 10px -6px rgb(0 0 0 / 0.1);
  padding: 1.5rem;
  transition: left 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              top 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ---- 头部 ---- */

.tour-card-header {
  display: flex;
  gap: 0.75rem;
}

.tour-card-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-lg, 0.75rem);
  background: var(--accent);
  color: var(--primary);
}

.tour-card-title-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
}

.tour-card-title {
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--foreground);
  margin: 0;
}

.tour-card-desc {
  font-size: 0.8125rem;
  line-height: 1.7;
  color: var(--muted-foreground);
  margin: 0;
}

/* ---- 底部 ---- */

.tour-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}

.tour-card-footer-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.tour-card-footer-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.tour-progress {
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
  color: var(--muted-foreground);
}

/* ---- 按钮 ---- */

.tour-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8125rem;
  font-weight: 500;
  border-radius: var(--radius-md, 0.5rem);
  padding: 0.375rem 0.875rem;
  border: none;
  cursor: pointer;
  transition: all 150ms;
  white-space: nowrap;
}

.tour-btn:focus-visible {
  outline: 2px solid var(--ring);
  outline-offset: 2px;
}

.tour-btn-next {
  background: var(--primary);
  color: var(--primary-foreground);
}

.tour-btn-next:hover {
  opacity: 0.9;
}

.tour-btn-prev {
  background: var(--secondary);
  color: var(--secondary-foreground);
  border: 1px solid var(--border);
}

.tour-btn-prev:hover {
  background: var(--accent);
  color: var(--accent-foreground);
}

.tour-btn-skip {
  background: transparent;
  color: var(--muted-foreground);
  padding-left: 0.5rem;
  padding-right: 0.5rem;
}

.tour-btn-skip:hover {
  color: var(--foreground);
}

/* ---- 过渡动画 ---- */

.tour-fade-enter-active,
.tour-fade-leave-active {
  transition: opacity 0.25s ease;
}

.tour-fade-enter-from,
.tour-fade-leave-to {
  opacity: 0;
}
</style>
