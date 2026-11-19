package com.secops.controller;

import com.secops.common.ApiResponse;
import com.secops.controller.dto.IncidentDtos.*;
import com.secops.model.enums.*;
import com.secops.service.IncidentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService service;
    public IncidentController(IncidentService service) { this.service=service; }
    @GetMapping
    public ApiResponse<Page<Item>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) AlertType alertType,
            @RequestParam(required=false) RiskLevel ruleRiskLevel,@RequestParam(required=false) IncidentStatus status,
            @RequestParam(required=false) String sourceIp,@RequestParam(required=false) String targetAsset) {
        return ApiResponse.ok(service.list(new Query(page,pageSize,keyword,alertType,ruleRiskLevel,status,sourceIp,targetAsset)));
    }
    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }
}
