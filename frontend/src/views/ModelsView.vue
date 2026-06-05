<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listModels } from '@/api/model'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { Card } from '@/components/ui/card'

const models = ref<string[]>([])
const loading = ref(true)
const search = ref('')

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return models.value
  return models.value.filter((m) => m.toLowerCase().includes(q))
})

async function load(): Promise<void> {
  loading.value = true
  try {
    models.value = await listModels()
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <Input v-model="search" class="w-full sm:w-64" placeholder="搜索模型" />
      <span class="text-sm text-muted-foreground">
        共 {{ models.length }} 个可用模型(由各渠道聚合得到,不可手动增删)
      </span>
    </div>

    <Card class="gap-0 overflow-hidden p-0" data-tour="model-list">
      <div v-if="loading" class="space-y-3 p-4">
        <Skeleton v-for="i in 8" :key="i" class="h-10 w-full" />
      </div>
      <div v-else-if="models.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        暂无可用模型,请先在「渠道管理」中接入渠道并配置其支持的模型
      </div>
      <div v-else-if="filtered.length === 0" class="px-4 py-14 text-center text-muted-foreground">
        没有匹配的模型
      </div>
      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>模型标识</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="m in filtered" :key="m">
            <TableCell class="font-mono">{{ m }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </Card>
  </div>
</template>
