package com.secops.controller.dto;

import com.secops.model.Alert;
import com.secops.model.enums.*;
import java.time.Instant;
import java.util.Map;

/**
 * 原始告警 API 投影（API.md 6/7 节）。
 * 列表不返回 rawLog；strategySupport 由策略注册表动态计算；scenarioId 不向前端暴露。
 */
public final class AlertDtos {
    private AlertDtos() {}

    public record Query(int page,int pageSize,String keyword,AlertType alertType,AlertCategory category,
                        Severity severity,String sourceIp,String targetAsset) {
        public Query { if(page<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("Invalid pagination"); }
    }

    public record Item(String alertId,String alertType,String category,Instant timestamp,String sourceIp,String targetIp,
                       String targetAsset,String severity,String sourceSystem,Map<String,Object> attributes,
                       StrategySupport strategySupport) {
        public static Item from(Alert a,StrategySupport support) {
            return new Item(a.alertId(),a.alertType(),a.category(),a.timestamp(),a.sourceIp(),a.targetIp(),
                    a.targetAsset(),a.severity(),a.sourceSystem(),a.attributes(),support);
        }
    }

    /** 告警完整视图（含不可信 rawLog），但不暴露内部评估字段 scenarioId（API.md 6 节）。 */
    public record AlertView(String alertId,String alertType,String category,Instant timestamp,String sourceIp,
                            String targetIp,String targetAsset,String severity,String sourceSystem,
                            Map<String,Object> attributes,String rawLog) {
        public static AlertView from(Alert a) {
            return new AlertView(a.alertId(),a.alertType(),a.category(),a.timestamp(),a.sourceIp(),a.targetIp(),
                    a.targetAsset(),a.severity(),a.sourceSystem(),a.attributes(),a.rawLog());
        }
    }

    /** 详情返回完整告警（含不可信 rawLog）与策略支持状态。 */
    public record Detail(AlertView alert,StrategySupport strategySupport) {}
}
