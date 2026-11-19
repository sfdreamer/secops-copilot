/**
 * 前端展示用枚举标签，字段值以 DATABASE.md 4 节为准。
 * 这里只保存「可展示的取值」，不复制后端聚合、评分或状态流转规则。
 */
export const ALERT_TYPES = [
  'SSH_BRUTE_FORCE',
  'PORT_SCAN',
  'SQL_INJECTION',
  'MALWARE_ACTIVITY',
  'UNKNOWN',
] as const
export type AlertType = (typeof ALERT_TYPES)[number]

export const ALERT_CATEGORIES = [
  'AUTHENTICATION',
  'NETWORK',
  'WEB_ATTACK',
  'HOST_SECURITY',
  'DATA_SECURITY',
  'UNKNOWN',
] as const
export type AlertCategory = (typeof ALERT_CATEGORIES)[number]

export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const
export type Severity = (typeof SEVERITIES)[number]

export const RISK_LEVELS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const
export type RiskLevel = (typeof RISK_LEVELS)[number]

export const INCIDENT_STATUSES = [
  'PENDING_ANALYSIS',
  'ANALYZING',
  'PENDING_REVIEW',
  'ANALYSIS_FAILED',
  'CONFIRMED_THREAT',
  'FALSE_POSITIVE',
  'INVESTIGATING',
] as const
export type IncidentStatus = (typeof INCIDENT_STATUSES)[number]

export const ANALYSIS_STATUSES = ['NOT_STARTED', 'RUNNING', 'SUCCEEDED', 'FAILED'] as const
export type AnalysisStatus = (typeof ANALYSIS_STATUSES)[number]

export const STRATEGY_SUPPORTS = ['SUPPORTED', 'UNSUPPORTED'] as const
export type StrategySupport = (typeof STRATEGY_SUPPORTS)[number]
