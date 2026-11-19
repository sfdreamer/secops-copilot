package com.secops.agent;
import com.fasterxml.jackson.databind.*;
import com.secops.common.*;
import com.secops.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AgentResultValidator {
    private final ObjectMapper mapper;
    public AgentResultValidator(ObjectMapper mapper) { this.mapper=mapper; }
    public record Result(String verdict,String summary,String attackType,String riskLevel,String reasoningSummary,
        List<AIAnalysis.Evidence> evidence,List<String> recommendations,List<String> uncertainties,String validationStatus,String executionMode) {
        public Result fallback() {
            var gaps=new ArrayList<>(uncertainties); addGap(gaps,"本次未查询工具（Function Calling 不可用，或事件缺少可绑定的来源 IP / 目标资产），仅依据已有告警与规则研判。");
            return new Result(verdict,summary,attackType,riskLevel,reasoningSummary,evidence,recommendations,List.copyOf(gaps),validationStatus,"NO_TOOL_FALLBACK");
        }
    }
    public Result validate(String json,Incident incident,String analysisId,List<ToolCallTrace> traces) {
        try {
            if(json==null || json.length()>50000) throw new IllegalArgumentException();
            JsonNode root=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(json);
            String verdict=choice(root,"verdict",Set.of("SUSPICIOUS","LIKELY_BENIGN","NEEDS_REVIEW"));
            String risk=choice(root,"riskLevel",Set.of("CRITICAL","HIGH","MEDIUM","LOW"));
            String attack=choice(root,"attackType",Set.of("SSH_BRUTE_FORCE","UNKNOWN"));
            String summary=text(root,"summary"),reason=text(root,"reasoningSummary");
            List<String> recommendations=strings(root,"recommendations"),uncertainties=new ArrayList<>(strings(root,"uncertainties"));
            JsonNode evidence=root.path("evidence"); if(!evidence.isArray() || evidence.size()>20) throw new IllegalArgumentException();
            Set<String> successful=new HashSet<>();
            traces.stream().filter(t -> analysisId.equals(t.analysisId()) && incident.incidentId().equals(t.incidentId()) && "SUCCESS".equals(t.executionStatus()))
                .forEach(t -> successful.add(t.toolCallId()));
            List<AIAnalysis.Evidence> valid=new ArrayList<>(); boolean partial=false;
            for(JsonNode e:evidence) {
                try {
                    String type=choice(e,"sourceType",Set.of("ALERT","TOOL"));
                    List<String> alerts=strings(e,"alertIds"),tools=strings(e,"toolCallIds");
                    String fact=text(e,"fact"),explanation=text(e,"explanation");
                    boolean ok=type.equals("ALERT")?!alerts.isEmpty() && tools.isEmpty() && incident.alertIds().containsAll(alerts):
                        alerts.isEmpty() && !tools.isEmpty() && successful.containsAll(tools);
                    if(!ok) throw new IllegalArgumentException();
                    valid.add(new AIAnalysis.Evidence(type,alerts,tools,fact,explanation));
                } catch(IllegalArgumentException e1) { partial=true; }
            }
            if(partial) addGap(uncertainties,"部分证据引用无效，已剔除；可追溯性校验不代表语义支持已验证。");
            if(traces.stream().anyMatch(t -> !"SUCCESS".equals(t.executionStatus()))) addGap(uncertainties,"部分工具调用失败、超时或被拒绝，存在信息缺口；失败不能视为未发现异常。");
            return new Result(verdict,summary,attack,risk,reason,List.copyOf(valid),recommendations,List.copyOf(uncertainties),partial?"PARTIALLY_VALID":"VALID","TOOL_ENABLED");
        } catch(Exception e) { throw new BizException(ErrorCode.AI_OUTPUT_INVALID,"AI 输出结构或核心字段无效"); }
    }
    private static void addGap(List<String> gaps,String gap) {
        // Preserve mandatory backend warnings while respecting the API array bound.
        if(gaps.size()>=20) gaps.remove(0);
        gaps.add(gap);
    }
    private String text(JsonNode node,String key) { JsonNode v=node.path(key); if(!v.isTextual() || v.textValue().isBlank() || v.textValue().length()>2000) throw new IllegalArgumentException(); return v.textValue(); }
    private String choice(JsonNode node,String key,Set<String> choices) { String v=text(node,key); if(!choices.contains(v)) throw new IllegalArgumentException(); return v; }
    private List<String> strings(JsonNode node,String key) {
        JsonNode v=node.path(key); if(!v.isArray() || v.size()>20) throw new IllegalArgumentException();
        List<String> result=new ArrayList<>(); for(JsonNode item:v) { if(!item.isTextual() || item.textValue().isBlank() || item.textValue().length()>2000) throw new IllegalArgumentException(); result.add(item.textValue()); }
        return List.copyOf(result);
    }
}
