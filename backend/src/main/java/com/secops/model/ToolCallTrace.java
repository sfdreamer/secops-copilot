package com.secops.model;
import java.time.Instant;
import java.util.Map;
public record ToolCallTrace(String toolCallId,String analysisId,String incidentId,String toolName,
    Map<String,Object> argumentsSummary,String executionStatus,Object resultSummary,String errorCode,long durationMs,Instant calledAt) {}
