package com.secops.service;
import com.secops.agent.*;
import com.secops.ai.TriagePrompt;
import com.secops.common.*;
import com.secops.model.*;
import com.secops.model.enums.*;
import com.secops.repository.*;
import com.secops.strategy.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class AnalysisService {
    private final IncidentRepository incidents; private final AlertRepository alerts; private final AlertStrategyRegistry strategies;
    private final AIAnalysisRepository analyses; private final ToolCallTraceRepository traces; private final SocTriageAgent agent;
    public AnalysisService(IncidentRepository incidents,AlertRepository alerts,AlertStrategyRegistry strategies,
        AIAnalysisRepository analyses,ToolCallTraceRepository traces,SocTriageAgent agent) {
        this.incidents=incidents; this.alerts=alerts; this.strategies=strategies; this.analyses=analyses; this.traces=traces; this.agent=agent;
    }
    public record Metrics(int toolCallCount,long agentDurationMs) {}
    public record Response(String incidentId,String analysisStatus,String status,AIAnalysis analysis,List<ToolCallTrace> toolCallTraces,Metrics agentMetrics) {}
    public Response analyze(String id) {
        Incident i;
        synchronized(incidents) {
            i=incidents.findById(id).orElseThrow(() -> new BizException(ErrorCode.INCIDENT_NOT_FOUND,"事件不存在"));
            strategies.requireSupported(i.alertType());
            if(i.status()==IncidentStatus.ANALYZING) throw new BizException(ErrorCode.ANALYSIS_IN_PROGRESS,"事件正在研判");
            if(!Set.of(IncidentStatus.PENDING_ANALYSIS,IncidentStatus.ANALYSIS_FAILED,IncidentStatus.INVESTIGATING).contains(i.status()))
                throw new BizException(ErrorCode.INVALID_INCIDENT_STATUS,"当前状态不允许研判");
            incidents.update(i.withStatus(AnalysisStatus.RUNNING,IncidentStatus.ANALYZING));
        }
        String analysisId="ANL-"+UUID.randomUUID(); List<ToolCallTrace> calls=new ArrayList<>(); long start=System.nanoTime();
        AIAnalysis analysis; BizException failure=null;
        try {
            var strategy=strategies.getStrategy(i.alertType());
            var result=agent.run(i,strategy.buildContext(i,alerts.findByIds(i.alertIds())),strategy.getPromptTemplate(),analysisId,calls);
            analysis=new AIAnalysis(analysisId,id,"SUCCEEDED",result.verdict(),result.summary(),result.attackType(),result.riskLevel(),
                result.reasoningSummary(),result.evidence(),result.recommendations(),result.uncertainties(),agent.modelName(),TriagePrompt.VERSION,
                result.executionMode(),elapsed(start),calls.size(),result.validationStatus(),null,Instant.now());
        } catch(Exception e) {
            failure=e instanceof BizException b?b:new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"Agent 执行失败");
            analysis=new AIAnalysis(analysisId,id,"FAILED",null,"",null,null,"",List.of(),List.of(),List.of(),agent.modelName(),
                TriagePrompt.VERSION,"TOOL_ENABLED",elapsed(start),calls.size(),"INVALID",failure.getMessage(),Instant.now());
        }
        synchronized(incidents) {
            analyses.save(analysis); calls.forEach(traces::save);
            incidents.update(i.withStatus(failure==null?AnalysisStatus.SUCCEEDED:AnalysisStatus.FAILED,
                failure==null?IncidentStatus.PENDING_REVIEW:IncidentStatus.ANALYSIS_FAILED));
        }
        if(failure!=null) throw failure;
        return new Response(id,"SUCCEEDED","PENDING_REVIEW",analysis,List.copyOf(calls),new Metrics(calls.size(),analysis.durationMs()));
    }
    private long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
}
