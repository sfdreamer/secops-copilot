package com.secops.model;

import com.secops.model.enums.RiskLevel;
import java.time.Instant;
import java.util.*;

public record RiskAssessment(int score, RiskLevel level, List<MatchedRule> matchedRules,
        List<String> reasons, Instant assessedAt) {
    public RiskAssessment { matchedRules = List.copyOf(matchedRules); reasons = List.copyOf(reasons); }
    public record MatchedRule(String ruleCode, String description, int points, Map<String,Object> supportingFacts) {
        public MatchedRule { supportingFacts = Map.copyOf(supportingFacts); }
    }
}
