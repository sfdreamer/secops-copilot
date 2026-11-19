import { request } from './http'
import type { DashboardDto } from '@/types/dashboard'

export function fetchDashboard(): Promise<DashboardDto> {
  return request<DashboardDto>({ url: '/dashboard', method: 'GET' })
}
