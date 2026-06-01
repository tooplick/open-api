<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createChannel,
  deleteChannel,
  pageChannels,
  updateChannel,
  updateChannelStatus,
} from '@/api/channel'
import type { Channel, ChannelRequest } from '@/types'
import BaseModal from '@/components/BaseModal.vue'
import BasePagination from '@/components/BasePagination.vue'
import { toast } from '@/composables/useToast'
import { confirmDialog } from '@/composables/useConfirm'

const list = ref<Channel[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const size = ref(10)
const search = ref('')

const showForm = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<ChannelRequest>({
  name: '',
  type: 'openai',
  baseUrl: '',
  apiKey: '',
  models: '',
  group: 'default',
  modelMapping: '',
  weight: 1,
  priority: 0,
  status: 1,
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageChannels({
      current: current.value,
      size: size.value,
      name: search.value.trim() || undefined,
    })
    list.value = p.records
    total.value = p.total
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function doSearch(): void {
  current.value = 1
  void load()
}

function goPage(p: number): void {
  current.value = p
  void load()
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 'openai',
    baseUrl: '',
    apiKey: '',
    models: '',
    group: 'default',
    modelMapping: '',
    weight: 1,
    priority: 0,
    status: 1,
  })
  showForm.value = true
}

function openEdit(c: Channel): void {
  editingId.value = c.id
  Object.assign(form, {
    name: c.name,
    type: c.type,
    baseUrl: c.baseUrl,
    apiKey: '',
    models: c.models,
    group: c.group,
    modelMapping: c.modelMapping ?? '',
    weight: c.weight,
    priority: c.priority,
    status: c.status,
  })
  showForm.value = true
}

async function submit(): Promise<void> {
  if (!form.name.trim() || !form.baseUrl.trim() || !form.models.trim()) {
    toast.error('请填写名称、上游地址与支持的模型')
    return
  }
  if (!form.apiKey.trim()) {
    toast.error(editingId.value ? '编辑时需重新填写上游密钥' : '请填写上游密钥')
    return
  }
  saving.value = true
  const payload: ChannelRequest = {
    name: form.name.trim(),
    type: form.type,
    baseUrl: form.baseUrl.trim(),
    apiKey: form.apiKey.trim(),
    models: form.models.trim(),
    group: form.group.trim() || 'default',
    modelMapping: form.modelMapping?.trim() || undefined,
    weight: Number(form.weight) || 1,
    priority: Number(form.priority) || 0,
    status: form.status,
  }
  try {
    if (editingId.value) {
      await updateChannel(editingId.value, payload)
      toast.success('已更新')
    } else {
      await createChannel(payload)
      toast.success('已创建')
    }
    showForm.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function toggle(c: Channel): Promise<void> {
  const next = c.status === 1 ? 0 : 1
  try {
    await updateChannelStatus(c.id, next)
    c.status = next
    toast.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器已提示
  }
}

async function remove(c: Channel): Promise<void> {
  const ok = await confirmDialog({
    message: `确定删除渠道「${c.name}」?`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await deleteChannel(c.id)
    toast.success('已删除')
    if (list.value.length === 1 && current.value > 1) current.value -= 1
    await load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="row spread" style="margin-bottom: 24px; flex-wrap: wrap; gap: 12px">
      <form class="row gap-8" @submit.prevent="doSearch">
        <input v-model="search" class="input" style="width: 220px" placeholder="搜索渠道名称" />
        <button class="btn" type="submit">搜索</button>
      </form>
      <button class="btn btn-primary" type="button" @click="openCreate">+ 新建渠道</button>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="list.length === 0" class="state-box">暂无渠道,点击右上角接入上游服务商</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>上游地址</th>
              <th>支持模型</th>
              <th>分组</th>
              <th>权重</th>
              <th>优先级</th>
              <th>状态</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in list" :key="c.id">
              <td>{{ c.name }}</td>
              <td><span class="badge badge-gray">{{ c.type }}</span></td>
              <td class="mono faint">{{ c.baseUrl }}</td>
              <td class="wrap">{{ c.models }}</td>
              <td class="wrap">{{ c.group }}</td>
              <td>{{ c.weight }}</td>
              <td>{{ c.priority }}</td>
              <td>
                <span :class="['badge', c.status === 1 ? 'badge-green' : 'badge-gray']">
                  {{ c.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td class="text-right nowrap">
                <button class="btn btn-sm" type="button" @click="toggle(c)">
                  {{ c.status === 1 ? '禁用' : '启用' }}
                </button>
                <button class="btn btn-sm" type="button" @click="openEdit(c)">编辑</button>
                <button class="btn btn-sm btn-danger" type="button" @click="remove(c)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card-pad" style="padding-top: 0">
        <BasePagination :current="current" :size="size" :total="total" @update:current="goPage" />
      </div>
    </div>

    <BaseModal :open="showForm" :title="editingId ? '编辑渠道' : '新建渠道'" width="560px" @close="showForm = false">
      <div class="grid2">
        <div class="field">
          <label class="field-label">渠道名称<span class="req">*</span></label>
          <input v-model.trim="form.name" class="input" placeholder="如:OpenAI 官方" />
        </div>
        <div class="field">
          <label class="field-label">类型</label>
          <select v-model="form.type" class="select">
            <option value="openai">openai</option>
            <option value="azure">azure</option>
            <option value="anthropic">anthropic</option>
            <option value="custom">custom</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label class="field-label">上游地址<span class="req">*</span></label>
        <input v-model.trim="form.baseUrl" class="input" placeholder="https://api.openai.com" />
        <span class="field-hint">填根地址(不含 /v1),平台转发时自动拼接请求路径</span>
      </div>
      <div class="field">
        <label class="field-label">上游密钥<span class="req">*</span></label>
        <textarea v-model.trim="form.apiKey" class="textarea" placeholder="sk-..." />
        <span class="field-hint">
          {{ editingId ? '出于安全,后端不返回原密钥,编辑保存时需重新填写' : '上游服务商的真实密钥;可换行填写多个 key(随机轮换)' }}
        </span>
      </div>
      <div class="field">
        <label class="field-label">支持的模型<span class="req">*</span></label>
        <textarea v-model.trim="form.models" class="textarea" placeholder="gpt-4o,gpt-4o-mini,claude-3-5-sonnet" />
        <span class="field-hint">逗号分隔;同一模型多渠道时按优先级取最高,再按权重随机</span>
      </div>
      <div class="field">
        <label class="field-label">分组</label>
        <input v-model.trim="form.group" class="input" placeholder="default" />
        <span class="field-hint">逗号分隔可属多组;仅同分组的 API Key 能路由到该渠道</span>
      </div>
      <div class="field">
        <label class="field-label">模型重命名映射</label>
        <textarea v-model.trim="form.modelMapping" class="textarea" placeholder='{"gpt-4":"gpt-4o"}(可选,JSON)' />
      </div>
      <div class="grid3">
        <div class="field">
          <label class="field-label">权重</label>
          <input v-model.number="form.weight" class="input" type="number" min="1" />
        </div>
        <div class="field">
          <label class="field-label">优先级</label>
          <input v-model.number="form.priority" class="input" type="number" />
        </div>
        <div class="field">
          <label class="field-label">状态</label>
          <select v-model.number="form.status" class="select">
            <option :value="1">启用</option>
            <option :value="0">禁用</option>
          </select>
        </div>
      </div>

      <template #footer>
        <button class="btn" type="button" @click="showForm = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="submit">
          <span v-if="saving" class="spinner" />
          <span v-else>保存</span>
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.grid3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 18px;
}
@media (max-width: 560px) {
  .grid2,
  .grid3 {
    grid-template-columns: 1fr;
  }
}
</style>
