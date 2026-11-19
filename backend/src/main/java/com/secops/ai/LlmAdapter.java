package com.secops.ai;

import java.util.List;

/** SDK-independent conversation protocol. Only the adapter knows Spring AI types. */
public interface LlmAdapter {
    class ToolsUnavailable extends RuntimeException {}
    record Call(String id, String name, String arguments) {}
    record Reply(String text, List<Call> calls) {}
    record Turn(String role, String text, List<Call> calls, String callId, String toolName) {
        public static Turn text(String role,String text) { return new Turn(role,text,List.of(),null,null); }
    }
    Reply complete(List<Turn> turns, boolean toolsEnabled);
    default String modelName() { return "mock"; }
}
