package com.secops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.model.*;
import com.secops.repository.*;
import com.secops.service.IncidentService;
import com.secops.strategy.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class IncidentApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IncidentRepository incidents;
    @Autowired AlertRepository alerts;
    @Autowired IncidentService service;
    @Autowired SshBruteForceStrategy strategy;

    @Test void fixturePipelineAndRepeatedGeneration() {
        assertEquals(13,incidents.findAll().size());
        var ids=incidents.findAll().stream().flatMap(i -> i.alertIds().stream()).toList();
        assertEquals(85,ids.size()); assertEquals(85,new HashSet<>(ids).size());
        assertEquals(125,alerts.count());
        assertTrue(incidents.findAll().stream().allMatch(i -> i.alertType().equals("SSH_BRUTE_FORCE")));
        long incomplete=incidents.findAll().stream().filter(i -> i.riskAssessment().reasons().stream().anyMatch(r -> r.startsWith("数据不完整"))).count();
        // ALERT-0059 in scenario 005 also lacks sourceIp, in addition to five display-only alerts.
        assertEquals(6,incomplete);
        var before=incidents.findAll(); service.generate(); assertEquals(before,incidents.findAll());
        for (Incident i:before) {
            assertEquals(i.features(),strategy.extractFeatures(i,alerts.findByIds(i.alertIds())));
            assertEquals("NOT_STARTED",i.analysisStatus().name()); assertEquals("PENDING_ANALYSIS",i.status().name());
        }
    }
    @Test void listPaginationFiltersAndEmptyResult() throws Exception {
        mvc.perform(get("/api/incidents")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type",containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.data.total").value(13)).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20)).andExpect(jsonPath("$.data.items",hasSize(13)));
        mvc.perform(get("/api/incidents?page=2&pageSize=10")).andExpect(jsonPath("$.data.items",hasSize(3)));
        mvc.perform(get("/api/incidents?page=2147483647&pageSize=100")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items",hasSize(0)));
        for(String type:List.of("PORT_SCAN","SQL_INJECTION","MALWARE_ACTIVITY"))
            mvc.perform(get("/api/incidents").param("alertType",type)).andExpect(jsonPath("$.data.total").value(0));
        Incident i=incidents.findAll().get(0);
        mvc.perform(get("/api/incidents").param("keyword",i.incidentId()).param("sourceIp",i.sourceIp())
                .param("targetAsset",i.targetAsset()).param("status","PENDING_ANALYSIS").param("alertType","SSH_BRUTE_FORCE")
                .param("ruleRiskLevel",i.riskAssessment().level().name()))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].ruleRiskScore").value(i.riskAssessment().score()));
        mvc.perform(get("/api/incidents?status=CONFIRMED_THREAT")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/incidents?sourceIp=does-not-exist")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/incidents?targetAsset=does-not-exist")).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void invalidParametersAndMissingIncident() throws Exception {
        for(String query:List.of("page=0","page=-1","page=x","pageSize=0","pageSize=101","alertType=INVALID","ruleRiskLevel=INFO","status=SUCCEEDED"))
            mvc.perform(get("/api/incidents?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/incidents/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("INCIDENT_NOT_FOUND"));
    }
    @Test void detailsFollowContractAndDoNotLeakEvaluationData() throws Exception {
        for(Incident i:incidents.findAll()) {
            String body=mvc.perform(get("/api/incidents/"+i.incidentId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.incident.alertCount").value(i.alertCount()))
                    .andExpect(jsonPath("$.data.features.firstSeen").doesNotExist())
                    .andExpect(jsonPath("$.data.riskAssessment.score").value(i.riskAssessment().score()))
                    .andExpect(jsonPath("$.data.relatedAlerts",hasSize(i.alertCount())))
                    .andExpect(jsonPath("$.data.toolCallTraces",hasSize(0))).andReturn().getResponse().getContentAsString();
            var data=mapper.readTree(body).get("data");
            assertTrue(data.has("latestAnalysis")); assertTrue(data.get("latestAnalysis").isNull());
            for (String forbidden:List.of("scenarioId","expectedLabel","expectedToolPlan","SCENARIO-","assessedAt")) assertFalse(body.contains(forbidden));
            for(var rule:data.get("riskAssessment").get("matchedRules")) {
                assertTrue(rule.has("points")); assertFalse(rule.has("score")); assertTrue(rule.get("supportingFacts").size()>0);
            }
        }
    }
    @Test void repositoryRejectsDoubleMembershipAtomicallyAndSnapshotsAreImmutable() throws Exception {
        var repository=new InMemoryIncidentRepository(alerts);
        Incident i=incidents.findAll().get(0);
        Incident duplicate=new Incident("different-id",i.alertType(),i.category(),i.sourceIp(),i.targetIp(),i.targetAsset(),i.firstSeen(),i.lastSeen(),
                i.alertIds(),i.alertCount(),i.features(),i.riskAssessment(),i.analysisStatus(),i.status(),i.createdAt(),i.updatedAt());
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results=executor.invokeAll(List.<java.util.concurrent.Callable<Boolean>>of(
                () -> { try { repository.save(i); return true; } catch(IllegalArgumentException e) { return false; } },
                () -> { try { repository.save(duplicate); return true; } catch(IllegalArgumentException e) { return false; } }));
            assertNotEquals(results.get(0).get(),results.get(1).get());
        } finally { executor.shutdownNow(); }
        assertEquals(1,repository.findAll().size());
        assertThrows(UnsupportedOperationException.class,() -> repository.findAll().clear());
        assertThrows(UnsupportedOperationException.class,() -> i.alertIds().clear());
        assertThrows(UnsupportedOperationException.class,() -> i.riskAssessment().reasons().clear());
    }
}
