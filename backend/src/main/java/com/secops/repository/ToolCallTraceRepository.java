package com.secops.repository;
import com.secops.model.ToolCallTrace;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ToolCallTraceRepository {
 private final Map<String,ToolCallTrace> store=new LinkedHashMap<>();
 public synchronized void save(ToolCallTrace value) { if(store.putIfAbsent(value.toolCallId(),value)!=null) throw new IllegalArgumentException("Duplicate ID"); }
 public synchronized List<ToolCallTrace> findByIncident(String id) { return store.values().stream().filter(v -> v.incidentId().equals(id)).toList(); }
 public synchronized List<ToolCallTrace> findAll() { return List.copyOf(store.values()); }
}
