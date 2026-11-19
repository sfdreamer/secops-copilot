package com.secops.controller;

import com.secops.common.ApiResponse;
import com.secops.service.DashboardService;
import com.secops.service.DashboardService.Dashboard;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service=service; }

    @GetMapping
    public ApiResponse<Dashboard> overview() { return ApiResponse.ok(service.overview()); }
}
