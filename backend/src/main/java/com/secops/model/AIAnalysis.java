package com.secops.model;
import java.time.Instant;
import java.util.List;
public record AIAnalysis(String analysisId,String incidentId,String analysisStatus,String verdict,String summary,
    String attackType,String riskLevel,String reasoningSummary,List<Evidence> evidence,List<String> recommendations,
    List<String> uncertainties,String modelName,String promptVersion,String executionMode,long durationMs,int toolCallCount,
    String validationStatus,String failureReason,Instant analyzedAt) {
    public record Evidence(String sourceType,List<String> alertIds,List<String> toolCallIds,String fact,String explanation) {}
}
