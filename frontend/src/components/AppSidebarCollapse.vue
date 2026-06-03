<script setup lang="ts">
import { PanelLeftCloseIcon, PanelLeftOpenIcon } from '@lucide/vue'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'

const { state, toggleSidebar } = useSidebar()
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <SidebarMenuButton
        as-child
        :tooltip="state === 'collapsed' ? '展开侧边栏' : '收起侧边栏'"
      >
        <button type="button" @click="toggleSidebar">
          <component :is="state === 'collapsed' ? PanelLeftOpenIcon : PanelLeftCloseIcon" />
          <span style="color: #71717a">收起侧边栏</span>
        </button>
      </SidebarMenuButton>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
