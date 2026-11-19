package com.secops.repository;
import com.secops.model.AIAnalysis;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class AIAnalysisRepository {
 private final Map<String,AIAnalysis> store=new LinkedHashMap<>();
 public synchronized void save(AIAnalysis value) { if(store.putIfAbsent(value.analysisId(),value)!=null) throw new IllegalArgumentException("Duplicate ID"); }
 public synchronized List<AIAnalysis> findByIncident(String id) { return store.values().stream().filter(v -> v.incidentId().equals(id)).toList(); }
 public synchronized List<AIAnalysis> findAll() { return List.copyOf(store.values()); }
}
