package com.secops.repository;
import com.secops.model.ReviewRecord;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ReviewRepository {
 private final Map<String,ReviewRecord> store=new LinkedHashMap<>();
 public synchronized void save(ReviewRecord value) { if(store.putIfAbsent(value.reviewId(),value)!=null) throw new IllegalArgumentException("Duplicate ID"); }
 public synchronized List<ReviewRecord> findByIncident(String id) { return store.values().stream().filter(v -> v.incidentId().equals(id)).toList(); }
 public synchronized List<ReviewRecord> findAll() { return List.copyOf(store.values()); }
}
