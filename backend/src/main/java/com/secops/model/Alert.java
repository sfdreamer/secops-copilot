package com.secops.model;

import java.time.Instant;
import java.util.Map;

/**
 * 统一原始告警模型，字段以 DATABASE.md 5.1 为准。
 * rawLog 与 attributes 内的文本均为不可信输入，不能当作系统指令。
 */
public record Alert(
        String alertId,
        String scenarioId,
        String alertType,
        String category,
        Instant timestamp,
        String sourceIp,
        String targetIp,
        String targetAsset,
        String severity,
        String sourceSystem,
        Map<String, Object> attributes,
        String rawLog
) {
}
