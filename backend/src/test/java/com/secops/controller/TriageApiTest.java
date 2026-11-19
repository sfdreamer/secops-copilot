package com.secops.controller;
import com.secops.ai.LlmAdapter;
import com.secops.common.*;
import com.secops.model.*;
import com.secops.repository.*;
import com.secops.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TriageApiTest {
    @Autowired MockMvc mvc;
    @Autowired IncidentRepository incidents;
    @Autowired AnalysisService analysis;
    @Autowired ReviewService review;
    @Autowired AIAnalysisRepository analyses;
    @Autowired ReviewRepository reviews;
    @MockitoBean LlmAdapter llm;
    Incident incident;
    static final String JSON="""
        {"verdict":"NEEDS_REVIEW","summary":"核实登录","attackType":"SSH_BRUTE_FORCE","riskLevel":"LOW",
        "reasoningSummary":"证据不足","evidence":[],"recommendations":["人工核实"],"uncertainties":[]}
        """;
    @BeforeEach void prepare() {
        incident=incidents.findAll().get(0);
        incidents.update(incident.withStatus(com.secops.model.enums.AnalysisStatus.NOT_STARTED,com.secops.model.enums.IncidentStatus.PENDING_ANALYSIS));
        when(llm.modelName()).thenReturn("mock");
        when(llm.complete(anyList(),anyBoolean())).thenReturn(new LlmAdapter.Reply(JSON,List.of()));
    }
    @Test void analysisDetailReviewAndTerminalState() throws Exception {
        String id=incident.incidentId();
        mvc.perform(post("/api/incidents/"+id+"/analyze")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.analysis.validationStatus").value("VALID"));
        mvc.perform(get("/api/incidents/"+id)).andExpect(jsonPath("$.data.latestAnalysis.verdict").value("NEEDS_REVIEW"));
        mvc.perform(post("/api/incidents/"+id+"/analyze")).andExpect(status().isConflict());
        mvc.perform(post("/api/incidents/"+id+"/review").contentType("application/json")
            .content("{\"reviewResult\":\"CONFIRMED_THREAT\",\"reviewer\":\"analyst\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.aiVerdict").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.data.incidentStatus").value("CONFIRMED_THREAT"));
        mvc.perform(post("/api/incidents/"+id+"/review").contentType("application/json")
            .content("{\"reviewResult\":\"FALSE_POSITIVE\",\"reviewer\":\"analyst\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/reviews").param("incidentId",id).param("reviewResult","CONFIRMED_THREAT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].reviewResult").value("CONFIRMED_THREAT"));
        mvc.perform(get("/api/reviews").param("pageSize","101")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/incidents/missing/analyze")).andExpect(status().isNotFound());
    }
    @Test void modelFailurePreservesRulesAndFailureRecord() throws Exception {
        when(llm.complete(anyList(),anyBoolean())).thenThrow(new BizException(ErrorCode.MODEL_SERVICE_ERROR,"不可用"));
        String id=incident.incidentId();
        mvc.perform(post("/api/incidents/"+id+"/analyze")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("MODEL_SERVICE_ERROR"));
        mvc.perform(get("/api/incidents/"+id)).andExpect(jsonPath("$.data.incident.status").value("ANALYSIS_FAILED"))
            .andExpect(jsonPath("$.data.latestAnalysis.analysisStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.riskAssessment.score").value(incident.riskAssessment().score()));
    }
    @Test void concurrentAnalysisAndReviewAreRejected() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(llm.complete(anyList(),anyBoolean())).thenAnswer(inv -> { entered.countDown(); release.await(5,TimeUnit.SECONDS); return new LlmAdapter.Reply(JSON,List.of()); });
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=pool.submit(()->analysis.analyze(incident.incidentId()));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            assertEquals(ErrorCode.ANALYSIS_IN_PROGRESS,assertThrows(BizException.class,()->analysis.analyze(incident.incidentId())).getErrorCode());
            assertThrows(BizException.class,()->review.review(incident.incidentId(),new ReviewService.Request(ReviewService.Result.INVESTIGATING,"a",null)));
            release.countDown(); first.get(5,TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void repeatedInvestigationAppendsAndConcurrentTerminalReviewHasOneWinner() throws Exception {
        String id=incident.incidentId(); int before=reviews.findByIncident(id).size();
        review.review(id,new ReviewService.Request(ReviewService.Result.INVESTIGATING,"a",null));
        review.review(id,new ReviewService.Request(ReviewService.Result.INVESTIGATING,"b",null));
        assertEquals(before+2,reviews.findByIncident(id).size());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> action=()-> { try { review.review(id,new ReviewService.Request(ReviewService.Result.FALSE_POSITIVE,"c",null)); return true; } catch(BizException e) { return false; } };
            var results=pool.invokeAll(List.of(action,action));
            assertNotEquals(results.get(0).get(),results.get(1).get());
            assertEquals(before+3,reviews.findByIncident(id).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void reviewValidationAndNoAiAssociation() throws Exception {
        String id=incidents.findAll().get(1).incidentId();
        mvc.perform(post("/api/incidents/"+id+"/review").contentType("application/json")
            .content("{\"reviewResult\":\"SUCCEEDED\",\"reviewer\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/incidents/"+id+"/review").contentType("application/json")
            .content("{\"reviewResult\":\"INVESTIGATING\",\"reviewer\":\" \"}")).andExpect(status().isBadRequest());
        var r=review.review(id,new ReviewService.Request(ReviewService.Result.INVESTIGATING,"a",null));
        assertNull(r.analysisId()); assertNull(r.aiVerdict());
    }
}
