import { request } from './http'
import type { PageResult } from '@/types/api'
import type { ReviewRecord } from '@/types/incidents'

export interface ReviewQuery {
  page: number
  pageSize: number
  incidentId?: string
  reviewResult?: string
}

export function fetchReviews(query: ReviewQuery): Promise<PageResult<ReviewRecord>> {
  return request<PageResult<ReviewRecord>>({ url: '/reviews', method: 'GET', params: query })
}
