package com.secops.controller.dto;

import com.secops.model.*;
import com.secops.model.enums.*;
import java.time.Instant;
import java.util.*;

/** Explicit API projections prevent internal evaluation fields from leaking. */
public final class IncidentDtos {
    private IncidentDtos() {}
    public record Page<T>(long total,int page,int pageSize,List<T> items) {}
    public record Query(int page,int pageSize,String keyword,AlertType alertType,RiskLevel ruleRiskLevel,
                        IncidentStatus status,String sourceIp,String targetAsset) {
        public Query { if(page<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("Invalid pagination"); }
    }
    public record Item(String incidentId,String alertType,String category,String sourceIp,String targetAsset,
                       Instant firstSeen,Instant lastSeen,int alertCount,int ruleRiskScore,RiskLevel ruleRiskLevel,
                       AnalysisStatus analysisStatus,IncidentStatus status) {
        public static Item from(Incident i) { return new Item(i.incidentId(),i.alertType(),i.category(),i.sourceIp(),i.targetAsset(),
                i.firstSeen(),i.lastSeen(),i.alertCount(),i.riskAssessment().score(),i.riskAssessment().level(),i.analysisStatus(),i.status()); }
    }
    public record Summary(String incidentId,String alertType,String category,String sourceIp,String targetIp,String targetAsset,
                          Instant firstSeen,Instant lastSeen,List<String> alertIds,int alertCount,AnalysisStatus analysisStatus,IncidentStatus status) {
        public static Summary from(Incident i) { return new Summary(i.incidentId(),i.alertType(),i.category(),i.sourceIp(),i.targetIp(),i.targetAsset(),
                i.firstSeen(),i.lastSeen(),i.alertIds(),i.alertCount(),i.analysisStatus(),i.status()); }
    }
    public record RelatedAlert(String alertId,Instant timestamp,Map<String,Object> attributes) {
        public static RelatedAlert from(Alert a) { return new RelatedAlert(a.alertId(),a.timestamp(),a.attributes()); }
    }
    public record Risk(int score,RiskLevel level,List<RiskAssessment.MatchedRule> matchedRules,List<String> reasons) {
        public static Risk from(RiskAssessment r) { return new Risk(r.score(),r.level(),r.matchedRules(),r.reasons()); }
    }
    // No analysis returns null/empty; failed attempts remain visible.
    public record Detail(Summary incident,SecurityFeatures features,Risk riskAssessment,List<RelatedAlert> relatedAlerts,
                         AIAnalysis latestAnalysis,List<ToolCallTrace> toolCallTraces) {}
}
