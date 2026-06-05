import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { TOUR_COMPLETED_KEY, TOUR_PENDING_KEY } from '@/utils/constants'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface StepConfig {
  /** 目标元素 CSS 选择器; null = 居中显示 */
  element: string | null
  /** 路由名称,用于跨页导航 */
  routeName: string
  title: string
  description: string
  /** 弹出位置偏好 */
  side?: 'top' | 'bottom' | 'left' | 'right'
}

/* ------------------------------------------------------------------ */
/*  步骤定义 — 跨页面连续编号                                            */
/* ------------------------------------------------------------------ */

function buildSteps(isAdmin: boolean): StepConfig[] {
  // 顺序与侧边栏导航一致:概览 → API Key → 模型 → [渠道管理] → [用户管理] → 调用日志 → 个人资料 → [系统设置]
  const steps: StepConfig[] = [
    // ---- 概览 ----
    {
      element: null,
      routeName: 'dashboard',
      title: '欢迎使用 AI Open Platform',
      description: '这是一个聚合多家大模型供应商的统一网关平台。接下来带你快速了解核心功能。',
    },
    {
      element: '[data-tour="dashboard-stats"]',
      routeName: 'dashboard',
      title: '用量总览',
      description: '这里展示 API 调用次数和 Token 消耗统计。管理员可查看全部用户的汇总数据。',
      side: 'bottom',
    },
    // ---- API Key ----
    {
      element: '[data-tour="create-key-btn"]',
      routeName: 'keys',
      title: '创建 API Key',
      description: '点击这里创建 API Key。获取后配置到 OpenAI SDK 的 api_key 字段，base_url 指向本平台 /v1 即可调用。',
      side: 'bottom',
    },
    // ---- 模型 ----
    {
      element: '[data-tour="model-list"]',
      routeName: 'models',
      title: '可用模型',
      description: '列出所有已接入渠道提供的模型，由渠道配置自动聚合，无需手动维护。',
      side: 'top',
    },
    // ---- 渠道管理 (admin) ----
    ...(isAdmin
      ? [{
          element: '[data-tour="channel-table"]' as string,
          routeName: 'channels',
          title: '渠道管理',
          description: '渠道是上游 AI 服务商的接入配置。每个渠道声明支持的模型和分组，系统自动构建路由表。',
          side: 'top' as const,
        }]
      : []),
    // ---- 用户管理 (admin) ----
    ...(isAdmin
      ? [{
          element: '[data-tour="user-table"]' as string,
          routeName: 'users',
          title: '用户管理',
          description: '管理平台用户，包括启用/禁用账户。',
          side: 'top' as const,
        }]
      : []),
    // ---- 调用日志 ----
    {
      element: '[data-tour="log-table"]',
      routeName: 'logs',
      title: '调用日志',
      description: '每次 API 调用的详细记录：模型、Token 用量、耗时、状态等。点击行可展开完整信息。',
      side: 'top',
    },
    // ---- 个人资料 ----
    {
      element: '[data-tour="profile-card"]',
      routeName: 'profile',
      title: '个人资料',
      description: '查看账户信息、修改密码。',
      side: 'right',
    },
    // ---- 系统设置 (admin) ----
    ...(isAdmin
      ? [{
          element: '[data-tour="settings-form"]' as string,
          routeName: 'settings',
          title: '系统设置',
          description: '配置站点名称、注册开关、默认分组和 SMTP 邮件服务。保存后即时生效。',
          side: 'top' as const,
        }]
      : []),
    // ---- 完成 ----
    {
      element: null,
      routeName: '',
      title: '教程完成！🎉',
      description: '你已了解平台的核心功能。随时可通过顶部导航栏的帮助按钮 (?) 重新查看本教程。',
    },
  ]

  return steps
}

/* ------------------------------------------------------------------ */
/*  Singleton 状态 (module-level,跨组件共享)                             */
/* ------------------------------------------------------------------ */

const currentStep = ref(0)
const isActive = ref(false)

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

