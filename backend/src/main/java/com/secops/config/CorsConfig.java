package com.secops.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地前后端分离调试 + 公网隧道演示用 CORS。
 *
 * <p>注意：浏览器对非简单请求（POST）即使同源也会携带 Origin 头。经 Cloudflare 等
 * 隧道用 https 域名访问时，若 Origin 不在白名单，Spring 的 CORS 处理器会在进入
 * Controller 之前直接返回 403 "Invalid CORS request"，且不产生业务日志。
 * 本项目无 Cookie / 凭据认证，故放行本机任意端口与任意 https 来源。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "https://*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
