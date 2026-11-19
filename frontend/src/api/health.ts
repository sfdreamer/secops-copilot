import { request } from './http'
import type { HealthDto } from '@/types/api'

export function fetchHealth(): Promise<HealthDto> {
  return request<HealthDto>({ url: '/health', method: 'GET' })
}
