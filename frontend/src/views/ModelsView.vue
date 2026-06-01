<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listModels } from '@/api/model'

const models = ref<string[]>([])
const loading = ref(false)
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
    <div class="row spread" style="margin-bottom: 24px; flex-wrap: wrap; gap: 12px">
      <input v-model="search" class="input" style="width: 260px" placeholder="搜索模型" />
      <span class="muted">共 {{ models.length }} 个可用模型(由各渠道聚合得到,不可手动增删)</span>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="models.length === 0" class="state-box">
        暂无可用模型,请先在「渠道管理」中接入渠道并配置其支持的模型
      </div>
      <div v-else-if="filtered.length === 0" class="state-box">没有匹配的模型</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>模型标识</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in filtered" :key="m">
              <td class="mono">{{ m }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
