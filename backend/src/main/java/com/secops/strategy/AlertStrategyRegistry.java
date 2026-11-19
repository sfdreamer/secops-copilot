package com.secops.strategy;

import com.secops.common.BizException;
import com.secops.common.ErrorCode;
import com.secops.model.enums.AlertType;
import com.secops.model.enums.StrategySupport;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 策略注册表：本期只注册 SSH_BRUTE_FORCE（AGENTS.md P0 / PRD 2.2）。
 * 未注册策略的告警只能展示，不得生成 Incident 或 AI 报告。
 *
 * 按 alertType 路由到注入的真实策略对象，并拒绝重复注册。
 */
@Component
public class AlertStrategyRegistry {

    private final Map<String, AlertAnalysisStrategy> strategies;

    public AlertStrategyRegistry(List<AlertAnalysisStrategy> implementations) {
        Map<String,AlertAnalysisStrategy> registered=new HashMap<>();
        for (AlertAnalysisStrategy strategy:implementations) {
            if (!AlertType.SSH_BRUTE_FORCE.name().equals(strategy.getAlertType()))
                throw new IllegalArgumentException("Only SSH_BRUTE_FORCE is enabled in this release");
            if (registered.putIfAbsent(strategy.getAlertType(),strategy)!=null)
                throw new IllegalArgumentException("Duplicate strategy registration");
        }
        strategies=Map.copyOf(registered);
    }

    public AlertAnalysisStrategy getStrategy(String alertType) {
        requireSupported(alertType);
        return strategies.get(alertType);
    }

    public boolean isSupported(String alertType) {
        return alertType != null && strategies.containsKey(alertType);
    }

    public StrategySupport getStrategySupport(String alertType) {
        return isSupported(alertType) ? StrategySupport.SUPPORTED : StrategySupport.UNSUPPORTED;
    }

    public Set<String> supportedTypes() {
        return strategies.keySet();
    }

    public void requireSupported(String alertType) {
        if (!isSupported(alertType)) {
            throw new BizException(ErrorCode.STRATEGY_NOT_SUPPORTED, "当前告警类型暂未启用分析策略");
        }
    }
}
