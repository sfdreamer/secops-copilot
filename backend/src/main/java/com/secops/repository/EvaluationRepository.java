package com.secops.repository;

import com.secops.model.GroundTruth;
import java.util.List;
import java.util.Optional;

/** Ground Truth 访问入口（DATABASE.md 9 节）。只允许评估模块依赖本接口。 */
public interface EvaluationRepository {
    List<GroundTruth> findAllGroundTruth();
    Optional<GroundTruth> findByScenarioId(String scenarioId);
}
