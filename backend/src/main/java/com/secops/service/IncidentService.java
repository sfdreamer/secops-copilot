package com.secops.service;

import com.secops.common.*;
import com.secops.controller.dto.IncidentDtos.*;
import com.secops.model.*;
import com.secops.repository.*;
import com.secops.strategy.AlertStrategyRegistry;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
@DependsOn("alertFixtureLoader")
public class IncidentService {
    private final AlertRepository alerts;
    private final IncidentRepository incidents;
    private final AlertStrategyRegistry strategies;
    @org.springframework.beans.factory.annotation.Autowired private AIAnalysisRepository analyses;
    @org.springframework.beans.factory.annotation.Autowired private ToolCallTraceRepository traces;
    public IncidentService(AlertRepository alerts,IncidentRepository incidents,AlertStrategyRegistry strategies) {
        this.alerts=alerts; this.incidents=incidents; this.strategies=strategies;
    }
    @PostConstruct
    public synchronized void generate() {
        Set<String> assigned=new HashSet<>();
        incidents.findAll().forEach(i -> assigned.addAll(i.alertIds()));
        Map<String,List<Alert>> routed=new TreeMap<>();
        alerts.findAll().stream().filter(a -> !assigned.contains(a.alertId()) && strategies.isSupported(a.alertType()))
                .forEach(a -> routed.computeIfAbsent(a.alertType(),key -> new ArrayList<>()).add(a));
        routed.forEach((type,batch) -> strategies.getStrategy(type).aggregate(batch).forEach(incidents::save));
    }
    public Page<Item> list(Query q) {
        List<Incident> matches=incidents.findAll().stream()
                .filter(i -> q.alertType()==null || q.alertType().name().equals(i.alertType()))
                .filter(i -> q.ruleRiskLevel()==null || q.ruleRiskLevel()==i.riskAssessment().level())
                .filter(i -> q.status()==null || q.status()==i.status())
                .filter(i -> q.sourceIp()==null || q.sourceIp().equals(i.sourceIp()))
                .filter(i -> q.targetAsset()==null || q.targetAsset().equals(i.targetAsset()))
                .filter(i -> q.keyword()==null || contains(i.incidentId(),q.keyword()) || contains(i.sourceIp(),q.keyword()) || contains(i.targetAsset(),q.keyword()))
                .sorted(Comparator.comparing(Incident::firstSeen).reversed().thenComparing(Incident::incidentId)).toList();
        long offset=((long)q.page()-1)*q.pageSize();
        return new Page<>(matches.size(),q.page(),q.pageSize(),matches.stream().skip(offset).limit(q.pageSize()).map(Item::from).toList());
    }
    private static boolean contains(String value,String keyword) { return value!=null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)); }
    public Detail detail(String id) {
        Incident i=incidents.findById(id).orElseThrow(() -> new BizException(ErrorCode.INCIDENT_NOT_FOUND,"事件不存在"));
        var history=analyses==null?List.<AIAnalysis>of():analyses.findByIncident(id);
        AIAnalysis latest=history.isEmpty()?null:history.get(history.size()-1);
        var latestTraces=latest==null?List.<ToolCallTrace>of():traces.findByIncident(id).stream().filter(t -> t.analysisId().equals(latest.analysisId())).toList();
        return new Detail(Summary.from(i),i.features(),Risk.from(i.riskAssessment()),
                alerts.findByIds(i.alertIds()).stream().map(RelatedAlert::from).toList(),latest,latestTraces);
    }
}
