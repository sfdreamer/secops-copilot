package com.secops.strategy;

import com.secops.model.*;
import java.util.List;

/** Typed deterministic analysis and context construction; never invokes an LLM. */
public interface AlertAnalysisStrategy {
    AIAnalysisContext buildContext(Incident incident, List<Alert> alerts);
    String getPromptTemplate();
    String getAlertType();
    List<Incident> aggregate(List<Alert> alerts);
    SecurityFeatures extractFeatures(Incident incident, List<Alert> alerts);
    RiskAssessment assessRisk(SecurityFeatures features);
}
