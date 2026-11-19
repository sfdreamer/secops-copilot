package com.secops.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.model.GroundTruth;
import com.secops.repository.EvaluationRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 从只读 fixtures 加载 Ground Truth（ARCHITECTURE.md 6.2）。
 * 本类位于 evaluation 包，只有 EvaluationService 允许注入；加载失败必须中止启动，不得静默返回空标签。
 */
@Repository
public class GroundTruthFixtureRepository implements EvaluationRepository {

    private static final Logger log = LoggerFactory.getLogger(GroundTruthFixtureRepository.class);

    private final ObjectMapper objectMapper;

    @Value("${app.fixtures.ground-truth-path}")
    private Resource groundTruthResource;

    private Map<String, GroundTruth> store = Map.of();

    public GroundTruthFixtureRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try (Reader reader = new InputStreamReader(groundTruthResource.getInputStream(), StandardCharsets.UTF_8)) {
            List<GroundTruth> raw = objectMapper.readValue(reader, new TypeReference<List<GroundTruth>>() {});
            Map<String, GroundTruth> loaded = new LinkedHashMap<>();
            for (GroundTruth gt : raw == null ? List.<GroundTruth>of() : raw) {
                if (gt == null || gt.scenarioId() == null || gt.scenarioId().isBlank()) {
                    throw new IllegalStateException("Ground Truth 存在缺少 scenarioId 的记录");
                }
                if (loaded.putIfAbsent(gt.scenarioId(), gt) != null) {
                    throw new IllegalStateException("Ground Truth scenarioId 重复: " + gt.scenarioId());
                }
            }
            store = Map.copyOf(loaded);
            log.info("Ground truth loaded: {} scenarios", store.size());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Ground Truth fixtures 加载失败，应用启动中止", e);
        }
    }

    @Override
    public List<GroundTruth> findAllGroundTruth() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<GroundTruth> findByScenarioId(String scenarioId) {
        return Optional.ofNullable(store.get(scenarioId));
    }
}
