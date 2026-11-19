import type { AlertType, AlertCategory, RiskLevel, AnalysisStatus, IncidentStatus } from './enums'

export interface SecurityFeatures {
  failedAttempts: number
  successfulAttempts: number
  distinctUserCount: number
  windowSeconds: number
  windowMinutes: number
  failureRate: number
  successAfterFailure: boolean
  publicExposure: boolean | null
  assetCriticality: 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN'
}

export interface RiskAssessment {
  score: number
  level: RiskLevel
  matchedRules: { ruleCode: string; description: string; points: number; supportingFacts: Record<string, unknown> }[]
  reasons: string[]
}

export interface IncidentItem {
  incidentId: string
  alertType: AlertType
  category: AlertCategory
  sourceIp: string | null
  targetAsset: string | null
  firstSeen: string
  lastSeen: string
  alertCount: number
  ruleRiskScore: number
  ruleRiskLevel: RiskLevel
  analysisStatus: AnalysisStatus
  status: IncidentStatus
}

export interface IncidentDetail {
  incident: Omit<IncidentItem, 'ruleRiskScore' | 'ruleRiskLevel'> & { targetIp: string | null; alertIds: string[] }
  features: SecurityFeatures
  riskAssessment: RiskAssessment
  relatedAlerts: { alertId: string; timestamp: string; attributes: Record<string, unknown> | null }[]
  latestAnalysis: AIAnalysis | null
  toolCallTraces: ToolCallTrace[]
}

export interface Evidence {
  sourceType: 'ALERT' | 'TOOL'
  alertIds: string[]
  toolCallIds: string[]
  fact: string
  explanation: string
}
export interface AIAnalysis {
  analysisId: string
  incidentId: string
  analysisStatus: 'SUCCEEDED' | 'FAILED'
  verdict: 'SUSPICIOUS' | 'LIKELY_BENIGN' | 'NEEDS_REVIEW' | null
  summary: string
  attackType: 'SSH_BRUTE_FORCE' | 'UNKNOWN' | null
  riskLevel: RiskLevel | null
  reasoningSummary: string
  evidence: Evidence[]
  recommendations: string[]
  uncertainties: string[]
  modelName: string
  promptVersion: string
  executionMode: 'TOOL_ENABLED' | 'NO_TOOL_FALLBACK'
  durationMs: number
  toolCallCount: number
  validationStatus: 'VALID' | 'PARTIALLY_VALID' | 'INVALID'
  failureReason: string | null
  analyzedAt: string
}
export interface ToolCallTrace {
  toolCallId: string
  analysisId: string
  incidentId: string
  toolName: string
  argumentsSummary: Record<string, unknown>
  executionStatus: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'REJECTED'
  resultSummary: unknown
  errorCode: string | null
  durationMs: number
  calledAt: string
}
export interface ReviewRecord {
  reviewId: string
  incidentId: string
  analysisId: string | null
  aiVerdict: AIAnalysis['verdict']
  reviewResult: 'CONFIRMED_THREAT' | 'FALSE_POSITIVE' | 'INVESTIGATING'
  reviewer: string
  remark: string | null
  reviewTime: string
}
export interface AnalysisResponse {
  incidentId: string
  analysisStatus: 'SUCCEEDED'
  status: 'PENDING_REVIEW'
  analysis: AIAnalysis
  toolCallTraces: ToolCallTrace[]
  agentMetrics: { toolCallCount: number; agentDurationMs: number }
}
export type ReviewResponse = ReviewRecord & { incidentStatus: ReviewRecord['reviewResult'] }
