<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ConfigProvider } from 'reka-ui'
import { Toaster } from '@/components/ui/sonner'
</script>

<template>
  <ConfigProvider :scroll-body="false">
    <RouterView />
    <Toaster position="top-center" rich-colors :swipe-directions="['left', 'right']" />
  </ConfigProvider>
</template>
