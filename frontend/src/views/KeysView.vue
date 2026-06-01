<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createKey, deleteKey, listKeys, updateKeyStatus } from '@/api/apikey'
import type { ApiKey } from '@/types'
import BaseModal from '@/components/BaseModal.vue'
import { toast } from '@/composables/useToast'
import { confirmDialog } from '@/composables/useConfirm'
import { formatDateTime, formatQuota, localInputToIso } from '@/utils/format'

const keys = ref<ApiKey[]>([])
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', quota: 0, expireTime: '' })
const newlyCreated = ref<ApiKey | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    keys.value = await listKeys()
  } catch {
    keys.value = []
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  createForm.name = ''
  createForm.quota = 0
  createForm.expireTime = ''
  newlyCreated.value = null
  showCreate.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.name.trim()) {
    toast.error('请输入名称')
    return
  }
  creating.value = true
  try {
    const k = await createKey({
      name: createForm.name.trim(),
      quota: Number(createForm.quota) || 0,
      expireTime: createForm.expireTime ? localInputToIso(createForm.expireTime) : null,
    })
    toast.success('创建成功')
    newlyCreated.value = k
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

async function toggle(k: ApiKey): Promise<void> {
  const next = k.status === 1 ? 0 : 1
  try {
    await updateKeyStatus(k.id, next)
    k.status = next
    toast.success(next === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器已提示
  }
}

async function remove(k: ApiKey): Promise<void> {
  const ok = await confirmDialog({
    message: `确定删除 API Key「${k.name}」?删除后使用该 Key 的调用将立即失败。`,
    danger: true,
    confirmText: '删除',
  })
  if (!ok) return
  try {
    await deleteKey(k.id)
    toast.success('已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

function mask(k: string): string {
  return k.length > 14 ? `${k.slice(0, 8)}••••••${k.slice(-4)}` : k
}

async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败,请手动复制')
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="row spread" style="margin-bottom: 24px">
      <span class="muted">用于调用 OpenAI 兼容接口(/v1/**)的密钥</span>
      <button class="btn btn-primary" type="button" @click="openCreate">+ 创建 API Key</button>
    </div>

    <div class="card">
      <div v-if="loading" class="state-box"><span class="spinner" /> 加载中…</div>
      <div v-else-if="keys.length === 0" class="state-box">还没有 API Key,点击右上角创建一个吧</div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>名称</th>
              <th>Key</th>
              <th>状态</th>
              <th>额度(已用 / 总)</th>
              <th>过期时间</th>
              <th>创建时间</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in keys" :key="k.id">
              <td>{{ k.name }}</td>
              <td>
                <span class="mono">{{ mask(k.apiKey) }}</span>
                <button class="btn btn-sm btn-ghost" type="button" @click="copy(k.apiKey)">复制</button>
              </td>
              <td>
                <span :class="['badge', k.status === 1 ? 'badge-green' : 'badge-gray']">
                  {{ k.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td class="mono">{{ formatQuota(k.usedQuota, k.quota) }}</td>
              <td>{{ k.expireTime ? formatDateTime(k.expireTime) : '永不过期' }}</td>
              <td>{{ formatDateTime(k.createTime) }}</td>
              <td class="text-right nowrap">
                <button class="btn btn-sm" type="button" @click="toggle(k)">
                  {{ k.status === 1 ? '禁用' : '启用' }}
                </button>
                <button class="btn btn-sm btn-danger" type="button" @click="remove(k)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <BaseModal :open="showCreate" title="创建 API Key" @close="showCreate = false">
      <template v-if="!newlyCreated">
        <div class="field">
          <label class="field-label">名称<span class="req">*</span></label>
          <input v-model.trim="createForm.name" class="input" placeholder="如:生产环境 / 我的应用" />
        </div>
        <div class="field">
          <label class="field-label">独立额度上限</label>
          <input v-model.number="createForm.quota" class="input" type="number" min="0" />
          <span class="field-hint">0 表示不单独限额,跟随账户总额度</span>
        </div>
        <div class="field">
          <label class="field-label">过期时间</label>
          <input v-model="createForm.expireTime" class="input" type="datetime-local" />
          <span class="field-hint">留空表示永不过期</span>
        </div>
      </template>

      <template v-else>
        <p class="muted" style="margin-bottom: 10px">创建成功!请立即复制保存你的 Key:</p>
        <div class="key-box">
          <span class="mono">{{ newlyCreated.apiKey }}</span>
          <button class="btn btn-sm" type="button" @click="copy(newlyCreated.apiKey)">复制</button>
        </div>
        <p class="field-hint" style="margin-top: 10px">
          把它配置到 OpenAI SDK 的 api_key,base_url 指向本平台的 /v1 即可调用。
        </p>
      </template>

      <template #footer>
        <template v-if="!newlyCreated">
          <button class="btn" type="button" @click="showCreate = false">取消</button>
          <button class="btn btn-primary" type="button" :disabled="creating" @click="submitCreate">
            <span v-if="creating" class="spinner" />
            <span v-else>创建</span>
          </button>
        </template>
        <button v-else class="btn btn-primary" type="button" @click="showCreate = false">完成</button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.key-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  background: var(--color-primary-soft);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius);
  word-break: break-all;
}
</style>
