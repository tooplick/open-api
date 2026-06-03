import { get, put } from './http'
import type { PublicSettings, Settings } from '@/types'

/** 管理员:读取全部系统设置 */
export function getSettings() {
  return get<Settings>('/api/settings')
}

/** 管理员:全量更新系统设置 */
export function updateSettings(body: Settings) {
  return put<void>('/api/settings', body)
}

/** 公开:登录页所需设置子集(无需鉴权) */
export function getPublicSettings() {
  return get<PublicSettings>('/api/settings/public')
}
