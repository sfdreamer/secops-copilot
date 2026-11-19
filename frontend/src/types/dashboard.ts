import type { IncidentItem } from './incidents'

/** GET /api/dashboard 响应，对应 API.md 5 节。 */
export interface DashboardDto {
  alertTotal: number
  categoryDistribution: Record<string, number>
  alertTypeDistribution: Record<string, number>
  supportedIncidentTotal: number
  highRiskIncidentTotal: number
  pendingReviewTotal: number
  unsupportedAlertTotal: number
  riskDistribution: Record<string, number>
  recentIncidents: IncidentItem[]
}
