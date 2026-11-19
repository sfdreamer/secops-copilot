package com.secops.engine;

import com.secops.model.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class SecurityFeatureExtractor {
    public SecurityFeatures extract(List<Alert> alerts, AssetProfile asset) {
        if (alerts.isEmpty()) throw new IllegalArgumentException("Features require alerts");
        int failed=0, successful=0;
        Instant first=null, last=null, firstFailure=null, lastSuccess=null;
        Set<String> users = new HashSet<>();
        for (Alert a : alerts) {
            if (first==null || a.timestamp().isBefore(first)) first=a.timestamp();
            if (last==null || a.timestamp().isAfter(last)) last=a.timestamp();
            String action=SshAlertAggregator.attribute(a,"action");
            if ("LOGIN_FAILED".equals(action)) {
                failed++; if (firstFailure==null || a.timestamp().isBefore(firstFailure)) firstFailure=a.timestamp();
            } else if ("LOGIN_SUCCESS".equals(action)) {
                successful++; if (lastSuccess==null || a.timestamp().isAfter(lastSuccess)) lastSuccess=a.timestamp();
            }
            String user=SshAlertAggregator.attribute(a,"username");
            if (user!=null) users.add(user);
        }
        long seconds=Duration.between(first,last).getSeconds();
        return new SecurityFeatures(failed,successful,users.size(),seconds,seconds/60.0,
                failed/Math.max(seconds/60.0,1/60.0),firstFailure!=null && lastSuccess!=null && lastSuccess.isAfter(firstFailure),
                asset==null?null:asset.publicExposure(),asset==null || asset.assetCriticality()==null?"UNKNOWN":asset.assetCriticality());
    }
}
