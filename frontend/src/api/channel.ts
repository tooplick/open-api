import { get, post, put, del } from './http'
import type { Channel, ChannelRequest, PageResult } from '@/types'

export function pageChannels(params: { current: number; size: number; name?: string }) {
  return get<PageResult<Channel>>('/api/channels/page', params)
}

export function getChannel(id: number) {
  return get<Channel>(`/api/channels/${id}`)
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
