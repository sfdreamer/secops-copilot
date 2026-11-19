package com.secops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 模型密钥的唯一来源：只保存环境变量「名称」，绝不保存、打印或把密钥值返回到响应体 / 日志 / 前端。
 * 配置项 app.model.api-key-env 支持逗号分隔的候选名（按优先级），用于兼容历史环境变量名。
 * 目前主名为 SECOPS_API_KEY（硅基流动 / 轨迹流动平台），SECOPS_COPILOT_API_KEY 为兼容别名。
 */
@Component
public class ModelKeyProvider {

    private final List<String> envNames;

    public ModelKeyProvider(@Value("${app.model.api-key-env:SECOPS_API_KEY}") String raw) {
        this.envNames = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 仅供 AI 适配层在后端进程内使用；调用方不得把返回值写入响应、日志或异常消息。 */
    public String apiKey() {
        for (String name : envNames) {
            String value = System.getenv(name);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey());
    }
}
