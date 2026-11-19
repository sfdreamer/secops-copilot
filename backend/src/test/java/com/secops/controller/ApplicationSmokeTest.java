package com.secops.controller;

import com.secops.model.Alert;
import com.secops.model.enums.AlertCategory;
import com.secops.model.enums.AlertType;
import com.secops.model.enums.Severity;
import com.secops.repository.AlertRepository;
import com.secops.strategy.AlertStrategyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 启动链路冒烟：容器能启动即证明 fixtures 加载成功（@PostConstruct 失败会中止启动）。 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertStrategyRegistry registry;

    @Test
    void fixturesAreLoadedIntoRepository() {
        assertEquals(125, alertRepository.count());
        assertEquals(125, alertRepository.findAll().stream().map(Alert::alertId).distinct().count());
    }

    @Test
    void allLoadedAlertsAreValid() {
        Set<String> types = java.util.Arrays.stream(AlertType.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> categories = java.util.Arrays.stream(AlertCategory.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> severities = java.util.Arrays.stream(Severity.values()).map(Enum::name).collect(Collectors.toSet());
        for (Alert alert : alertRepository.findAll()) {
            assertNotNull(alert.alertId());
            assertNotNull(alert.timestamp());
            assertTrue(types.contains(alert.alertType()), "非法 alertType: " + alert.alertType());
            assertTrue(categories.contains(alert.category()), "非法 category: " + alert.category());
            assertTrue(severities.contains(alert.severity()), "非法 severity: " + alert.severity());
        }
    }

    @Test
    void unsupportedAlertTypesHaveNoStrategy() {
        long unsupported = alertRepository.findAll().stream()
                .filter(a -> !registry.isSupported(a.alertType()))
                .count();
        assertEquals(40, unsupported, "四类告警中 40 条为未支持策略的展示样本");
    }

    @Test
    void eachEvaluationScenarioFormsExactlyOneIncidentCandidate() {
        Map<String, List<Alert>> byScenario = alertRepository.findAll().stream()
                .filter(a -> a.scenarioId() != null)
                .collect(Collectors.groupingBy(Alert::scenarioId));

        assertEquals(8, byScenario.size());
        assertEquals(80, byScenario.values().stream().mapToInt(List::size).sum());

        Set<String> aggregationKeys = new java.util.HashSet<>();
        for (Map.Entry<String, List<Alert>> entry : byScenario.entrySet()) {
            List<Alert> alerts = entry.getValue();
            Instant first = alerts.stream().map(Alert::timestamp).min(Instant::compareTo).orElseThrow();
            Instant last = alerts.stream().map(Alert::timestamp).max(Instant::compareTo).orElseThrow();
            assertTrue(Duration.between(first, last).getSeconds() <= 300,
                    entry.getKey() + " 必须落在同一 5 分钟窗口内");

            Set<String> sourceIps = alerts.stream().map(Alert::sourceIp).collect(Collectors.toSet());
            Set<String> assets = alerts.stream().map(Alert::targetAsset).collect(Collectors.toSet());
            assertEquals(1, sourceIps.size(), entry.getKey() + " 应共享聚合键 sourceIp");
            assertEquals(1, assets.size(), entry.getKey() + " 应共享聚合键 targetAsset");

            String key = sourceIps.iterator().next() + "|" + assets.iterator().next()
                    + "|" + first.toEpochMilli();
            assertTrue(aggregationKeys.add(key), "不同评估场景不得因聚合键与窗口相同而合并");
        }
    }

    @Test
    void healthReturnsExpectedShape() throws Exception {
        mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.mockDataLoaded").value(true))
                .andExpect(jsonPath("$.data.modelConfigured").isBoolean())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void healthNeverLeaksModelSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("SECOPS_API_KEY"), "不得返回环境变量名");
        assertFalse(body.contains("SECOPS_COPILOT_API_KEY"), "不得返回历史环境变量名");
        assertFalse(body.contains("apiKey"), "不得返回密钥字段");
        assertFalse(body.contains("Bearer "), "不得返回凭据");
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void repositoryDoesNotExposeMutableReferences() {
        List<Alert> snapshot = alertRepository.findAll();
        List<Alert> copy = new ArrayList<>(snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertEquals(copy.size(), alertRepository.count());
    }
}
