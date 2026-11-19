package com.secops.agent;
import com.secops.ai.*;
import com.secops.model.*;
import com.secops.repository.*;
import com.secops.strategy.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicit opt-in: mvn -Dtest=DashScopeLiveIT test; ordinary calls must pass first. */
@SpringBootTest
class DashScopeLiveIT {
    @Autowired LlmAdapter adapter;
    @Autowired SocTriageAgent agent;
    @Autowired IncidentRepository incidents;
    @Autowired AlertRepository alerts;
    @Autowired AlertStrategyRegistry strategies;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Test void ordinaryCallThenRealFunctionCalling() throws Exception {
        assumeTrue(System.getenv("SECOPS_API_KEY")!=null || System.getenv("SECOPS_COPILOT_API_KEY")!=null,
            "需配置 SECOPS_API_KEY（或兼容别名 SECOPS_COPILOT_API_KEY）才会执行真实模型调用");
        var plain=adapter.complete(List.of(LlmAdapter.Turn.text("user","只回答 OK")),false);
        assertNotNull(plain.text()); assertFalse(plain.text().isBlank()); assertTrue(plain.calls().isEmpty());
        Incident i=incidents.findAll().stream().filter(v -> "203.0.113.12".equals(v.sourceIp())).findFirst().orElseThrow();
        var strategy=strategies.getStrategy(i.alertType()); var traces=new ArrayList<ToolCallTrace>();
        var result=agent.run(i,strategy.buildContext(i,alerts.findByIds(i.alertIds())),
            strategy.getPromptTemplate()+" 本次集成验证要求先调用 query_login_history，lookbackHours=168，再输出最终报告。","LIVE",traces);
        assertTrue(traces.stream().anyMatch(t -> t.executionStatus().equals("SUCCESS")));
        assertNotEquals("INVALID",result.validationStatus());
        Files.writeString(Path.of("target/dashscope-live-result.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("result",result,"traces",traces)));
    }
}
