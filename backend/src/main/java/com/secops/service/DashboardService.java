package com.secops.service;

import com.secops.controller.dto.IncidentDtos.Item;
import com.secops.model.Incident;
import com.secops.model.enums.*;
import com.secops.repository.AlertRepository;
import com.secops.repository.IncidentRepository;
import com.secops.strategy.AlertStrategyRegistry;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Dashboard 统计全部由当前内存数据实时计算（API.md 5 节），不使用硬编码数字。 */
@Service
public class DashboardService {

    public record Dashboard(long alertTotal,Map<String,Long> categoryDistribution,Map<String,Long> alertTypeDistribution,
            long supportedIncidentTotal,long highRiskIncidentTotal,long pendingReviewTotal,long unsupportedAlertTotal,
            Map<String,Long> riskDistribution,List<Item> recentIncidents) {}

    private final AlertRepository alerts;
    private final IncidentRepository incidents;
    private final AlertStrategyRegistry strategies;

    public DashboardService(AlertRepository alerts,IncidentRepository incidents,AlertStrategyRegistry strategies) {
        this.alerts=alerts; this.incidents=incidents; this.strategies=strategies;
    }

    public Dashboard overview() {
        var allAlerts=alerts.findAll();
        var allIncidents=incidents.findAll();

        Map<String,Long> byCategory=allAlerts.stream()
                .collect(Collectors.groupingBy(a -> a.category(),Collectors.counting()));
        Map<String,Long> byType=allAlerts.stream()
                .collect(Collectors.groupingBy(a -> a.alertType(),Collectors.counting()));

        Map<String,Long> risk=new LinkedHashMap<>();
        for (RiskLevel level:RiskLevel.values()) risk.put(level.name(),0L);
        allIncidents.forEach(i -> risk.merge(i.riskAssessment().level().name(),1L,Long::sum));

        long highRisk=allIncidents.stream()
                .filter(i -> i.riskAssessment().level()==RiskLevel.HIGH || i.riskAssessment().level()==RiskLevel.CRITICAL).count();
        long pendingReview=allIncidents.stream().filter(i -> i.status()==IncidentStatus.PENDING_REVIEW).count();
        long unsupported=allAlerts.stream().filter(a -> !strategies.isSupported(a.alertType())).count();

        List<Item> recent=allIncidents.stream()
                .sorted(Comparator.comparing(Incident::firstSeen).reversed().thenComparing(Incident::incidentId))
                .limit(8).map(Item::from).toList();

        return new Dashboard(allAlerts.size(),byCategory,byType,allIncidents.size(),highRisk,pendingReview,unsupported,risk,recent);
    }
}
