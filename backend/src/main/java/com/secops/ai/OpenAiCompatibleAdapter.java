package com.secops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secops.common.BizException;
import com.secops.common.ErrorCode;
import com.secops.config.ModelKeyProvider;
import com.secops.tools.ReadOnlyTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.*;

/**
 * OpenAI 兼容协议适配器（硅基流动 / 轨迹流动等 OpenAI-compatible 平台）。
 * 与 DashScopeAdapter 二选一，由 app.model.provider 切换；模型密钥仍只从后端环境变量读取。
 * 协议细节集中在本适配层，Agent / Tool / Service 不感知具体平台。
 */
@Component
@ConditionalOnProperty(name = "app.model.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    // 与 app.agent.total-timeout-ms=45000 匹配；单轮模型读超时留 40s，避免慢响应先于 Agent 总超时触发。
    private static final int READ_TIMEOUT_MS = 40000;

    private final String name;
    private final String baseUrl;
    private final ModelKeyProvider keys;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Value("${app.model.function-calling-enabled:true}")
    private boolean functionCallingEnabled = true;

    public OpenAiCompatibleAdapter(
            @Value("${app.model.name:Qwen/Qwen2.5-72B-Instruct}") String name,
            @Value("${app.model.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            ObjectMapper mapper,
            ModelKeyProvider keys) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.keys = keys;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String modelName() { return name; }

    @Override
    public Reply complete(List<Turn> turns, boolean toolsEnabled) {
        if (toolsEnabled && !functionCallingEnabled) throw new ToolsUnavailable();
        String key = keys.apiKey();
        if (key == null || key.isBlank()) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型密钥未配置");
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", name);
            body.put("messages", buildMessages(turns));
            body.put("temperature", 0.1);
            body.put("max_tokens", 2500);
            body.put("stream", false);
            if (toolsEnabled) {
                body.put("tools", List.of(
                    Map.of("type", "function", "function", Map.of("name", ReadOnlyTools.LOGIN,
                        "description", "查询当前事件发生前的历史登录；成功记录不代表来源可信", "parameters", mapper.readTree(ReadOnlyTools.LOGIN_SCHEMA))),
                    Map.of("type", "function", "function", Map.of("name", ReadOnlyTools.AUTHORIZED,
                        "description", "查询当前事件附近已批准或已完成的运维记录；时间需在事件前后5分钟内", "parameters", mapper.readTree(ReadOnlyTools.AUTHORIZED_SCHEMA)))));
                body.put("tool_choice", "auto");
            }
            String raw = client.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return parse(raw);
        } catch (BizException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("模型服务返回 HTTP {}；响应摘要：{}", e.getStatusCode().value(), safeError(e.getResponseBodyAsString()));
            throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型服务调用失败");
        } catch (RestClientException | IOException e) {
            log.warn("模型服务调用异常：{}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型服务调用失败");
        } catch (Exception e) {
            log.warn("模型服务调用异常：{}", e.getClass().getSimpleName());
            throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型服务调用失败");
        }
    }

    private List<Map<String, Object>> buildMessages(List<Turn> turns) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Turn t : turns) {
            switch (t.role()) {
                case "system", "user" -> messages.add(Map.of("role", t.role(), "content", t.text() == null ? "" : t.text()));
                case "assistant" -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", "assistant");
                    m.put("content", t.text() == null ? "" : t.text());
                    if (t.calls() != null && !t.calls().isEmpty()) {
                        List<Map<String, Object>> toolCalls = new ArrayList<>();
                        for (Call c : t.calls()) {
                            toolCalls.add(Map.of("id", c.id(), "type", "function",
                                    "function", Map.of("name", c.name(), "arguments", c.arguments() == null ? "{}" : c.arguments())));
                        }
                        m.put("tool_calls", toolCalls);
                    }
                    messages.add(m);
                }
                case "tool" -> messages.add(Map.of("role", "tool",
                        "tool_call_id", t.callId() == null ? "" : t.callId(),
                        "content", t.text() == null ? "" : t.text()));
                default -> throw new IllegalArgumentException("Unknown role");
            }
        }
        return messages;
    }

    private Reply parse(String raw) throws IOException {
        if (raw == null || raw.isBlank()) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型服务返回空响应");
        JsonNode root = mapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型服务返回无效响应");
        JsonNode message = choices.get(0).path("message");
        String text = message.path("content").isTextual() ? message.path("content").asText() : null;
        List<Call> calls = new ArrayList<>();
        for (JsonNode tc : message.path("tool_calls")) {
            JsonNode fn = tc.path("function");
            String id = tc.path("id").asText(null);
            String fnName = fn.path("name").asText(null);
            String args = fn.path("arguments").asText("{}");
            if (id == null || fnName == null) throw new BizException(ErrorCode.MODEL_SERVICE_ERROR, "模型返回无效工具协议");
            calls.add(new Call(id, fnName, args));
        }
        return new Reply(text, calls);
    }

    /** 只保留错误码与简短消息；绝不输出密钥、完整 Prompt、请求体或堆栈。 */
    private String safeError(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode err = mapper.readTree(body).path("error");
            String code = err.path("code").asText("");
            String msg = err.path("message").asText("");
            String brief = msg.length() > 120 ? msg.substring(0, 120) : msg;
            return (code.isEmpty() ? "" : "code=" + code + " ") + brief;
        } catch (Exception e) {
            return body.length() > 120 ? body.substring(0, 120) : body;
        }
    }
}
