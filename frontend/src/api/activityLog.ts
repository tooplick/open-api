import { get } from './http'
import type { ActivityLogItem, PageResult } from '@/types'

export interface ActivityLogPageParams {
  current: number
  size: number
  userId?: number
  action?: string
  resourceType?: string
  startTime?: string
  endTime?: string
}

export function pageActivityLogs(params: ActivityLogPageParams) {
  return get<PageResult<ActivityLogItem>>('/api/activity-logs/page', params)
}
