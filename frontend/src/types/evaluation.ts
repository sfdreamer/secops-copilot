/** GET /api/evaluation 响应结构，对应 API.md 13 节；无有效样本的指标为 null。 */
export interface EvaluationDto {
  meta: {
    datasetVersion: string
    sampleCount: number
    evaluatedAt: string
    modelName: string
    promptVersion: string
  }
  dataQuality: {
    inputAlertCount: number
    parsedAlertCount: number
    parseSuccessRate: number | null
    requiredFieldCompleteness: number | null
  }
  aggregation: {
    rawSshAlertCount: number
    incidentCount: number
    compressionRate: number | null
    precision: number | null
    recall: number | null
  }
  ruleEvaluation: {
    sampleCount: number
    uncertainSampleCount: number
    precision: number | null
    recall: number | null
    f1: number | null
  }
  aiEvaluation: {
    sampleCount: number
    accuracy: number | null
    precision: number | null
    recall: number | null
    macroF1: number | null
    confusionMatrix: Record<string, Record<string, number>>
  }
  evidenceEvaluation: {
    traceabilityRate: number | null
    traceabilityStatus: string
    semanticSupportRate: number | null
    semanticSupportStatus: string
  }
  agentEvaluation: {
    toolCallCount: number
    toolSuccessRate: number | null
    parameterValidityRate: number | null
    toolPlanPrecision: number | null
    toolPlanRecall: number | null
    toolPlanF1: number | null
    averageToolCalls: number | null
    averageDurationMs: number | null
  }
  businessEvaluation: {
    reviewSampleCount: number
    adoptionRate: number | null
    correctionRate: number | null
    manualAverageDurationMs: number | null
    aiAssistedAverageDurationMs: number | null
    efficiencyImprovementRate: number | null
    measurementStatus: string
  }
}