export function useTour(router: Router) {
  const auth = useAuthStore()
  const steps = computed(() => buildSteps(auth.isAdmin))
  const step = computed(() => steps.value[currentStep.value])
  const totalSteps = computed(() => steps.value.length)

  const targetEl = shallowRef<Element | null>(null)
  const targetRect = ref<DOMRect | null>(null)

  /* ---- 元素查找 ---- */

  function findTarget(): void {
    const cfg = step.value
    if (!cfg.element) {
      targetEl.value = null
      targetRect.value = null
      return
    }
    if (router.currentRoute.value.name !== cfg.routeName) {
      targetEl.value = null
      targetRect.value = null
      return
    }
    const el = document.querySelector(cfg.element)
    if (el && el.clientWidth > 0 && el.clientHeight > 0) {
      targetEl.value = el
      targetRect.value = el.getBoundingClientRect()
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    } else {
      targetEl.value = null
      targetRect.value = null
    }
  }

  function refreshRect(): void {
    if (targetEl.value) {
      targetRect.value = targetEl.value.getBoundingClientRect()
    }
  }

  /* ---- 步骤切换 ---- */

  async function applyStep(): Promise<void> {
    const cfg = step.value
    if (!cfg) return

    // 需要导航时先跳转并等待渲染
    if (cfg.routeName && router.currentRoute.value.name !== cfg.routeName) {
      await router.push({ name: cfg.routeName })
      await waitForElement(cfg.element, cfg.routeName, 5000)
    }

    await nextTick()
    findTarget()
  }

  function waitForElement(
    selector: string | null,
    routeName: string,
    timeout = 5000,
  ): Promise<void> {
    return new Promise((resolve) => {
      if (!selector) { resolve(); return }

      const start = Date.now()
      const check = (): void => {
        if (router.currentRoute.value.name !== routeName) {
          resolve(); return
        }
        const el = document.querySelector(selector)
        if (el && el.clientWidth > 0 && el.clientHeight > 0) {
          resolve()
        } else if (Date.now() - start >= timeout) {
          resolve()
        } else {
          setTimeout(check, 100)
        }
      }
      check()
    })
  }

  /* ---- 公开操作 ---- */

  function start(): void {
    currentStep.value = 0
    isActive.value = true
    void applyStep()
  }

  function next(): void {
    if (currentStep.value < steps.value.length - 1) {
      currentStep.value++
    } else {
      end()
    }
  }

  function prev(): void {
    if (currentStep.value > 0) {
      currentStep.value--
    }
  }

  function skip(): void {
    end()
  }

  function end(): void {
    isActive.value = false
    targetEl.value = null
    targetRect.value = null
    localStorage.setItem(TOUR_COMPLETED_KEY, '1')
    localStorage.removeItem(TOUR_PENDING_KEY)
  }

  function goTo(idx: number): void {
    if (idx >= 0 && idx < steps.value.length) {
      currentStep.value = idx
    }
  }

  /* ---- 生命周期绑定 ---- */

  let scrollHandler: (() => void) | null = null
  let resizeHandler: (() => void) | null = null
  let routeUnwatch: (() => void) | null = null
  let stepUnwatch: (() => void) | null = null

  function bind(): void {
    scrollHandler = refreshRect
    resizeHandler = refreshRect
    window.addEventListener('scroll', scrollHandler, { passive: true })
    window.addEventListener('resize', resizeHandler)

    // 路由变化后重新查找元素(处理手动导航)
    routeUnwatch = watch(
      () => router.currentRoute.value.name,
      () => { if (isActive.value) { void nextTick(findTarget) } },
    )

    // 步骤变化时执行切换
    stepUnwatch = watch(currentStep, () => { if (isActive.value) void applyStep() })
  }

  function unbind(): void {
    if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
    if (resizeHandler) window.removeEventListener('resize', resizeHandler)
    scrollHandler = null
    resizeHandler = null
    routeUnwatch?.()
    stepUnwatch?.()
  }

  onMounted(bind)
  onBeforeUnmount(unbind)

  return {
    // 状态
    isActive,
    currentStep,
    step,
    steps,
    totalSteps,
    targetEl,
    targetRect,
    // 操作
    start,
    next,
    prev,
    skip,
    end,
    goTo,
    // 便捷属性
    isFirst: computed(() => currentStep.value === 0),
    isLast: computed(() => currentStep.value === steps.value.length - 1),
  }
}
