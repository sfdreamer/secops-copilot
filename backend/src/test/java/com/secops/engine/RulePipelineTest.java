package com.secops.engine;

import com.secops.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RulePipelineTest {
    static final Instant START = Instant.parse("2026-11-18T00:00:00Z");
    static Alert alert(String id, long seconds, String ip, String asset, String protocol, String action, String user) {
        Map<String,Object> attrs = new HashMap<>();
        if (protocol != null) attrs.put("protocol", protocol);
        if (action != null) attrs.put("action", action);
        if (user != null) attrs.put("username", user);
        return new Alert(id, "ignored-" + id, "SSH_BRUTE_FORCE", "AUTHENTICATION", START.plusSeconds(seconds), ip,
                "10.0.0.1", asset, "HIGH", "TEST", attrs, null);
    }
    static Alert a(String id, long seconds) { return alert(id, seconds, "ip", "asset", "SSH", "LOGIN_FAILED", "root"); }

    @Test void fixedWindowInclusiveAndNotSliding() {
        var groups = new SshAlertAggregator().aggregate(List.of(a("d", 600),a("b",300),a("a",0),a("c",301)));
        assertEquals(List.of(List.of("a","b"),List.of("c","d")), groups.stream().map(g -> g.stream().map(Alert::alertId).toList()).toList());
    }
    @Test void fullPrecisionBoundary() {
        Alert b = a("b",300);
        b = new Alert(b.alertId(),null,b.alertType(),b.category(),b.timestamp().plusNanos(1),b.sourceIp(),b.targetIp(),b.targetAsset(),b.severity(),null,b.attributes(),null);
        assertEquals(2,new SshAlertAggregator().aggregate(List.of(a("a",0),b)).size());
    }
    @Test void keysMissingValuesDuplicatesAndUnsupported() {
        List<Alert> input = new ArrayList<>(List.of(a("a",0),a("a",1),a("b",2),
                alert("c",0,"other","asset","SSH",null,null),alert("d",0,"ip","other","SSH",null,null),
                alert("e",0,"ip","asset","OTHER",null,null)));
        for (int i=0;i<2;i++) {
            input.add(alert("ip"+i,0,null,"asset","SSH",null,null));
            input.add(alert("asset"+i,0,"ip"," ","SSH",null,null));
            input.add(alert("protocol"+i,0,"ip","asset",null,null,null));
        }
        input.add(new Alert("port",null,"PORT_SCAN","NETWORK",START,"ip",null,"asset","HIGH",null,Map.of("protocol","SSH"),null));
        var groups = new SshAlertAggregator().aggregate(input);
        assertEquals(10,groups.size());
        var ids = groups.stream().flatMap(Collection::stream).map(Alert::alertId).toList();
        assertEquals(11,ids.size()); assertEquals(ids.size(),new HashSet<>(ids).size()); assertFalse(ids.contains("port"));
        assertEquals(0,new SshAlertAggregator().aggregate(List.of()).size());
    }
    @Test void featuresUseChronologyAndUnknownAssets() {
        var extractor = new SecurityFeatureExtractor();
        var result = extractor.extract(List.of(alert("c",60,"ip","asset","SSH","LOGIN_SUCCESS","admin"),a("b",30),a("a",0)), null);
        assertEquals(2,result.failedAttempts()); assertEquals(1,result.successfulAttempts());
        assertEquals(2,result.distinctUserCount()); assertEquals(60,result.windowSeconds());
        assertEquals(1,result.windowMinutes()); assertEquals(2,result.failureRate());
        assertTrue(result.successAfterFailure()); assertNull(result.publicExposure()); assertEquals("UNKNOWN",result.assetCriticality());
        assertEquals(60,extractor.extract(List.of(a("a",0)),null).failureRate());
        assertFalse(extractor.extract(List.of(a("a",0),alert("b",0,"ip","asset","SSH","LOGIN_SUCCESS",null)),null).successAfterFailure());
        assertFalse(extractor.extract(List.of(alert("b",0,"ip","asset","SSH","LOGIN_SUCCESS",null),a("a",1)),null).successAfterFailure());
        var asset = new AssetProfile("id","asset","ip",true,"HIGH",null);
        var known = extractor.extract(List.of(a("a",0)),asset);
        assertEquals(true,known.publicExposure()); assertEquals("HIGH",known.assetCriticality());
        var unknownAction = extractor.extract(List.of(alert("u",0,"ip","asset","SSH",null," ")),null);
        assertEquals(0,unknownAction.failedAttempts()); assertEquals(0,unknownAction.successfulAttempts()); assertEquals(0,unknownAction.distinctUserCount());
    }
    @ParameterizedTest @CsvSource({"0,0","9,0","10,15","29,15","30,30","99,30","100,45","1000,45"})
    void failureBandsAreExclusive(int failures,int expected) {
        var r = new RiskScoringEngine().assess(new SecurityFeatures(failures,0,0,0,0,0,false,null,"UNKNOWN"));
        assertEquals(expected,r.score()); assertEquals(expected==0?0:1,r.matchedRules().size());
    }
    @ParameterizedTest @CsvSource({"0,LOW","29,LOW","30,MEDIUM","49,MEDIUM","50,HIGH","79,HIGH","80,CRITICAL","100,CRITICAL"})
    void levelBoundaries(int score,String expected) { assertEquals(expected,RiskScoringEngine.levelFor(score).name()); }
    @Test void allRulesHaveEvidenceAndScoreIsCapped() {
        var r = new RiskScoringEngine().assess(new SecurityFeatures(100,1,5,60,1,100,true,true,"HIGH"));
        assertEquals(95,r.score()); assertEquals(4,r.matchedRules().size());
        assertTrue(r.matchedRules().stream().allMatch(m -> !m.supportingFacts().isEmpty()));
        assertEquals(100,RiskScoringEngine.capScore(120));
        assertEquals(0,new RiskScoringEngine().assess(new SecurityFeatures(0,0,4,0,0,0,false,false,"HIGH")).score());
    }
}
