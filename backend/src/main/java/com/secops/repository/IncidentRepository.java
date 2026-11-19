package com.secops.repository;
import com.secops.model.Incident;
import java.util.*;
public interface IncidentRepository {
    List<Incident> findAll();
    Optional<Incident> findById(String id);
    void save(Incident incident);
    void update(Incident incident);
}
