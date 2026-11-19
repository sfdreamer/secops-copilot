package com.secops.agent;
import com.fasterxml.jackson.databind.*;
import com.secops.ai.*;
import com.secops.ai.LlmAdapter.*;
import com.secops.common.*;
import com.secops.engine.*;
import com.secops.model.*;
import com.secops.model.enums.*;
import com.secops.tools.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentTest {
    ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    DeadlineRunner runner=new DeadlineRunner();
    Incident incident;
    List<Alert> alerts;
    static final String JSON="""
        {"verdict":"NEEDS_REVIEW","summary":"需要人工核实","attackType":"SSH_BRUTE_FORCE","riskLevel":"LOW",
        "reasoningSummary":"现有证据不足","evidence":[],"recommendations":["核实账号"],"uncertainties":[]}
        """;
    @BeforeEach void setup() {
        Instant now=Instant.parse("2026-11-18T00:00:00Z");
        alerts=List.of(new Alert("A","SECRET_SCENARIO","SSH_BRUTE_FORCE","AUTHENTICATION",now,"203.0.113.12",
            null,"server-01","HIGH",null,Map.of("protocol","SSH","action","LOGIN_FAILED","username","ops-admin","expectedLabel","SECRET_LABEL"),"untrusted log"));
        var features=new SecurityFeatureExtractor().extract(alerts,null);
        incident=new Incident("I","SSH_BRUTE_FORCE","AUTHENTICATION","203.0.113.12",null,"server-01",now,now,List.of("A"),1,
            features,new RiskScoringEngine().assess(features),AnalysisStatus.NOT_STARTED,IncidentStatus.PENDING_ANALYSIS,now,now);
    }
    @AfterEach void close() { runner.close(); }
    Call call(String id) { return new Call(id,ReadOnlyTools.LOGIN,"{\"sourceIp\":\"203.0.113.12\",\"targetAsset\":\"server-01\",\"lookbackHours\":168}"); }
    SocTriageAgent agent(LlmAdapter llm,ReadOnlyTools tools,long toolTimeout,long total) {
        return new SocTriageAgent(llm,new ToolExecutor(tools,mapper,runner,toolTimeout),runner,mapper,new AgentResultValidator(mapper),2,4,total);
    }
    AgentResultValidator.Result run(SocTriageAgent a,List<ToolCallTrace> traces) {
        return a.run(incident,AIAnalysisContext.from(incident,alerts),TriagePrompt.SYSTEM,"ANL",traces);
    }
    @Test void directAnswerAndEvaluationIsolation() throws Exception {
        assertEquals(List.of("ops-admin"),AIAnalysisContext.from(incident,alerts).usernames());
        LlmAdapter llm=(turns,enabled) -> {
            String serialized=turns.toString();
            for(String secret:List.of("SECRET_SCENARIO","SECRET_LABEL","expectedLabel","scenarioId","expectedToolPlan")) assertFalse(serialized.contains(secret));
            return new Reply(JSON,List.of());
        };
        assertEquals("VALID",run(agent(llm,new ReadOnlyTools(mapper),3000,30000),new ArrayList<>()).validationStatus());
    }
    @Test void explicitToolUnavailabilityFallsBackWithoutHidingModelErrors() throws Exception {
        LlmAdapter llm=(turns,enabled) -> { if(enabled) throw new LlmAdapter.ToolsUnavailable(); return new Reply(JSON,List.of()); };
        var traces=new ArrayList<ToolCallTrace>();
        var result=run(agent(llm,new ReadOnlyTools(mapper),3000,30000),traces);
        assertEquals("NO_TOOL_FALLBACK",result.executionMode()); assertFalse(result.uncertainties().isEmpty()); assertTrue(traces.isEmpty());
    }
    @Test void realFixtureToolThroughMockLlmAndEvidence() throws Exception {
        LlmAdapter llm=(turns,enabled) -> {
            if(turns.size()==2) return new Reply("",List.of(call("c")));
            try {
                var feedback=mapper.readTree(turns.get(3).text());
                assertEquals(2,feedback.path("result").path("successCount").asInt());
                var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(JSON);
                root.putArray("evidence").addObject().put("sourceType","TOOL").put("fact","历史成功登录").put("explanation","需人工核实")
                    .putArray("toolCallIds").add(feedback.path("toolCallId").asText());
                ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("evidence").get(0)).putArray("alertIds");
                return new Reply(root.toString(),List.of());
            } catch(Exception e) { throw new RuntimeException(e); }
        };
        var traces=new ArrayList<ToolCallTrace>();
        var result=run(agent(llm,new ReadOnlyTools(mapper),3000,30000),traces);
        assertEquals(1,result.evidence().size()); assertEquals("SUCCESS",traces.get(0).executionStatus());
    }
    @Test void toolFailureAndTimeoutAreVisible() throws Exception {
        for(boolean timeout:List.of(false,true)) {
            ReadOnlyTools tools=mock(ReadOnlyTools.class);
            when(tools.execute(any(),any(),any())).thenAnswer(inv -> { if(timeout) Thread.sleep(500); throw new IllegalStateException("private"); });
            LlmAdapter llm=(turns,enabled) -> turns.size()==2?new Reply("",List.of(call("c"))):new Reply(JSON,List.of());
            var traces=new ArrayList<ToolCallTrace>();
            var result=run(agent(llm,tools,20,2000),traces);
            assertEquals(timeout?"TIMEOUT":"FAILED",traces.get(0).executionStatus()); assertFalse(result.uncertainties().isEmpty());
        }
    }
    @Test void illegalToolsAndBoundArguments() throws Exception {
        var executor=new ToolExecutor(new ReadOnlyTools(mapper),mapper,runner,3000);
        for(Call c:List.of(new Call("c","shell","{}"),new Call("c",ReadOnlyTools.LOGIN,call("c").arguments().replace("server-01","server-02")),
            new Call("c",ReadOnlyTools.LOGIN,call("c").arguments().replace("168","169")),new Call("c",ReadOnlyTools.LOGIN,"{}"))) {
            assertEquals("REJECTED",executor.execute(c,incident,"ANL",30000).executionStatus());
        }
    }
    @Test void executionIllegalArgumentIsFailureNotParameterRejection() throws Exception {
        ReadOnlyTools tools=mock(ReadOnlyTools.class);
        when(tools.execute(any(),any(),any())).thenThrow(new IllegalArgumentException("internal data error"));
        var trace=new ToolExecutor(tools,mapper,runner,3000).execute(call("c"),incident,"ANL",30000);
        assertEquals("FAILED",trace.executionStatus());
        assertEquals("TOOL_EXECUTION_FAILED",trace.errorCode());
    }
    @Test void duplicateJsonKeysAreRejected() throws Exception {
        String args=call("c").arguments().replace("\"lookbackHours\":168","\"lookbackHours\":1,\"lookbackHours\":168");
        var trace=new ToolExecutor(new ReadOnlyTools(mapper),mapper,runner,3000)
            .execute(new Call("c",ReadOnlyTools.LOGIN,args),incident,"ANL",30000);
        assertEquals("REJECTED",trace.executionStatus());
        String json=JSON.replace("\"verdict\":\"NEEDS_REVIEW\"","\"verdict\":\"SUSPICIOUS\",\"verdict\":\"NEEDS_REVIEW\"");
        assertThrows(BizException.class,()->new AgentResultValidator(mapper).validate(json,incident,"ANL",List.of()));
    }
    @Test void backendWarningsRespectArrayLimit() throws Exception {
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(JSON);
        var gaps=root.putArray("uncertainties");
        for(int n=0;n<20;n++) gaps.add("gap-"+n);
        var failed=new ToolCallTrace("t","ANL","I",ReadOnlyTools.LOGIN,Map.of(),"FAILED",null,"FAILED",1,Instant.now());
        var result=new AgentResultValidator(mapper).validate(root.toString(),incident,"ANL",List.of(failed)).fallback();
        assertEquals(20,result.uncertainties().size());
        assertTrue(result.uncertainties().get(18).contains("工具调用失败"));
        assertTrue(result.uncertainties().get(19).contains("Function Calling"));
    }
    @Test void boundedRoundsAndCalls() throws Exception {
        int[] n={0};
        LlmAdapter llm=(turns,enabled) -> { n[0]++; if(!enabled) return new Reply(JSON,List.of());
            return new Reply("",List.of(call("a"+n[0]),call("b"+n[0]))); };
        var traces=new ArrayList<ToolCallTrace>();
        run(agent(llm,new ReadOnlyTools(mapper),3000,30000),traces);
        assertEquals(4,traces.size()); assertEquals(3,n[0]);
    }
    @Test void oversizedBatchAndPersistentCallsFailWithoutExtraExecution() throws Exception {
        LlmAdapter oversized=(turns,enabled) -> new Reply("",List.of(call("1"),call("2"),call("3"),call("4"),call("5")));
        var traces=new ArrayList<ToolCallTrace>();
        assertThrows(BizException.class,()->run(agent(oversized,new ReadOnlyTools(mapper),3000,30000),traces)); assertTrue(traces.isEmpty());
        int[] count={0};
        LlmAdapter stubborn=(turns,enabled) -> new Reply("",List.of(call(""+count[0]++)));
        assertThrows(BizException.class,()->run(agent(stubborn,new ReadOnlyTools(mapper),3000,30000),traces)); assertEquals(2,traces.size());
    }
    @Test void modelFailureAndDeadline() throws Exception {
        LlmAdapter failure=(t,e)->{ throw new BizException(ErrorCode.MODEL_SERVICE_ERROR,"unavailable"); };
        assertEquals(ErrorCode.MODEL_SERVICE_ERROR,assertThrows(BizException.class,()->run(agent(failure,new ReadOnlyTools(mapper),3000,30000),new ArrayList<>())).getErrorCode());
        LlmAdapter slow=(t,e)-> { try { Thread.sleep(1000); } catch(InterruptedException x) { Thread.currentThread().interrupt(); } return new Reply(JSON,List.of()); };
        assertEquals(ErrorCode.AGENT_EXECUTION_ERROR,assertThrows(BizException.class,()->run(agent(slow,new ReadOnlyTools(mapper),3000,25),new ArrayList<>())).getErrorCode());
    }
    @Test void invalidEvidenceIsRemovedIncludingFailedOrOtherAnalysisTools() throws Exception {
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(JSON);
        var evidence=root.putArray("evidence");
        for(String type:List.of("ALERT","TOOL")) {
            var e=evidence.addObject().put("sourceType",type).put("fact","x").put("explanation","x");
            e.putArray("alertIds"); e.putArray("toolCallIds");
            ((com.fasterxml.jackson.databind.node.ArrayNode)e.get(type.equals("ALERT")?"alertIds":"toolCallIds")).add("outside");
        }
        var validator=new AgentResultValidator(mapper);
        var result=validator.validate(root.toString(),incident,"ANL",List.of());
        assertEquals("PARTIALLY_VALID",result.validationStatus()); assertTrue(result.evidence().isEmpty());
        var failed=new ToolCallTrace("outside","ANL","I","query_login_history",Map.of(),"FAILED",null,"FAILED",1,Instant.now());
        assertTrue(validator.validate(root.toString(),incident,"ANL",List.of(failed)).evidence().isEmpty());
        var other=new ToolCallTrace("outside","OTHER","I","query_login_history",Map.of(),"SUCCESS",Map.of(),null,1,Instant.now());
        assertTrue(validator.validate(root.toString(),incident,"ANL",List.of(other)).evidence().isEmpty());
    }
    @Test void invalidCoreOrJsonFails() {
        for(String json:List.of("not json",JSON.replace("NEEDS_REVIEW","ATTACK"),JSON.replace("\"uncertainties\":[]","\"uncertainties\":null"),JSON+" {}"))
            assertThrows(BizException.class,()->new AgentResultValidator(mapper).validate(json,incident,"ANL",List.of()));
    }
    @Test void historyExcludesAtAndAfterFirstSeen() throws Exception {
        var tool=new ReadOnlyTools(mapper);
        var result=tool.execute(ReadOnlyTools.LOGIN,mapper.readTree(call("c").arguments()),incident);
        assertEquals(2,result.get("totalLoginCount")); assertEquals(true,result.get("knownSource"));
        // Fixture success at 08:05 is after this incident's 08:00 anchor.
        assertEquals(Instant.parse("2026-11-17T23:20:00Z"),result.get("lastLoginTime"));
    }
    @Test void authorizedOperationsAreBoundedAndExcludeCancelledTickets() throws Exception {
        var tool=new ReadOnlyTools(mapper);
        Incident maintenance=new Incident("M","SSH_BRUTE_FORCE","AUTHENTICATION","203.0.113.12",null,"server-01",
            Instant.parse("2026-11-18T01:20:00Z"),Instant.parse("2026-11-18T01:24:50Z"),List.of("A"),1,
            incident.features(),incident.riskAssessment(),AnalysisStatus.NOT_STARTED,IncidentStatus.PENDING_ANALYSIS,
            incident.createdAt(),incident.updatedAt());
        var valid=mapper.readTree("{\"targetAsset\":\"server-01\",\"startTime\":\"2026-11-18T01:20:00Z\",\"endTime\":\"2026-11-18T01:24:50Z\"}");
        var result=tool.execute(ReadOnlyTools.AUTHORIZED,valid,maintenance);
        assertEquals(true,result.get("matched"));
        assertEquals("CHG-20261118-001",((List<?>)result.get("operations")).stream()
            .map(v -> ((Map<?,?>)v).get("ticketId")).findFirst().orElseThrow());
        assertEquals("2026-11-18T01:18:00Z",((Map<?,?>)((List<?>)result.get("operations")).get(0)).get("startTime"));
        var executor=new ToolExecutor(tool,mapper,runner,3000);
        var success=executor.execute(new Call("auth",ReadOnlyTools.AUTHORIZED,valid.toString()),maintenance,"ANL",30000);
        assertEquals("SUCCESS",success.executionStatus());
        assertEquals(true,((Map<?,?>)success.resultSummary()).get("matched"));
        Incident cancelled=new Incident("C","SSH_BRUTE_FORCE","AUTHENTICATION","198.51.100.21",null,"server-02",
            Instant.parse("2026-11-18T02:00:00Z"),Instant.parse("2026-11-18T02:04:50Z"),List.of("A"),1,
            incident.features(),incident.riskAssessment(),AnalysisStatus.NOT_STARTED,IncidentStatus.PENDING_ANALYSIS,
            incident.createdAt(),incident.updatedAt());
        var cancelledQuery=mapper.readTree("{\"targetAsset\":\"server-02\",\"startTime\":\"2026-11-18T02:00:00Z\",\"endTime\":\"2026-11-18T02:04:50Z\"}");
        assertEquals(false,tool.execute(ReadOnlyTools.AUTHORIZED,cancelledQuery,cancelled).get("matched"));
        for(String json:List.of(
            "{\"targetAsset\":\"server-02\",\"startTime\":\"2026-11-18T01:20:00Z\",\"endTime\":\"2026-11-18T01:24:50Z\"}",
            "{\"targetAsset\":\"server-01\",\"startTime\":\"2026-11-18T01:14:59Z\",\"endTime\":\"2026-11-18T01:24:50Z\"}",
            "{\"targetAsset\":\"server-01\",\"startTime\":\"2026-11-18T01:24:50Z\",\"endTime\":\"2026-11-18T01:20:00Z\"}"))
            assertEquals("REJECTED",executor.execute(new Call("bad",ReadOnlyTools.AUTHORIZED,json),maintenance,"ANL",30000).executionStatus());
    }
}
