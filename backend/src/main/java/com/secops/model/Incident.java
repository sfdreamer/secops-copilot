package com.secops.model;

import com.secops.model.enums.*;
import java.time.Instant;
import java.util.*;

public record Incident(String incidentId, String alertType, String category, String sourceIp,
        String targetIp, String targetAsset, Instant firstSeen, Instant lastSeen, List<String> alertIds,
        int alertCount, SecurityFeatures features, RiskAssessment riskAssessment,
        AnalysisStatus analysisStatus, IncidentStatus status, Instant createdAt, Instant updatedAt) {
    public Incident withStatus(AnalysisStatus analysis,IncidentStatus state) {
        return new Incident(incidentId,alertType,category,sourceIp,targetIp,targetAsset,firstSeen,lastSeen,alertIds,alertCount,features,riskAssessment,analysis,state,createdAt,Instant.now());
    }
    public Incident {
        alertIds = List.copyOf(alertIds);
        if (alertIds.isEmpty() || alertCount != alertIds.size() || new HashSet<>(alertIds).size() != alertCount
                || firstSeen.isAfter(lastSeen)) throw new IllegalArgumentException("Invalid incident membership/time range");
        Objects.requireNonNull(features); Objects.requireNonNull(riskAssessment);
    }
}
