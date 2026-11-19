import { request } from './http'
import type { PageResult } from '@/types/api'
import type { AnalysisResponse, IncidentDetail, IncidentItem, ReviewResponse } from '@/types/incidents'

export interface IncidentQuery {
  page: number
  pageSize: number
  keyword?: string
  alertType?: string
  ruleRiskLevel?: string
  status?: string
  sourceIp?: string
  targetAsset?: string
}

export function fetchIncidents(query: IncidentQuery): Promise<PageResult<IncidentItem>> {
  return request<PageResult<IncidentItem>>({ url: '/incidents', method: 'GET', params: query })
}

export function fetchIncidentDetail(id: string): Promise<IncidentDetail> {
  return request<IncidentDetail>({ url: `/incidents/${encodeURIComponent(id)}`, method: 'GET' })
}

/** 触发 Agent 研判；后端为同步接口且 Agent 总耗时上限 45s，前端单独放宽到 60s。 */
export function analyzeIncident(id: string): Promise<AnalysisResponse> {
  return request<AnalysisResponse>({
    url: `/incidents/${encodeURIComponent(id)}/analyze`,
    method: 'POST',
    timeout: 60000,
  })
}

export interface ReviewRequest {
  reviewResult: 'CONFIRMED_THREAT' | 'FALSE_POSITIVE' | 'INVESTIGATING'
  reviewer: string
  remark?: string
}

export function reviewIncident(id: string, body: ReviewRequest): Promise<ReviewResponse> {
  return request<ReviewResponse>({
    url: `/incidents/${encodeURIComponent(id)}/review`,
    method: 'POST',
    data: body,
  })
}
