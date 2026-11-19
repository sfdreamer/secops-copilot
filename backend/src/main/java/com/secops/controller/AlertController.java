package com.secops.controller;

import com.secops.common.ApiResponse;
import com.secops.controller.dto.AlertDtos.*;
import com.secops.controller.dto.IncidentDtos.Page;
import com.secops.model.enums.*;
import com.secops.service.AlertService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;
    public AlertController(AlertService service) { this.service=service; }

    @GetMapping
    public ApiResponse<Page<Item>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) AlertType alertType,
            @RequestParam(required=false) AlertCategory category,@RequestParam(required=false) Severity severity,
            @RequestParam(required=false) String sourceIp,@RequestParam(required=false) String targetAsset) {
        return ApiResponse.ok(service.list(new Query(page,pageSize,keyword,alertType,category,severity,sourceIp,targetAsset)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }
}
