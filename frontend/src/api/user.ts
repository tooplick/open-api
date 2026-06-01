import { get, put } from './http'
import type { ChangePasswordRequest, PageResult, User } from '@/types'

export function getMe() {
  return get<User>('/api/user/me')
}

export function changePassword(body: ChangePasswordRequest) {
  return put<void>('/api/user/password', body)
}

export function pageUsers(params: { current: number; size: number; username?: string }) {
  return get<PageResult<User>>('/api/user/page', params)
}

export function updateUserStatus(id: number, status: number) {
  return put<void>(`/api/user/${id}/status`, undefined, { status })
}

export function updateUserQuota(id: number, quota: number) {
  return put<void>(`/api/user/${id}/quota`, undefined, { quota })
}
