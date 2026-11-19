package com.secops.controller;
import com.secops.common.ApiResponse;
import com.secops.controller.dto.IncidentDtos.Page;
import com.secops.model.ReviewRecord;
import com.secops.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class TriageController {
    private final AnalysisService analysis; private final ReviewService review;
    public TriageController(AnalysisService analysis,ReviewService review) { this.analysis=analysis; this.review=review; }
    @PostMapping("/incidents/{id}/analyze")
    public ApiResponse<AnalysisService.Response> analyze(@PathVariable String id) { return ApiResponse.ok("analysis completed",analysis.analyze(id)); }
    @PostMapping("/incidents/{id}/review")
    public ApiResponse<ReviewService.Response> review(@PathVariable String id,@Valid @RequestBody ReviewService.Request request) {
        return ApiResponse.ok("review submitted",review.review(id,request));
    }
    @GetMapping("/reviews")
    public ApiResponse<Page<ReviewRecord>> reviews(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
        @RequestParam(required=false) String incidentId,@RequestParam(required=false) ReviewService.Result reviewResult) {
        return ApiResponse.ok(review.list(page,pageSize,incidentId,reviewResult));
    }
}
