package com.secops.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secops.model.Alert;
import com.secops.repository.InMemoryAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 纯单元：不启动 Spring 容器，直接验证加载/校验/去重逻辑。 */
class AlertFixtureLoaderTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private AlertFixtureLoader loader(InMemoryAlertRepository repository) {
        return new AlertFixtureLoader(mapper, repository, new AlertValidator(), new AlertNormalizer());
    }

    private static Alert alert(String id, String type, String category, String severity) {
        return new Alert(id, "SCENARIO-TEST", type, category,
                Instant.parse("2026-11-18T01:00:00Z"),
                "203.0.113.10", "10.0.0.10", "server-01", severity,
                "SIMULATED_SIEM",
                Map.of("protocol", "SSH", "username", "root", "action", "LOGIN_FAILED"),
                "Failed password for root from 203.0.113.10");
    }

    @Test
    void realFixtureFileLoadsCompletely() throws Exception {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        FixtureLoadReport report;
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("mock/alerts.json").getInputStream(), StandardCharsets.UTF_8)) {
            report = loader(repository).load(reader);
        }

        assertEquals(125, report.readCount(), "应完整读取 125 条告警");
        assertEquals(125, report.acceptedCount(), "全部告警应通过校验");
        assertEquals(0, report.duplicateCount());
        assertEquals(0, report.rejectedCount());
        assertEquals(125, repository.count());
        assertEquals(125, repository.findAll().stream().map(Alert::alertId).distinct().count());
    }

    @Test
    void duplicateAlertIdKeepsFirstOccurrence() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        Alert first = alert("ALERT-0001", "SSH_BRUTE_FORCE", "AUTHENTICATION", "HIGH");
        Alert duplicate = new Alert("ALERT-0001", "SCENARIO-TEST", "SSH_BRUTE_FORCE", "AUTHENTICATION",
                Instant.parse("2026-11-18T01:01:00Z"), "203.0.113.10", "10.0.0.10", "server-01",
                "LOW", "SIMULATED_SIEM", Map.of("protocol", "SSH"), "second");

        FixtureLoadReport report = loader(repository).load(List.of(first, duplicate));

        assertEquals(2, report.readCount());
        assertEquals(1, report.acceptedCount());
        assertEquals(1, report.duplicateCount());
        assertEquals("HIGH", repository.findById("ALERT-0001").orElseThrow().severity(),
                "重复 ID 应保留首条");
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("已去重")));
    }

    @Test
    void invalidEnumAndMissingRequiredFieldsAreRejected() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        List<Alert> raw = new ArrayList<>();
        raw.add(alert("ALERT-0001", "SSH_BRUTE_FORCE", "AUTHENTICATION", "HIGH"));
        raw.add(alert("ALERT-0002", "NOT_A_TYPE", "AUTHENTICATION", "HIGH"));
        raw.add(alert("ALERT-0003", "SSH_BRUTE_FORCE", "NOT_A_CATEGORY", "HIGH"));
        raw.add(alert("ALERT-0004", "SSH_BRUTE_FORCE", "AUTHENTICATION", "NOT_A_SEVERITY"));
        raw.add(new Alert(null, null, "SSH_BRUTE_FORCE", "AUTHENTICATION",
                Instant.parse("2026-11-18T01:00:00Z"), null, null, null, "HIGH", null, null, null));
        raw.add(new Alert("ALERT-0006", null, "SSH_BRUTE_FORCE", "AUTHENTICATION",
                null, null, null, null, "HIGH", null, null, null));

        FixtureLoadReport report = loader(repository).load(raw);

        assertEquals(1, report.acceptedCount());
        assertEquals(5, report.rejectedCount());
        assertEquals(1, repository.count());
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("alertType 非法")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("category 非法")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("severity 非法")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("缺少必填字段 alertId")));
        assertTrue(report.messages().stream().anyMatch(m -> m.contains("缺少必填字段 timestamp")));
    }

    @Test
    void optionalAggregationFieldsMayBeMissing() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        Alert incomplete = new Alert("ALERT-0001", "SCENARIO-SSH-005", "SSH_BRUTE_FORCE", "AUTHENTICATION",
                Instant.parse("2026-11-18T01:00:00Z"), null, "10.0.0.11", "server-02",
                "MEDIUM", "SIMULATED_SIEM", Map.of("protocol", "SSH"), "raw");

        FixtureLoadReport report = loader(repository).load(List.of(incomplete));

        assertEquals(1, report.acceptedCount(), "缺失可选聚合字段不应判为非法告警");
        assertNull(repository.findById("ALERT-0001").orElseThrow().sourceIp());
    }

    @Test
    void normalizedAttributesAreImmutable() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        loader(repository).load(List.of(alert("ALERT-0001", "SSH_BRUTE_FORCE", "AUTHENTICATION", "HIGH")));
        Map<String, Object> attributes = repository.findById("ALERT-0001").orElseThrow().attributes();

        assertThrows(UnsupportedOperationException.class,
                () -> attributes.put("injected", "value"));
    }

    @Test
    void emptyInputProducesEmptyReport() {
        InMemoryAlertRepository repository = new InMemoryAlertRepository();
        FixtureLoadReport report = loader(repository).load(List.of());

        assertEquals(0, report.acceptedCount());
        assertEquals(0, repository.count());
    }
}
