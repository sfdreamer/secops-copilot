package com.secops.repository;

import com.secops.model.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<String,Incident> store=new LinkedHashMap<>();
    private final Map<String,String> owners=new HashMap<>();
    private final AlertRepository alerts;
    public InMemoryIncidentRepository(AlertRepository alerts) { this.alerts=alerts; }
    public synchronized List<Incident> findAll() { return List.copyOf(store.values()); }
    public synchronized Optional<Incident> findById(String id) { return Optional.ofNullable(store.get(id)); }
    public synchronized void save(Incident incident) {
        if (store.containsKey(incident.incidentId())) throw new IllegalArgumentException("Incident already exists");
        validate(incident);
        store.put(incident.incidentId(),incident);
        incident.alertIds().forEach(id -> owners.put(id,incident.incidentId()));
    }
    public synchronized void update(Incident incident) {
        Incident old=store.get(incident.incidentId());
        if (old==null || !old.alertIds().equals(incident.alertIds())) throw new IllegalArgumentException("Cannot change incident membership");
        validate(incident); store.put(incident.incidentId(),incident);
    }
    private void validate(Incident incident) {
        List<Alert> related=new ArrayList<>();
        for (String id:incident.alertIds()) {
            Alert a=alerts.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown alert"));
            if (!a.alertType().equals(incident.alertType())) throw new IllegalArgumentException("Mixed alert types");
            String owner=owners.get(id);
            if (owner!=null && !owner.equals(incident.incidentId())) throw new IllegalArgumentException("Alert already belongs to another incident");
            related.add(a);
        }
        if (!incident.firstSeen().equals(related.stream().map(Alert::timestamp).min(Comparator.naturalOrder()).orElseThrow())
                || !incident.lastSeen().equals(related.stream().map(Alert::timestamp).max(Comparator.naturalOrder()).orElseThrow()))
            throw new IllegalArgumentException("Incident time range does not match alerts");
    }
}
