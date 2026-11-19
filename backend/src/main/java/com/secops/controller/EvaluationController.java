package com.secops.controller;

import com.secops.common.ApiResponse;
import com.secops.evaluation.EvaluationService;
import com.secops.evaluation.EvaluationService.Result;
import org.springframework.web.bind.annotation.*;

/** 评估接口只读取实际运行产物与隔离的 Ground Truth，不触发模型调用（API.md 13 节）。 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service) { this.service=service; }

    @GetMapping
    public ApiResponse<Result> evaluate() { return ApiResponse.ok(service.evaluate()); }
}
