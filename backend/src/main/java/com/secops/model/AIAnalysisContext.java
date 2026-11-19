package com.secops.model;

import java.time.Instant;
import java.util.*;

/** Allowlist projection: no evaluation fields or arbitrary attributes are serialized. */
public record AIAnalysisContext(String incidentId,String alertType,String sourceIp,String targetAsset,
        Instant firstSeen,Instant lastSeen,int alertCount,SecurityFeatures features,RiskAssessment riskAssessment,
        List<String> usernames,List<ContextAlert> alerts,List<String> representativeLogs) {
    public record ContextAlert(String alertId,Instant timestamp,String protocol,String username,String action) {}
    public static AIAnalysisContext from(Incident i,List<Alert> alerts) {
        return new AIAnalysisContext(i.incidentId(),i.alertType(),i.sourceIp(),i.targetAsset(),i.firstSeen(),i.lastSeen(),
            i.alertCount(),i.features(),i.riskAssessment(),alerts.stream().map(a -> field(a,"username"))
                .filter(Objects::nonNull).distinct().toList(),alerts.stream().map(a -> new ContextAlert(a.alertId(),a.timestamp(),
                field(a,"protocol"),field(a,"username"),field(a,"action"))).toList(),
            alerts.stream().map(Alert::rawLog).filter(Objects::nonNull).limit(3).map(s -> s.substring(0,Math.min(500,s.length()))).toList());
    }
    private static String field(Alert a,String key) { Object v=a.attributes().get(key); return v==null?null:v.toString().substring(0,Math.min(128,v.toString().length())); }
}
