package com.secops.model;

import java.util.List;

/** 离线评估标签（DATABASE.md 7 节）。仅评估模块可访问，不得进入 Agent 上下文或业务 API。 */
public record GroundTruth(String scenarioId,String expectedLabel,List<String> expectedToolPlan,String notes) {
    public GroundTruth {
        expectedToolPlan=expectedToolPlan==null?List.of():List.copyOf(expectedToolPlan);
    }
}
