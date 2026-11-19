package com.secops.model;
import java.time.Instant;
public record ReviewRecord(String reviewId,String incidentId,String analysisId,String aiVerdict,
    String reviewResult,String reviewer,String remark,Instant reviewTime) {}
