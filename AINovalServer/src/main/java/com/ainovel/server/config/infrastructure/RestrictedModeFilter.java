package com.ainovel.server.config.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * 受限模式过滤器
 * 当系统处于受限模式时，拦截大部分 API 请求并返回 503
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RestrictedModeFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RestrictedModeFilter.class);

    private final InfrastructureStatusManager statusManager;

    // 受限模式下允许访问的路径前缀
    private static final Set<String> ALLOWED_PATHS = Set.of(
        "/api/setup",           // 配置向导
        "/api/system/status",   // 系统状态
        "/api/v1/auth",         // 认证接口（用于管理员登录）
        "/api/v1/admin/auth",   // 管理员认证
        "/actuator",            // 健康检查
        "/assets",              // 静态资源
        "/admin",               // 管理后台静态资源
        "/icons",
        "/canvaskit",
        "/fonts"
    );

    // 静态资源扩展名
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
        ".html", ".js", ".css", ".ico", ".png", ".jpg", ".jpeg", 
        ".gif", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".json"
    );

    public RestrictedModeFilter(InfrastructureStatusManager statusManager) {
        this.statusManager = statusManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果不是受限模式，直接放行
        if (!statusManager.isRestrictedMode()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // 检查是否是允许的路径
        if (isAllowedPath(path)) {
            return chain.filter(exchange);
        }

        // 检查是否是静态资源
        if (isStaticResource(path)) {
            return chain.filter(exchange);
        }

        // 根路径放行（显示"即将上线"页面由前端处理）
        if ("/".equals(path) || "/index.html".equals(path)) {
            return chain.filter(exchange);
        }

        // 受限模式下，API 请求返回 503
        if (path.startsWith("/api/")) {
            log.debug("受限模式：拦截请求 {}", path);
            return writeRestrictedResponse(exchange);
        }

        // 其他请求放行（前端路由等）
        return chain.filter(exchange);
    }

    /**
     * 检查是否是允许的路径
     */
    private boolean isAllowedPath(String path) {
        for (String allowed : ALLOWED_PATHS) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是静态资源
     */
    private boolean isStaticResource(String path) {
        String lowerPath = path.toLowerCase();
        for (String ext : STATIC_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入受限模式响应
     */
    private Mono<Void> writeRestrictedResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String responseBody = """
            {
                "error": "SERVICE_UNAVAILABLE",
                "message": "系统正在配置中，请访问 /setup 完成初始化",
                "setupRequired": true,
                "code": 503
            }
            """;
        
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }
}
