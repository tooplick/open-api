import { get } from './http'
import type { LogItem, LogStat, PageResult } from '@/types'

export interface LogPageParams {
  current: number
  size: number
  userId?: number
  channelId?: number
  modelName?: string
  type?: number
  startTime?: string
  endTime?: string
}

export function pageLogs(params: LogPageParams) {
  return get<PageResult<LogItem>>('/api/logs/page', params)
}

export function logStatistics(params: { userId?: number; startTime?: string; endTime?: string }) {
  return get<LogStat>('/api/logs/statistics', params)
}
