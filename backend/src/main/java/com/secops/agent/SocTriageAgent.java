package com.secops.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.ai.*;
import com.secops.ai.LlmAdapter.*;
import com.secops.common.*;
import com.secops.model.*;
import com.secops.tools.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Component
public class SocTriageAgent {
    private final LlmAdapter llm; private final ToolExecutor tools; private final DeadlineRunner runner;
    private final ObjectMapper mapper; private final AgentResultValidator validator;
    private final int maxRounds,maxCalls; private final long timeout;
    public SocTriageAgent(LlmAdapter llm,ToolExecutor tools,DeadlineRunner runner,ObjectMapper mapper,AgentResultValidator validator,
        @Value("${app.agent.max-tool-rounds:2}") int rounds,@Value("${app.agent.max-tool-calls:4}") int calls,
        @Value("${app.agent.total-timeout-ms:45000}") long timeout) {
        this.llm=llm; this.tools=tools; this.runner=runner; this.mapper=mapper; this.validator=validator;
        // 代码硬上限 60000ms：配置可放宽至 45s，但不能无限放大（前端研判请求 60s / Nginx 90s）。
        this.maxRounds=Math.max(0,Math.min(2,rounds)); this.maxCalls=Math.max(0,Math.min(4,calls)); this.timeout=Math.max(1,Math.min(60000,timeout));
    }
    public String modelName() { return llm.modelName(); }
    public AgentResultValidator.Result run(Incident i,AIAnalysisContext context,String prompt,String analysisId,List<ToolCallTrace> traces) {
        long deadline=System.nanoTime()+timeout*1_000_000;
        try {
            List<Turn> turns=new ArrayList<>(List.of(Turn.text("system",prompt),Turn.text("user",mapper.writeValueAsString(context))));
            int rounds=0; boolean fallback=false; Set<String> callIds=new HashSet<>();
            while(true) {
                boolean enabled=!fallback && rounds<maxRounds && traces.size()<maxCalls;
                List<Turn> snapshot=List.copyOf(turns);
                Reply reply;
                try { reply=runner.call(() -> llm.complete(snapshot,enabled),remaining(deadline)); }
                catch(LlmAdapter.ToolsUnavailable e) {
                    if(!enabled || !traces.isEmpty()) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR,"工具协议不可用");
                    fallback=true;
                    turns.add(Turn.text("user","工具协议不可用；仅根据已有事实给出最终 JSON，并在 uncertainties 明确信息缺口。"));
                    continue;
                }
                if(reply.calls()==null) throw new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"模型返回无效工具协议");
                if(reply.calls().isEmpty()) {
                    var result=validator.validate(reply.text(),i,analysisId,traces);
                    if(remaining(deadline)<=0) throw new TimeoutException();
                    return fallback?result.fallback():result;
                }
                if(!enabled || reply.calls().size()>maxCalls-traces.size())
                    throw new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"工具调用达到上限");
                for(Call call:reply.calls()) if(call.id()==null || call.id().isBlank() || !callIds.add(call.id()))
                    throw new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"模型工具调用 ID 无效或重复");
                turns.add(new Turn("assistant",reply.text(),reply.calls(),null,null));
                for(Call call:reply.calls()) {
                    long left=remaining(deadline); if(left<=0) throw new TimeoutException();
                    ToolCallTrace trace=tools.execute(call,i,analysisId,left); traces.add(trace);
                    Map<String,Object> feedback=new LinkedHashMap<>();
                    feedback.put("toolCallId",trace.toolCallId()); feedback.put("status",trace.executionStatus());
                    feedback.put("result",trace.resultSummary()); feedback.put("errorCode",trace.errorCode());
                    turns.add(new Turn("tool",mapper.writeValueAsString(feedback),List.of(),call.id(),call.name()));
                }
                rounds++;
                if(rounds>=maxRounds || traces.size()>=maxCalls)
                    turns.add(Turn.text("user","工具预算已用完，请只根据已有证据输出最终 JSON，说明仍有的信息缺口。"));
            }
        } catch(BizException e) { throw e; }
        catch(TimeoutException e) { throw new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"Agent 总耗时达到上限"); }
        catch(Exception e) { throw new BizException(ErrorCode.AGENT_EXECUTION_ERROR,"Agent 执行失败"); }
    }
    private long remaining(long deadline) { return (deadline-System.nanoTime())/1_000_000; }
}
