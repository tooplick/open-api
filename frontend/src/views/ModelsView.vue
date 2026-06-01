<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createModel, deleteModel, pageModels, updateModel } from '@/api/model'
import type { Model, ModelRequest } from '@/types'
import { useAuthStore } from '@/stores/auth'
import BaseModal from '@/components/BaseModal.vue'
import BasePagination from '@/components/BasePagination.vue'
import { toast } from '@/composables/useToast'
import { confirmDialog } from '@/composables/useConfirm'

const auth = useAuthStore()
const list = ref<Model[]>([])
const loading = ref(false)
const total = ref(0)
const current = ref(1)
const size = ref(10)
const search = ref('')

const showForm = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<ModelRequest>({
  modelName: '',
  displayName: '',
  type: 'chat',
  promptPrice: 0,
  completionPrice: 0,
  status: 1,
  remark: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageModels({
      current: current.value,
      size: size.value,
      modelName: search.value.trim() || undefined,
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
    modelName: '',
    displayName: '',
    type: 'chat',
    promptPrice: 0,
    completionPrice: 0,
    status: 1,
    remark: '',
  })
  showForm.value = true
}

function openEdit(m: Model): void {
  editingId.value = m.id
  Object.assign(form, {
    modelName: m.modelName,
    displayName: m.displayName ?? '',
    type: m.type,
    promptPrice: m.promptPrice,
    completionPrice: m.completionPrice,
    status: m.status,
    remark: m.remark ?? '',
  })
  showForm.value = true
}

async function submit(): Promise<void> {
  if (!form.modelName.trim()) {
    toast.error('请输入模型标识')
    return
  }
  saving.value = true
  const payload: ModelRequest = {
    modelName: form.modelName.trim(),
    displayName: form.displayName || undefined,
    type: form.type,
    promptPrice: Number(form.promptPrice) || 0,
    completionPrice: Number(form.completionPrice) || 0,
    status: form.status,
    remark: form.remark || undefined,
  }
  try {
    if (editingId.value) {
      await updateModel(editingId.value, payload)
      toast.success('已更新')
    } else {
      await createModel(payload)
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

async function remove(m: Model): Promise<void> {
  const ok = await confirmDialog({
    message: `确定删除模型「${m.modelName}」?`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await deleteModel(m.id)
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
        <input v-model="search" class="input" style="width: 220px" placeholder="搜索模型标识" />
        <button class="btn" type="submit">搜索</button>
      </form>
      <button v-if="auth.isAdmin" class="btn btn-primary" type="button" @click="openCreate">
        + 新建模型
      </button>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="list.length === 0" class="state-box">暂无模型</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>模型标识</th>
              <th>显示名</th>
              <th>类型</th>
              <th>输入单价</th>
              <th>输出单价</th>
              <th>状态</th>
              <th v-if="auth.isAdmin" class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in list" :key="m.id">
              <td class="mono">{{ m.modelName }}</td>
              <td>{{ m.displayName || '—' }}</td>
              <td><span class="badge badge-gray">{{ m.type }}</span></td>
              <td class="mono">{{ m.promptPrice }}</td>
              <td class="mono">{{ m.completionPrice }}</td>
              <td>
                <span :class="['badge', m.status === 1 ? 'badge-green' : 'badge-gray']">
                  {{ m.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td v-if="auth.isAdmin" class="text-right nowrap">
                <button class="btn btn-sm" type="button" @click="openEdit(m)">编辑</button>
                <button class="btn btn-sm btn-danger" type="button" @click="remove(m)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card-pad" style="padding-top: 0">
        <BasePagination :current="current" :size="size" :total="total" @update:current="goPage" />
      </div>
    </div>

    <BaseModal :open="showForm" :title="editingId ? '编辑模型' : '新建模型'" width="520px" @close="showForm = false">
      <div class="field">
        <label class="field-label">模型标识<span class="req">*</span></label>
        <input v-model.trim="form.modelName" class="input" placeholder="如 gpt-4o" :disabled="!!editingId" />
        <span v-if="editingId" class="field-hint">模型标识创建后不建议修改</span>
      </div>
      <div class="grid2">
        <div class="field">
          <label class="field-label">显示名</label>
          <input v-model.trim="form.displayName" class="input" placeholder="如 GPT-4o" />
        </div>
        <div class="field">
          <label class="field-label">类型</label>
          <select v-model="form.type" class="select">
            <option value="chat">chat</option>
            <option value="embedding">embedding</option>
            <option value="image">image</option>
          </select>
        </div>
      </div>
      <div class="grid2">
        <div class="field">
          <label class="field-label">输入单价(每 token 点数)</label>
          <input v-model.number="form.promptPrice" class="input" type="number" min="0" step="0.0001" />
        </div>
        <div class="field">
          <label class="field-label">输出单价(每 token 点数)</label>
          <input v-model.number="form.completionPrice" class="input" type="number" min="0" step="0.0001" />
        </div>
      </div>
      <div class="field">
        <label class="field-label">状态</label>
        <select v-model.number="form.status" class="select">
          <option :value="1">启用</option>
          <option :value="0">禁用</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label">备注</label>
        <textarea v-model.trim="form.remark" class="textarea" placeholder="可选" />
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
@media (max-width: 560px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
