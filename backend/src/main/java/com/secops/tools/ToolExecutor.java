package com.secops.tools;
import com.fasterxml.jackson.databind.*;
import com.secops.agent.DeadlineRunner;
import com.secops.ai.LlmAdapter.Call;
import com.secops.model.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Component
public class ToolExecutor {
    private final ReadOnlyTools tools; private final ObjectMapper mapper; private final DeadlineRunner runner; private final long timeout;
    public ToolExecutor(ReadOnlyTools tools,ObjectMapper mapper,DeadlineRunner runner,
            @Value("${app.agent.tool-timeout-ms:3000}") long timeout) {
        this.tools=tools; this.mapper=mapper; this.runner=runner; this.timeout=Math.min(3000,Math.max(1,timeout));
    }
    public ToolCallTrace execute(Call call,Incident i,String analysisId,long remaining) {
        Instant now=Instant.now(); long started=System.nanoTime(); String status="SUCCESS",error=null; Object result=null;
        Map<String,Object> summary=new LinkedHashMap<>();
        boolean validated=false;
        try {
            JsonNode args=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(call.arguments());
            tools.validate(call.name(),args,i);
            validated=true;
            args.fields().forEachRemaining(e -> summary.put(e.getKey(),e.getValue().isTextual()?e.getValue().textValue():e.getValue().intValue()));
            result=runner.call(() -> tools.execute(call.name(),args,i),Math.min(timeout,remaining));
        } catch(IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            status=validated?"FAILED":"REJECTED";
            error=validated?"TOOL_EXECUTION_FAILED":"INVALID_TOOL_ARGUMENTS";
        }
        catch(TimeoutException e) { status="TIMEOUT"; error="TOOL_TIMEOUT"; }
        catch(Exception e) { status="FAILED"; error="TOOL_EXECUTION_FAILED"; }
        return new ToolCallTrace("TOOL-"+UUID.randomUUID(),analysisId,i.incidentId(),
            call.name()==null?"":call.name().substring(0,Math.min(128,call.name().length())),
            summary,status,result,error,(System.nanoTime()-started)/1_000_000,now);
    }
}
