package com.secops.engine;

import com.secops.model.*;
import com.secops.model.RiskAssessment.MatchedRule;
import com.secops.model.enums.RiskLevel;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class RiskScoringEngine {
    public RiskAssessment assess(SecurityFeatures f) {
        List<MatchedRule> rules=new ArrayList<>();
        int failures=f.failedAttempts();
        if (failures>=100) rules.add(new MatchedRule("FAILED_ATTEMPTS_GE_100","登录失败至少100次",45,Map.of("failedAttempts",failures)));
        else if (failures>=30) rules.add(new MatchedRule("FAILED_ATTEMPTS_30_99","登录失败30–99次",30,Map.of("failedAttempts",failures)));
        else if (failures>=10) rules.add(new MatchedRule("FAILED_ATTEMPTS_10_29","登录失败10–29次",15,Map.of("failedAttempts",failures)));
        if (f.successAfterFailure()) rules.add(new MatchedRule("SUCCESS_AFTER_FAILURE","失败后出现成功登录",30,Map.of("successAfterFailure",true,"successfulAttempts",f.successfulAttempts())));
        if (f.distinctUserCount()>=5) rules.add(new MatchedRule("DISTINCT_USERS_GE_5","不同用户名数量至少5个",10,Map.of("distinctUserCount",f.distinctUserCount())));
        if (Boolean.TRUE.equals(f.publicExposure())) rules.add(new MatchedRule("PUBLIC_EXPOSURE","目标资产公网暴露",10,Map.of("publicExposure",true)));
        int score=capScore(rules.stream().mapToInt(MatchedRule::points).sum());
        return new RiskAssessment(score,levelFor(score),rules,rules.stream().map(MatchedRule::description).toList(),Instant.now());
    }
    static int capScore(int score) { return Math.min(100,score); }
    public static RiskLevel levelFor(int score) {
        if (score<0 || score>100) throw new IllegalArgumentException("Score outside 0..100");
        return score>=80?RiskLevel.CRITICAL:score>=50?RiskLevel.HIGH:score>=30?RiskLevel.MEDIUM:RiskLevel.LOW;
    }
}
