package com.secops.repository;

import com.secops.model.Alert;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 运行期只读内存集合；不暴露可被外部直接修改的引用（DATABASE.md 9 节）。 */
@Repository
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<String, Alert> store = new ConcurrentHashMap<>();

    @Override
    public List<Alert> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Alert::alertId))
                .toList();
    }

    @Override
    public Optional<Alert> findById(String alertId) {
        if (alertId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(alertId));
    }

    @Override
    public List<Alert> findByIds(Collection<String> alertIds) {
        if (alertIds == null) {
            return List.of();
        }
        return alertIds.stream()
                .map(store::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public int count() {
        return store.size();
    }

    /** 只允许由 Ingestion 在启动加载时整体替换。 */
    public void replaceAll(Collection<Alert> alerts) {
        store.clear();
        for (Alert alert : alerts) {
            store.put(alert.alertId(), alert);
        }
    }
}
