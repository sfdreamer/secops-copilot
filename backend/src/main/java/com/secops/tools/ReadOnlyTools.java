package com.secops.tools;

import com.fasterxml.jackson.databind.*;
import com.secops.model.Incident;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class ReadOnlyTools {
    public static final String LOGIN="query_login_history";
    public static final String AUTHORIZED="query_authorized_operations";
    public static final String AUTHORIZED_SCHEMA="""
        {"type":"object","properties":{"targetAsset":{"type":"string"},"startTime":{"type":"string","format":"date-time"},
        "endTime":{"type":"string","format":"date-time"}},
        "required":["targetAsset","startTime","endTime"],"additionalProperties":false}
        """;
    public static final String LOGIN_SCHEMA="""
        {"type":"object","properties":{"sourceIp":{"type":"string"},"targetAsset":{"type":"string"},
        "lookbackHours":{"type":"integer","minimum":1,"maximum":168}},
        "required":["sourceIp","targetAsset","lookbackHours"],"additionalProperties":false}
        """;
    private final JsonNode history;
    private final JsonNode operations;
    public ReadOnlyTools(ObjectMapper mapper) throws java.io.IOException {
        try(var in=new ClassPathResource("mock/login-history.json").getInputStream()) { history=mapper.readTree(in); }
        try(var in=new ClassPathResource("mock/authorized-operations.json").getInputStream()) { operations=mapper.readTree(in); }
    }
    public void validate(String name,JsonNode args,Incident i) {
        if(AUTHORIZED.equals(name)) {
            if(args==null || !args.isObject() || args.size()!=3 || i.targetAsset()==null
                || !args.path("targetAsset").isTextual() || !i.targetAsset().equals(args.path("targetAsset").textValue())
                || !args.path("startTime").isTextual() || !args.path("endTime").isTextual())
                throw new IllegalArgumentException("Tool or arguments rejected");
            try {
                Instant start=Instant.parse(args.get("startTime").textValue());
                Instant end=Instant.parse(args.get("endTime").textValue());
                if(start.isAfter(end) || start.isBefore(i.firstSeen().minus(5,ChronoUnit.MINUTES))
                    || end.isAfter(i.lastSeen().plus(5,ChronoUnit.MINUTES))
                    || Duration.between(start,end).compareTo(Duration.ofMinutes(30))>0)
                    throw new IllegalArgumentException("Query range rejected");
            } catch(DateTimeException e) { throw new IllegalArgumentException("Invalid timestamp",e); }
            return;
        }
        if(!LOGIN.equals(name) || args==null || !args.isObject() || args.size()!=3
            || i.sourceIp()==null || i.targetAsset()==null
            || !args.path("sourceIp").isTextual() || !args.path("targetAsset").isTextual()
            || !i.sourceIp().equals(args.path("sourceIp").textValue())
            || !i.targetAsset().equals(args.path("targetAsset").textValue())
            || !args.path("lookbackHours").isIntegralNumber() || !args.path("lookbackHours").canConvertToInt()
            || args.path("lookbackHours").intValue()<1 || args.path("lookbackHours").intValue()>168)
            throw new IllegalArgumentException("Tool or arguments rejected");
    }
    public Map<String,Object> execute(String name,JsonNode args,Incident i) {
        validate(name,args,i);
        if(AUTHORIZED.equals(name)) {
            Instant start=Instant.parse(args.get("startTime").textValue());
            Instant end=Instant.parse(args.get("endTime").textValue());
            List<Map<String,Object>> matches=new ArrayList<>();
            for(JsonNode row:operations) {
                if(!i.targetAsset().equals(row.path("targetAsset").asText())) continue;
                String status=row.path("status").asText();
                if(!"APPROVED".equals(status) && !"COMPLETED".equals(status)) continue;
                Instant opStart=Instant.parse(row.path("startTime").asText());
                Instant opEnd=Instant.parse(row.path("endTime").asText());
                if(opStart.isAfter(end) || opEnd.isBefore(start)) continue;
                // Normalize offsets so the model compares the same UTC clock used by Incident.
                matches.add(Map.of("ticketId",row.path("ticketId").asText(),"operator",row.path("operator").asText(),
                    "startTime",opStart.toString(),"endTime",opEnd.toString(),"status",status));
            }
            return Map.of("matched",!matches.isEmpty(),"operations",List.copyOf(matches));
        }
        Instant end=i.firstSeen(),start=end.minus(args.get("lookbackHours").intValue(),ChronoUnit.HOURS),last=null;
        int success=0,failed=0;
        for(JsonNode row:history) {
            if(!i.sourceIp().equals(row.path("sourceIp").asText()) || !i.targetAsset().equals(row.path("targetAsset").asText())) continue;
            Instant time=Instant.parse(row.get("timestamp").asText());
            if(time.isBefore(start) || !time.isBefore(end)) continue;
            if("LOGIN_SUCCESS".equals(row.path("action").asText())) success++; else if("LOGIN_FAILED".equals(row.path("action").asText())) failed++; else continue;
            if(last==null || time.isAfter(last)) last=time;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("totalLoginCount",success+failed); result.put("successCount",success); result.put("failedCount",failed);
        result.put("lastLoginTime",last); result.put("knownSource",success>0); result.put("hasSuccessfulLogin",success>0);
        return result;
    }
}
