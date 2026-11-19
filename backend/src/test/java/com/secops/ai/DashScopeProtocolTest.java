package com.secops.ai;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real SDK request/response serialization with simulated HTTP, not a live model test. */
class DashScopeProtocolTest {
    @Test void ordinaryAndFunctionCallingProtocol() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var model=DashScopeChatModel.builder().dashScopeApi(DashScopeApi.builder().apiKey("test-placeholder").restClientBuilder(builder).build())
            .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-plus").withInternalToolExecutionEnabled(false).build()).build();
        var adapter=new DashScopeAdapter("qwen-plus",model);
        server.expect(jsonPath("$.input.messages[0].role").value("user"))
            .andRespond(withSuccess(response("{\"role\":\"assistant\",\"content\":\"OK\"}","stop"),MediaType.APPLICATION_JSON));
        assertEquals("OK",adapter.complete(List.of(LlmAdapter.Turn.text("user","ping")),false).text());
        server.verify(); server.reset();
        server.expect(jsonPath("$.parameters.tools[0].function.name").value("query_login_history"))
            .andRespond(withSuccess(response("""
                {"role":"assistant","content":"","tool_calls":[{"id":"vendor-call","type":"function",
                "function":{"name":"query_login_history","arguments":"{\"sourceIp\":\"ip\"}"}}]}
                """.replace("{\"sourceIp\":\"ip\"}","{\\\"sourceIp\\\":\\\"ip\\\"}"),"tool_calls"),MediaType.APPLICATION_JSON));
        var reply=adapter.complete(List.of(LlmAdapter.Turn.text("user","query")),true);
        assertEquals(1,reply.calls().size()); assertEquals("vendor-call",reply.calls().get(0).id());
        server.verify(); server.reset();
        server.expect(jsonPath("$.input.messages[2].role").value("tool"))
            .andExpect(jsonPath("$.input.messages[2].tool_call_id").value("vendor-call"))
            .andRespond(withSuccess(response("{\"role\":\"assistant\",\"content\":\"done\"}","stop"),MediaType.APPLICATION_JSON));
        var answer=adapter.complete(List.of(LlmAdapter.Turn.text("user","query"),
            new LlmAdapter.Turn("assistant","",reply.calls(),null,null),
            new LlmAdapter.Turn("tool","{\"status\":\"SUCCESS\"}",List.of(),"vendor-call","query_login_history")),false);
        assertEquals("done",answer.text()); server.verify();
    }
    private String response(String message,String reason) {
        return "{\"request_id\":\"test\",\"output\":{\"choices\":[{\"finish_reason\":\""+reason+"\",\"message\":"+message+"}]},\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}";
    }
}
