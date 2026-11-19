package com.secops.engine;

import com.secops.model.Alert;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

@Component
public class SshAlertAggregator {
    private record Key(String type, String source, String asset, String protocol, String singletonId) {}

    public static String attribute(Alert alert, String key) {
        Object value = alert.attributes() == null ? null : alert.attributes().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
    public static List<String> missingFields(Alert a) {
        List<String> fields = new ArrayList<>();
        if (a.sourceIp()==null || a.sourceIp().isBlank()) fields.add("sourceIp");
        if (a.targetAsset()==null || a.targetAsset().isBlank()) fields.add("targetAsset");
        if (attribute(a,"protocol")==null) fields.add("attributes.protocol");
        return List.copyOf(fields);
    }
    public List<List<Alert>> aggregate(List<Alert> alerts) {
        Map<String,Alert> unique = new LinkedHashMap<>();
        // Ingestion already deduplicates; defend direct callers too, preserving first occurrence.
        alerts.forEach(a -> unique.putIfAbsent(a.alertId(),a));
        Map<Key,List<Alert>> groups = new LinkedHashMap<>();
        unique.values().stream().filter(a -> "SSH_BRUTE_FORCE".equals(a.alertType()))
                .sorted(Comparator.comparing(Alert::timestamp).thenComparing(Alert::alertId)).forEach(a -> {
                    Key key = new Key(a.alertType(),a.sourceIp(),a.targetAsset(),attribute(a,"protocol"),
                            missingFields(a).isEmpty() ? null : a.alertId());
                    groups.computeIfAbsent(key,k -> new ArrayList<>()).add(a);
                });
        List<List<Alert>> result = new ArrayList<>();
        for (List<Alert> group : groups.values()) {
            List<Alert> window = new ArrayList<>();
            for (Alert a : group) {
                if (!window.isEmpty() && Duration.between(window.get(0).timestamp(),a.timestamp()).compareTo(Duration.ofMinutes(5)) > 0) {
                    result.add(List.copyOf(window)); window = new ArrayList<>();
                }
                window.add(a);
            }
            if (!window.isEmpty()) result.add(List.copyOf(window));
        }
        return List.copyOf(result);
    }
}
