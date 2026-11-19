package com.secops.service;
import com.secops.common.*;
import com.secops.controller.dto.IncidentDtos.Page;
import com.secops.model.*;
import com.secops.model.enums.IncidentStatus;
import com.secops.repository.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class ReviewService {
    public enum Result { CONFIRMED_THREAT,FALSE_POSITIVE,INVESTIGATING }
    public record Request(@NotNull Result reviewResult,@NotBlank @Size(max=100) String reviewer,@Size(max=2000) String remark) {}
    public record Response(String reviewId,String incidentId,String analysisId,String aiVerdict,String reviewResult,
        String reviewer,String remark,Instant reviewTime,String incidentStatus) {}
    private final IncidentRepository incidents; private final AIAnalysisRepository analyses; private final ReviewRepository reviews;
    public ReviewService(IncidentRepository incidents,AIAnalysisRepository analyses,ReviewRepository reviews) {
        this.incidents=incidents; this.analyses=analyses; this.reviews=reviews;
    }
    public Response review(String id,Request request) {
        synchronized(incidents) {
            Incident i=incidents.findById(id).orElseThrow(() -> new BizException(ErrorCode.INCIDENT_NOT_FOUND,"事件不存在"));
            if(!Set.of(IncidentStatus.PENDING_ANALYSIS,IncidentStatus.PENDING_REVIEW,IncidentStatus.ANALYSIS_FAILED,IncidentStatus.INVESTIGATING).contains(i.status()))
                throw new BizException(ErrorCode.INVALID_INCIDENT_STATUS,"当前状态不允许审核");
            AIAnalysis latest=analyses.findByIncident(id).stream().filter(a -> "SUCCEEDED".equals(a.analysisStatus())).reduce((a,b)->b).orElse(null);
            var r=new ReviewRecord("REV-"+UUID.randomUUID(),id,latest==null?null:latest.analysisId(),latest==null?null:latest.verdict(),
                request.reviewResult().name(),request.reviewer(),request.remark(),Instant.now());
            reviews.save(r); incidents.update(i.withStatus(i.analysisStatus(),IncidentStatus.valueOf(r.reviewResult())));
            return new Response(r.reviewId(),id,r.analysisId(),r.aiVerdict(),r.reviewResult(),r.reviewer(),r.remark(),r.reviewTime(),r.reviewResult());
        }
    }
    public Page<ReviewRecord> list(int page,int pageSize,String incidentId,Result result) {
        if(page<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("Invalid pagination");
        var matches=reviews.findAll().stream().filter(r -> incidentId==null || incidentId.equals(r.incidentId()))
            .filter(r -> result==null || result.name().equals(r.reviewResult())).sorted(Comparator.comparing(ReviewRecord::reviewTime).reversed()).toList();
        return new Page<>(matches.size(),page,pageSize,matches.stream().skip(((long)page-1)*pageSize).limit(pageSize).toList());
    }
}
