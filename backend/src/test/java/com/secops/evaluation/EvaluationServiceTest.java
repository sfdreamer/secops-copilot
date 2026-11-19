package com.secops.evaluation;

import com.secops.ai.LlmAdapter;
import com.secops.ingestion.AlertFixtureLoader;
import com.secops.ingestion.FixtureLoadReport;
import com.secops.model.*;
import com.secops.model.enums.*;
import com.secops.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ARCHITECTURE 9 / AGENTS 7 要求的评估模块测试：空样本、除零、UNCERTAIN 单独统计、
 * 证据可追溯率不可计算、重复 ID 不误算解析失败。全部使用 Mock，不触发真实模型。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final Instant T = Instant.parse("2026-11-18T00:00:00Z");

    @Mock AlertRepository alerts;
    @Mock IncidentRepository incidents;
    @Mock AIAnalysisRepository analyses;
    @Mock ToolCallTraceRepository traces;
    @Mock ReviewRepository reviews;
    @Mock EvaluationRepository groundTruth;
    @Mock AlertFixtureLoader fixtureLoader;
    @Mock LlmAdapter llm;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(alerts, incidents, analyses, traces, reviews,
                groundTruth, fixtureLoader, llm);
        lenient().when(llm.modelName()).thenReturn("test-model");
        lenient().when(fixtureLoader.getReport()).thenReturn(new FixtureLoadReport(0, 0, 0, 0, List.of()));
        lenient().when(alerts.findAll()).thenReturn(List.of());
        lenient().when(alerts.count()).thenReturn(0);
        lenient().when(incidents.findAll()).thenReturn(List.of());
        lenient().when(analyses.findAll()).thenReturn(List.of());
        lenient().when(analyses.findByIncident(anyString())).thenReturn(List.of());
        lenient().when(traces.findAll()).thenReturn(List.of());
        lenient().when(traces.findByIncident(anyString())).thenReturn(List.of());
        lenient().when(reviews.findAll()).thenReturn(List.of());
    }

    @Test
    void emptySamplesReturnNullWithoutDivisionByZero() {
        EvaluationService.Result r = assertDoesNotThrow(() -> service.evaluate());

        assertNull(r.dataQuality().parseSuccessRate());
        assertNull(r.dataQuality().requiredFieldCompleteness());
        assertNull(r.aggregation().compressionRate());
        assertEquals(0, r.ruleEvaluation().sampleCount());
        assertEquals(0, r.ruleEvaluation().uncertainSampleCount());
        assertNull(r.ruleEvaluation().precision());
        assertNull(r.ruleEvaluation().recall());
        assertNull(r.ruleEvaluation().f1());
        assertEquals(0, r.aiEvaluation().sampleCount());
        assertNull(r.aiEvaluation().accuracy());
        assertNull(r.aiEvaluation().macroF1());
        assertEquals(0, r.agentEvaluation().toolCallCount());
        assertNull(r.agentEvaluation().toolSuccessRate());
        assertNull(r.agentEvaluation().parameterValidityRate());
        assertNull(r.businessEvaluation().adoptionRate());
        assertEquals("NOT_MEASURED", r.businessEvaluation().measurementStatus());
    }

    @Test
    void uncertainLabelsAreCountedSeparatelyFromBinaryClassification() {
        Incident attack = incident("INC-A", "a1", 80);
        Incident benign = incident("INC-B", "a2", 10);
        Incident uncertain = incident("INC-U", "a3", 90);
        when(incidents.findAll()).thenReturn(List.of(attack, benign, uncertain));
        when(alerts.findByIds(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Collection<String> ids = inv.getArgument(0);
            return ids.stream().map(id -> alert(id, "SC-" + id)).toList();
        });
        when(groundTruth.findByScenarioId(anyString())).thenAnswer(inv -> {
            String sid = inv.getArgument(0);
            String label = sid.endsWith("a3") ? "UNCERTAIN" : (sid.endsWith("a1") ? "ATTACK" : "BENIGN");
            return Optional.of(new GroundTruth(sid, label, List.of(), ""));
        });

        EvaluationService.Result r = service.evaluate();

        assertEquals(3, r.meta().sampleCount());
        assertEquals(2, r.ruleEvaluation().sampleCount(), "UNCERTAIN 不得进入二分类分母");
        assertEquals(1, r.ruleEvaluation().uncertainSampleCount(), "UNCERTAIN 必须单独计数");
        assertEquals(1.0, r.ruleEvaluation().precision());
        assertEquals(1.0, r.ruleEvaluation().recall());
        assertEquals(1.0, r.ruleEvaluation().f1());
    }

    @Test
    void traceabilityRateStaysNullEvenWhenValidEvidenceExists() {
        Incident inc = incident("INC-A", "a1", 80);
        when(incidents.findAll()).thenReturn(List.of(inc));
        when(analyses.findAll()).thenReturn(List.of(new AIAnalysis(
                "AN-1", "INC-A", "SUCCEEDED", "SUSPICIOUS", "summary", "SSH_BRUTE_FORCE", "HIGH", "reasoning",
                List.of(new AIAnalysis.Evidence("ALERT", List.of("a1"), List.of(), "fact", "explanation")),
                List.of("rec"), List.of("uncertainty"), "test-model", "ssh-triage-v1",
                "TOOL_ENABLED", 100L, 1, "VALID", null, T)));

        EvaluationService.Result r = service.evaluate();

        assertNull(r.evidenceEvaluation().traceabilityRate(),
                "过滤前引用总数未持久化，分母不可还原，不得输出恒为 1.0 的失真值");
        assertEquals("NOT_COMPUTABLE", r.evidenceEvaluation().traceabilityStatus());
        assertNull(r.evidenceEvaluation().semanticSupportRate());
        assertEquals("NOT_EVALUATED", r.evidenceEvaluation().semanticSupportStatus());
    }

    @Test
    void duplicateAlertIdsAreNotCountedAsParseFailures() {
        when(fixtureLoader.getReport()).thenReturn(
                new FixtureLoadReport(3, 3, 1, 0, List.of("duplicate alertId")));
        when(alerts.count()).thenReturn(2);

        EvaluationService.Result r = service.evaluate();

        assertEquals(2, r.dataQuality().parsedAlertCount(), "入库数仍是去重后的数量");
        assertEquals(1.0, r.dataQuality().parseSuccessRate(), "去重不是解析失败");
        assertEquals(1.0, r.dataQuality().requiredFieldCompleteness());
    }

    @Test
    void allRejectedFixtureYieldsZeroRatesWithoutDivisionByZero() {
        when(fixtureLoader.getReport()).thenReturn(
                new FixtureLoadReport(2, 0, 0, 2, List.of("invalid enum")));
        when(alerts.count()).thenReturn(0);

        EvaluationService.Result r = assertDoesNotThrow(() -> service.evaluate());

        assertEquals(0.0, r.dataQuality().parseSuccessRate());
        assertEquals(0.0, r.dataQuality().requiredFieldCompleteness());
    }

    private static Incident incident(String id, String alertId, int score) {
        SecurityFeatures features = new SecurityFeatures(0, 0, 0, 0, 0.0, 0.0, false, null, "UNKNOWN");
        RiskAssessment risk = new RiskAssessment(score, RiskLevel.LOW, List.of(), List.of(), T);
        return new Incident(id, "SSH_BRUTE_FORCE", "NETWORK", "203.0.113.12", null, "server-01",
                T, T, List.of(alertId), 1, features, risk,
                AnalysisStatus.NOT_STARTED, IncidentStatus.PENDING_ANALYSIS, T, T);
    }

    private static Alert alert(String id, String scenarioId) {
        return new Alert(id, scenarioId, "SSH_BRUTE_FORCE", "NETWORK", T,
                "203.0.113.12", null, "server-01", "HIGH", "IDS", Map.of(), "raw log");
    }
}
