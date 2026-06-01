import { get, post, put, del } from './http'
import type { Model, ModelRequest, PageResult } from '@/types'

export function pageModels(params: { current: number; size: number; modelName?: string }) {
  return get<PageResult<Model>>('/api/models/page', params)
}

export function listEnabledModels() {
  return get<Model[]>('/api/models/enabled')
}

export function createModel(body: ModelRequest) {
  return post<Model>('/api/models', body)
}

export function updateModel(id: number, body: ModelRequest) {
  return put<void>(`/api/models/${id}`, body)
}

export function deleteModel(id: number) {
  return del<void>(`/api/models/${id}`)
}
