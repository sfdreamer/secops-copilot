package com.secops.controller;

import com.secops.common.ApiResponse;
import com.secops.config.ModelKeyProvider;
import com.secops.repository.AlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API.md 第 4 节健康检查。不返回密钥或环境变量原文。 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final AlertRepository alertRepository;
    private final ModelKeyProvider modelKeyProvider;

    public HealthController(AlertRepository alertRepository, ModelKeyProvider modelKeyProvider) {
        this.alertRepository = alertRepository;
        this.modelKeyProvider = modelKeyProvider;
    }

    @GetMapping("/health")
    public ApiResponse<HealthDto> health() {
        return ApiResponse.ok(new HealthDto(
                "UP",
                modelKeyProvider.isConfigured(),
                alertRepository.count() > 0
        ));
    }

    public record HealthDto(String status, boolean modelConfigured, boolean mockDataLoaded) {
    }
}
