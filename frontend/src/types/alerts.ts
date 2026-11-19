import type { AlertType, AlertCategory, Severity, StrategySupport } from './enums'

/** 原始告警列表项（API.md 6 节，不含 rawLog）。 */
export interface AlertItem {
  alertId: string
  alertType: AlertType
  category: AlertCategory
  timestamp: string
  sourceIp: string | null
  targetIp: string | null
  targetAsset: string | null
  severity: Severity
  sourceSystem: string | null
  attributes: Record<string, unknown> | null
  strategySupport: StrategySupport
}

/** 原始告警（详情，含不可信 rawLog）。 */
export interface RawAlert extends Omit<AlertItem, 'strategySupport'> {
  rawLog: string | null
}

/** GET /api/alerts/{id} 响应（API.md 7 节）。 */
export interface AlertDetail {
  alert: RawAlert
  strategySupport: StrategySupport
}
