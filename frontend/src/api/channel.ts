import { get, post, put, del } from './http'
import type { Channel, ChannelRequest, FetchModelsRequest, PageResult } from '@/types'

export function pageChannels(params: { current: number; size: number; name?: string }) {
  return get<PageResult<Channel>>('/api/channels/page', params)
}

export function getChannel(id: number) {
  return get<Channel>(`/api/channels/${id}`)
}

/** 用 baseUrl + 密钥拉取上游可用模型(留空密钥且带 id 时复用库中原密钥) */
export function fetchChannelModels(body: FetchModelsRequest) {
  return post<string[]>('/api/channels/fetch-models', body)
}

export function createChannel(body: ChannelRequest) {
  return post<Channel>('/api/channels', body)
}

export function updateChannel(id: number, body: ChannelRequest) {
  return put<void>(`/api/channels/${id}`, body)
}

export function updateChannelStatus(id: number, status: number) {
  return put<void>(`/api/channels/${id}/status`, undefined, { status })
}

export function deleteChannel(id: number) {
  return del<void>(`/api/channels/${id}`)
}
