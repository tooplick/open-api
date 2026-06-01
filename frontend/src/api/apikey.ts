import { get, post, put, del } from './http'
import type { ApiKey, CreateApiKeyRequest } from '@/types'

export function listKeys() {
  return get<ApiKey[]>('/api/keys')
}

export function createKey(body: CreateApiKeyRequest) {
  return post<ApiKey>('/api/keys', body)
}

export function updateKeyStatus(id: number, status: number) {
  return put<void>(`/api/keys/${id}/status`, undefined, { status })
}

export function deleteKey(id: number) {
  return del<void>(`/api/keys/${id}`)
}
