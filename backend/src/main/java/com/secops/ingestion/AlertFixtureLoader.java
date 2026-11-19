package com.secops.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.model.Alert;
import com.secops.repository.InMemoryAlertRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 加载、解析、校验、去重并标准化告警（ARCHITECTURE.md 3.1）。 */
@Component
public class AlertFixtureLoader {

    private static final Logger log = LoggerFactory.getLogger(AlertFixtureLoader.class);

    private final ObjectMapper objectMapper;
    private final InMemoryAlertRepository repository;
    private final AlertValidator validator;
    private final AlertNormalizer normalizer;

    @Value("${app.fixtures.alerts-path}")
    private Resource alertsResource;

    /** 保留最近一次加载报告，供评估模块计算数据质量指标（只读，不修改 fixtures）。 */
    private volatile FixtureLoadReport report;

    public FixtureLoadReport getReport() {
        return report;
    }

    public AlertFixtureLoader(ObjectMapper objectMapper,
                              InMemoryAlertRepository repository,
                              AlertValidator validator,
                              AlertNormalizer normalizer) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.validator = validator;
        this.normalizer = normalizer;
    }

    /** 纯逻辑入口，供单测直接注入构造数据，不依赖 Spring 与文件系统。 */
    public FixtureLoadReport load(List<Alert> rawAlerts) {
        List<Alert> raw = rawAlerts == null ? List.of() : rawAlerts;
        List<Alert> normalized = new ArrayList<>(raw.size());
        List<String> messages = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            Alert item = raw.get(i);
            if (item == null) {
                messages.add("index=" + i + " 告警记录为 null，已拒绝");
                continue;
            }
            try {
                normalized.add(normalizer.normalize(item));
            } catch (RuntimeException e) {
                messages.add("index=" + i + " 标准化失败，已拒绝: " + e.getMessage());
            }
        }

        AlertValidator.Result validation = validator.validate(normalized);
        for (AlertValidator.Issue issue : validation.rejected()) {
            messages.add("index=" + issue.index() + " alertId=" + issue.alertId()
                    + " 校验失败: " + issue.reason());
        }

        // C2 裁决：重复 alertId 去重保留首条 + WARN，不因单条重复导致启动失败
        Map<String, Alert> deduplicated = new LinkedHashMap<>();
        int duplicateCount = 0;
        for (Alert alert : validation.accepted()) {
            Alert previous = deduplicated.putIfAbsent(alert.alertId(), alert);
            if (previous != null) {
                duplicateCount++;
                String message = "重复 alertId=" + alert.alertId() + " 已去重，保留首条";
                messages.add(message);
                log.warn(message);
            }
        }

        repository.replaceAll(deduplicated.values());

        return new FixtureLoadReport(
                raw.size(),
                deduplicated.size(),
                duplicateCount,
                validation.rejected().size() + (raw.size() - normalized.size()),
                List.copyOf(messages)
        );
    }

    public FixtureLoadReport load(Reader reader) throws IOException {
        List<Alert> raw = objectMapper.readValue(reader, new TypeReference<List<Alert>>() {
        });
        return load(raw);
    }

    @PostConstruct
    public void init() {
        try (Reader reader = new InputStreamReader(alertsResource.getInputStream(), StandardCharsets.UTF_8)) {
            FixtureLoadReport report = load(reader);
            this.report = report;
            log.info("Alert fixtures loaded: read={}, accepted={}, duplicates={}, rejected={}",
                    report.readCount(), report.acceptedCount(), report.duplicateCount(), report.rejectedCount());
            for (String message : report.messages()) {
                log.warn("Fixture issue: {}", message);
            }
            // 不能静默返回空数据并显示虚假统计（DATABASE.md 10 节）
            if (report.acceptedCount() == 0) {
                throw new IllegalStateException("未加载到任何有效告警，拒绝以空数据启动");
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("告警 fixtures 加载失败，应用启动中止", e);
        }
    }
}
