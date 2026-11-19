package com.secops.service;

import com.secops.common.*;
import com.secops.controller.dto.AlertDtos.*;
import com.secops.controller.dto.IncidentDtos.Page;
import com.secops.model.Alert;
import com.secops.repository.AlertRepository;
import com.secops.strategy.AlertStrategyRegistry;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AlertService {
    private final AlertRepository alerts;
    private final AlertStrategyRegistry strategies;

    public AlertService(AlertRepository alerts,AlertStrategyRegistry strategies) {
        this.alerts=alerts; this.strategies=strategies;
    }

    public Page<Item> list(Query q) {
        List<Item> matches=alerts.findAll().stream()
                .filter(a -> q.alertType()==null || q.alertType().name().equals(a.alertType()))
                .filter(a -> q.category()==null || q.category().name().equals(a.category()))
                .filter(a -> q.severity()==null || q.severity().name().equals(a.severity()))
                .filter(a -> q.sourceIp()==null || q.sourceIp().equals(a.sourceIp()))
                .filter(a -> q.targetAsset()==null || q.targetAsset().equals(a.targetAsset()))
                .filter(a -> q.keyword()==null || contains(a.alertId(),q.keyword())
                        || contains(a.sourceIp(),q.keyword()) || contains(a.targetAsset(),q.keyword()))
                .sorted(Comparator.comparing(Alert::timestamp).reversed().thenComparing(Alert::alertId))
                .map(a -> Item.from(a,strategies.getStrategySupport(a.alertType()))).toList();
        long offset=((long)q.page()-1)*q.pageSize();
        return new Page<>(matches.size(),q.page(),q.pageSize(),matches.stream().skip(offset).limit(q.pageSize()).toList());
    }

    public Detail detail(String id) {
        Alert a=alerts.findById(id).orElseThrow(() -> new BizException(ErrorCode.ALERT_NOT_FOUND,"告警不存在"));
        return new Detail(AlertView.from(a),strategies.getStrategySupport(a.alertType()));
    }

    private static boolean contains(String value,String keyword) {
        return value!=null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
