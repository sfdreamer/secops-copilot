package com.secops.strategy;

import com.secops.engine.*;
import com.secops.model.*;
import com.secops.model.enums.*;
import com.secops.repository.AssetRepository;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class SshBruteForceStrategy implements AlertAnalysisStrategy {
    private final SshAlertAggregator aggregator;
    private final SecurityFeatureExtractor extractor;
    private final RiskScoringEngine scoring;
    private final AssetRepository assets;
    public SshBruteForceStrategy(SshAlertAggregator aggregator, SecurityFeatureExtractor extractor, RiskScoringEngine scoring, AssetRepository assets) {
        this.aggregator=aggregator; this.extractor=extractor; this.scoring=scoring; this.assets=assets;
    }
    public AIAnalysisContext buildContext(Incident i,List<Alert> alerts) { return AIAnalysisContext.from(i,alerts); }
    public String getPromptTemplate() { return com.secops.ai.TriagePrompt.SYSTEM; }
    public String getAlertType() { return AlertType.SSH_BRUTE_FORCE.name(); }
    public List<Incident> aggregate(List<Alert> alerts) {
        return aggregator.aggregate(alerts).stream().map(group -> {
            Alert first=group.get(0), last=group.get(group.size()-1);
            var features=extractor.extract(group,assets.findByName(first.targetAsset()).orElse(null));
            var risk=assessRisk(features);
            var missing=SshAlertAggregator.missingFields(first);
            if (!missing.isEmpty()) {
                List<String> reasons=new ArrayList<>(risk.reasons());
                reasons.add("数据不完整：缺少 " + String.join(", ",missing) + "；单独成事件，不增加风险分值");
                risk=new RiskAssessment(risk.score(),risk.level(),risk.matchedRules(),reasons,risk.assessedAt());
            }
            Instant now=Instant.now();
            // Globally unique first Alert ID yields stable Incident ID, independent of scenarioId and wall clock.
            String id="INC-"+UUID.nameUUIDFromBytes(first.alertId().getBytes(StandardCharsets.UTF_8));
            return new Incident(id,getAlertType(),first.category(),first.sourceIp(),first.targetIp(),first.targetAsset(),
                    first.timestamp(),last.timestamp(),group.stream().map(Alert::alertId).toList(),group.size(),
                    features,risk,AnalysisStatus.NOT_STARTED,IncidentStatus.PENDING_ANALYSIS,now,now);
        }).toList();
    }
    public SecurityFeatures extractFeatures(Incident incident,List<Alert> alerts) {
        Set<String> ids=new HashSet<>();
        for (Alert a:alerts) if (!ids.add(a.alertId()) || !getAlertType().equals(a.alertType())) throw new IllegalArgumentException("Invalid feature input");
        if (!ids.equals(new HashSet<>(incident.alertIds()))) throw new IllegalArgumentException("Alerts must match incident");
        return extractor.extract(alerts,assets.findByName(incident.targetAsset()).orElse(null));
    }
    public RiskAssessment assessRisk(SecurityFeatures features) { return scoring.assess(features); }
}
