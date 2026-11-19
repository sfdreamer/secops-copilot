import { request } from './http'
import type { PageResult } from '@/types/api'
import type { AlertDetail, AlertItem } from '@/types/alerts'

export interface AlertQuery {
  page: number
  pageSize: number
  keyword?: string
  alertType?: string
  category?: string
  severity?: string
  sourceIp?: string
  targetAsset?: string
}

export function fetchAlerts(query: AlertQuery): Promise<PageResult<AlertItem>> {
  return request<PageResult<AlertItem>>({ url: '/alerts', method: 'GET', params: query })
}

export function fetchAlertDetail(id: string): Promise<AlertDetail> {
  return request<AlertDetail>({ url: `/alerts/${encodeURIComponent(id)}`, method: 'GET' })
}
