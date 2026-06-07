import { get } from './http'

/** 平台可用模型(由各渠道的 models 字段聚合得到,只读) */
export function listModels(group?: string) {
  return get<string[]>('/api/models', group ? { group } : undefined)
}
