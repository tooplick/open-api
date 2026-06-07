<script setup lang="ts">
import type { Component } from 'vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  BoxesIcon,
  ChevronsUpDownIcon,
  CircleHelpIcon,
  CircleUserIcon,
  KeyRoundIcon,
  LayoutDashboardIcon,
  LogOutIcon,
  NetworkIcon,
  ScrollTextIcon,
  SearchIcon,
  SettingsIcon,
  UsersIcon,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { TOUR_COMPLETED_KEY, TOUR_PENDING_KEY } from '@/utils/constants'
import AppSidebarCollapse from './AppSidebarCollapse.vue'
import ClaudeMark from './icons/ClaudeMark.vue'
import AppTour from './AppTour.vue' // 渲染 overlay;通过 ref 调用 start()
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Separator } from '@/components/ui/separator'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

interface NavItem {
  name: string
  label: string
  icon: Component
  admin?: boolean
}

const nav: NavItem[] = [
  { name: 'dashboard', label: '概览', icon: LayoutDashboardIcon },
  { name: 'keys', label: 'API Key', icon: KeyRoundIcon },
  { name: 'models', label: '模型', icon: BoxesIcon },
  { name: 'channels', label: '渠道管理', icon: NetworkIcon, admin: true },
  { name: 'users', label: '用户管理', icon: UsersIcon, admin: true },
  { name: 'logs', label: '调用日志', icon: ScrollTextIcon },
  { name: 'profile', label: '个人资料', icon: CircleUserIcon },
  { name: 'settings', label: '系统设置', icon: SettingsIcon, admin: true },
]

const visibleNav = computed(() => nav.filter(n => !n.admin || auth.isAdmin))
const pageTitle = computed(() => route.meta.title ?? '')

const initials = computed(() => (auth.user?.username ?? '?').slice(0, 2).toUpperCase())

function logout(): void {
  auth.clear()
  void router.push({ name: 'login' })
}

// ⌘K command palette
const cmdOpen = ref(false)
const query = ref('')
const appTourRef = ref<InstanceType<typeof AppTour> | null>(null)
const filteredNav = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return visibleNav.value
  return visibleNav.value.filter(n => n.label.toLowerCase().includes(q))
})

function runCommand(name: string): void {
  cmdOpen.value = false
  query.value = ''
  if (route.name !== name) void router.push({ name })
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    cmdOpen.value = !cmdOpen.value
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  if (
    localStorage.getItem(TOUR_PENDING_KEY) === '1'
    && localStorage.getItem(TOUR_COMPLETED_KEY) !== '1'
  ) {
    localStorage.removeItem(TOUR_PENDING_KEY)
    window.setTimeout(() => appTourRef.value?.start(), 250)
  }
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <SidebarProvider>
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <div class="flex items-center gap-2 py-1.5">
          <span
            class="bg-primary-soft text-primary ring-primary/15 flex aspect-square size-8 items-center justify-center rounded-lg ring-1"
          >
            <ClaudeMark class="size-5" />
          </span>
          <div class="grid flex-1 text-left leading-tight group-data-[collapsible=icon]:hidden">
            <span class="truncate text-sm font-semibold">Open Platform</span>
            <span class="text-muted-foreground truncate text-xs">大模型聚合网关</span>
          </div>
        </div>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>导航</SidebarGroupLabel>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in visibleNav" :key="item.name" :data-tour-nav="item.name">
              <SidebarMenuButton
                as-child
                :is-active="route.name === item.name"
                :tooltip="item.label"
              >
                <RouterLink :to="{ name: item.name }">
                  <component :is="item.icon" />
                  <span>{{ item.label }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <AppSidebarCollapse />
      </SidebarFooter>
    </Sidebar>

    <SidebarInset class="min-w-0">
      <header
        class="bg-background/80 sticky top-0 z-10 flex h-16 shrink-0 items-center gap-2 border-b px-6 backdrop-blur md:px-10 lg:px-12"
      >
        <SidebarTrigger class="-ml-1 md:hidden" />
        <Separator orientation="vertical" class="mr-1 h-4 md:hidden" />
        <Breadcrumb>
          <BreadcrumbList>
            <template v-if="route.name === 'dashboard'">
              <BreadcrumbItem>
                <BreadcrumbPage>概览</BreadcrumbPage>
              </BreadcrumbItem>
            </template>
            <template v-else>
              <BreadcrumbItem class="hidden md:block">
                <BreadcrumbLink as-child>
                  <RouterLink :to="{ name: 'dashboard' }">概览</RouterLink>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator class="hidden md:block" />
              <BreadcrumbItem>
                <BreadcrumbPage>{{ pageTitle }}</BreadcrumbPage>
              </BreadcrumbItem>
            </template>
          </BreadcrumbList>
        </Breadcrumb>

        <div class="ml-auto flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            class="text-muted-foreground"
            @click="appTourRef?.start()"
          >
            <CircleHelpIcon class="size-4" />
            <span class="sr-only">使用教程</span>
          </Button>

          <Button
            variant="outline"
            size="sm"
            class="text-muted-foreground gap-2 font-normal"
            @click="cmdOpen = true"
          >
            <SearchIcon />
            <span class="hidden sm:inline">搜索</span>
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="sm" class="gap-2">
                <Avatar class="size-7">
                  <AvatarFallback class="bg-primary text-primary-foreground text-xs">
                    {{ initials }}
                  </AvatarFallback>
                </Avatar>
                <span class="hidden text-sm font-medium sm:inline">{{ auth.user?.username }}</span>
                <ChevronsUpDownIcon class="text-muted-foreground hidden size-4 sm:inline" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" class="w-56">
              <DropdownMenuLabel>
                <div class="flex flex-col gap-1">
                  <span class="text-sm font-medium">{{ auth.user?.username }}</span>
                  <span class="text-muted-foreground truncate text-xs font-normal">
                    {{ auth.user?.email || '未设置邮箱' }}
                  </span>
                  <Badge :variant="auth.isAdmin ? 'info' : 'muted'" class="mt-1 w-fit">
                    {{ auth.isAdmin ? '管理员' : '普通用户' }}
                  </Badge>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem @click="router.push({ name: 'profile' })">
                  <CircleUserIcon />
                  个人资料
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" @click="logout">
                <LogOutIcon />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>

      <main class="flex-1 overflow-x-hidden px-6 py-6 md:px-10 md:py-8 lg:px-12 lg:py-10">
        <RouterView />
      </main>
    </SidebarInset>

    <CommandDialog v-model:open="cmdOpen">
      <CommandInput v-model="query" placeholder="跳转到页面…" />
      <CommandList>
        <CommandEmpty>没有匹配的页面</CommandEmpty>
        <CommandGroup heading="导航">
          <CommandItem
            v-for="item in filteredNav"
            :key="item.name"
            :value="item.name"
            @select="runCommand(item.name)"
          >
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>

    <AppTour ref="appTourRef" />
  </SidebarProvider>
</template>
