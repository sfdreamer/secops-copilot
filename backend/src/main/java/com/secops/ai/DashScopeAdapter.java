package com.secops.ai;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.*;
import com.secops.common.*;
import com.secops.config.ModelKeyProvider;
import com.secops.tools.ReadOnlyTools;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
@ConditionalOnProperty(name = "app.model.provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeAdapter implements LlmAdapter {
    private final String name;
    private volatile DashScopeChatModel model;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ModelKeyProvider keys;
    @Value("${app.model.function-calling-enabled:true}")
    private boolean functionCallingEnabled=true;
    @org.springframework.beans.factory.annotation.Autowired
    public DashScopeAdapter(@Value("${app.model.name:qwen-plus}") String name) { this.name=name; }
    DashScopeAdapter(String name,DashScopeChatModel model) { this.name=name; this.model=model; }
    public String modelName() { return name; }
    private synchronized DashScopeChatModel model() {
        if(model!=null) return model;
        String key=keys!=null?keys.apiKey():null;
        if(key==null || key.isBlank()) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR,"模型密钥未配置");
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(40000);
        model=DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(key)
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build())
            .defaultOptions(DashScopeChatOptions.builder().withModel(name).withTemperature(0.1)
                .withMaxToken(2500).withInternalToolExecutionEnabled(false).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
        return model;
    }
    public Reply complete(List<Turn> turns,boolean toolsEnabled) {
        if(toolsEnabled && !functionCallingEnabled) throw new ToolsUnavailable();
        try {
            List<Message> messages=new ArrayList<>();
            for(Turn t:turns) switch(t.role()) {
                case "system" -> messages.add(new SystemMessage(t.text()));
                case "user" -> messages.add(new UserMessage(t.text()));
                case "assistant" -> messages.add(new AssistantMessage(t.text()==null?"":t.text(),Map.of(),
                    t.calls().stream().map(c -> new AssistantMessage.ToolCall(c.id(),"function",c.name(),c.arguments())).toList()));
                case "tool" -> messages.add(new ToolResponseMessage(List.of(
                    new ToolResponseMessage.ToolResponse(t.callId(),t.toolName(),t.text()))));
                default -> throw new IllegalArgumentException("Unknown role");
            }
            var options=DashScopeChatOptions.builder().withModel(name).withInternalToolExecutionEnabled(false).build();
            if(toolsEnabled) options.setToolCallbacks(List.of(
                definition(ReadOnlyTools.LOGIN,"查询当前事件发生前的历史登录；成功记录不代表来源可信",ReadOnlyTools.LOGIN_SCHEMA),
                definition(ReadOnlyTools.AUTHORIZED,"查询当前事件附近已批准或已完成的运维记录",ReadOnlyTools.AUTHORIZED_SCHEMA)));
            var output=model().call(new Prompt(messages,options)).getResult().getOutput();
            return new Reply(output.getText(),output.getToolCalls().stream()
                .map(c -> new Call(c.id(),c.name(),c.arguments())).toList());
        } catch(BizException e) { throw e; }
        catch(Exception e) { throw new BizException(ErrorCode.MODEL_SERVICE_ERROR,"模型服务调用失败"); }
    }
    private ToolCallback definition(String name,String description,String schema) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
            }
            public String call(String input) { throw new IllegalStateException("Only the bounded executor may execute tools"); }
        };
    }
}
