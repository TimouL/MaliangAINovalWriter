package com.ainovel.server.service.fanqie;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 番茄小说 API 配置服务
 * 支持从远程 URL 动态加载配置，并提供配置管理功能
 */
@Slf4j
@Service
public class FanqieApiConfigService {

    private static final String DEFAULT_REMOTE_CONFIG_URL = "https://qbin.me/r/fpoash/";
    // 使用 HTTPS 版本，避免 HuggingFace 等容器环境对 HTTP 的限制
    private static final String DEFAULT_API_BASE_URL = "https://fq.shusan.cn";
    
    private static final Map<String, String> DEFAULT_ENDPOINTS = Map.of(
            "search", "/api/search",
            "detail", "/api/detail",
            "book", "/api/book",
            "directory", "/api/directory",
            "content", "/api/content",
            "chapter", "/api/chapter"
    );

    @Value("${fanqie.api.remote-config-url:" + DEFAULT_REMOTE_CONFIG_URL + "}")
    private String remoteConfigUrl;

    @Value("${fanqie.api.fallback-base-url:" + DEFAULT_API_BASE_URL + "}")
    private String fallbackBaseUrl;

    @Value("${fanqie.api.timeout:30}")
    @Getter
    private int timeout;

    @Value("${fanqie.api.enabled:true}")
    @Getter
    private boolean enabled;

    @Getter
    private String apiBaseUrl;
    
    @Getter
    private Map<String, String> endpoints = new ConcurrentHashMap<>();
    
    @Getter
    private LocalDateTime lastLoadTime;
    
    @Getter
    private boolean configLoaded = false;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FanqieApiConfigService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        loadRemoteConfig();
    }

    /**
     * 加载远程配置
     */
    @SuppressWarnings("unchecked")
    public boolean loadRemoteConfig() {
        log.info("正在加载番茄小说 API 远程配置: {}", remoteConfigUrl);
        
        try {
            String response = restTemplate.getForObject(remoteConfigUrl, String.class);
            if (response == null || response.isEmpty()) {
                throw new RuntimeException("远程配置响应为空");
            }

            Map<String, Object> jsonResponse = objectMapper.readValue(response, Map.class);
            
            if (jsonResponse.containsKey("config")) {
                Map<String, Object> config = (Map<String, Object>) jsonResponse.get("config");
                
                // 解析 API 基础 URL，优先使用 HTTPS 版本
                String remoteApiUrl = (String) config.getOrDefault("api_base_url", fallbackBaseUrl);
                // 将 HTTP URL 转换为 HTTPS（避免容器环境对 HTTP 的限制）
                this.apiBaseUrl = convertToHttpsIfNeeded(remoteApiUrl);
                
                // 解析端点配置
                Map<String, String> newEndpoints = new ConcurrentHashMap<>();
                if (config.containsKey("tomato_endpoints")) {
                    Map<String, String> remoteEndpoints = (Map<String, String>) config.get("tomato_endpoints");
                    newEndpoints.putAll(remoteEndpoints);
                } else {
                    newEndpoints.putAll(DEFAULT_ENDPOINTS);
                }
                this.endpoints = newEndpoints;
                
                this.lastLoadTime = LocalDateTime.now();
                this.configLoaded = true;
                
                log.info("番茄小说 API 配置加载成功: baseUrl={}, endpoints={}", 
                        apiBaseUrl, endpoints.keySet());
                return true;
            } else {
                throw new RuntimeException("远程配置格式错误，缺少 config 字段");
            }
        } catch (Exception e) {
            log.error("加载远程配置失败，使用默认配置: {}", e.getMessage());
            useDefaultConfig();
            return false;
        }
    }

    /**
     * 使用默认配置
     */
    private void useDefaultConfig() {
        this.apiBaseUrl = fallbackBaseUrl;
        this.endpoints = new ConcurrentHashMap<>(DEFAULT_ENDPOINTS);
        this.lastLoadTime = LocalDateTime.now();
        this.configLoaded = true;
        log.info("使用默认配置: baseUrl={}", apiBaseUrl);
    }

    /**
     * 将 HTTP URL 转换为 HTTPS（如果有已知的 HTTPS 映射）
     * 避免容器环境（如 HuggingFace）对 HTTP 请求的限制
     */
    private String convertToHttpsIfNeeded(String url) {
        if (url == null || url.isEmpty()) {
            return fallbackBaseUrl;
        }
        // 已知的 HTTP -> HTTPS 映射
        if (url.equals("http://qkfqapi.vv9v.cn")) {
            log.info("将 HTTP API 地址转换为 HTTPS: {} -> https://fq.shusan.cn", url);
            return "https://fq.shusan.cn";
        }
        // 如果已经是 HTTPS，直接返回
        if (url.startsWith("https://")) {
            return url;
        }
        // 其他 HTTP URL，尝试直接转换为 HTTPS
        if (url.startsWith("http://")) {
            String httpsUrl = url.replace("http://", "https://");
            log.info("尝试将 HTTP API 地址转换为 HTTPS: {} -> {}", url, httpsUrl);
            return httpsUrl;
        }
        return url;
    }

    /**
     * 获取指定端点路径
     */
    public String getEndpoint(String name) {
        return endpoints.getOrDefault(name, DEFAULT_ENDPOINTS.get(name));
    }

    /**
     * 获取完整的端点 URL
     */
    public String getFullUrl(String endpointName) {
        return apiBaseUrl + getEndpoint(endpointName);
    }

    /**
     * 获取远程配置 URL
     */
    public String getRemoteConfigUrl() {
        return remoteConfigUrl;
    }

    /**
     * 重新加载远程配置
     */
    public Mono<Boolean> reloadRemoteConfig() {
        return Mono.fromCallable(this::loadRemoteConfig);
    }

    /**
     * 测试 API 连接
     */
    public Mono<TestResult> testApiConnection() {
        return Mono.fromCallable(() -> {
            try {
                String testUrl = apiBaseUrl + getEndpoint("search") + "?key=test&tab_type=3";
                log.info("测试 API 连接: {}", testUrl);
                
                String response = restTemplate.getForObject(testUrl, String.class);
                if (response != null && response.contains("\"code\"")) {
                    Map<String, Object> jsonResponse = objectMapper.readValue(response, Map.class);
                    int code = (Integer) jsonResponse.getOrDefault("code", 0);
                    if (code == 200) {
                        return new TestResult(true, "API 连接正常");
                    } else {
                        return new TestResult(false, "API 返回错误码: " + code);
                    }
                }
                return new TestResult(false, "API 响应格式异常");
            } catch (Exception e) {
                log.error("API 连接测试失败: {}", e.getMessage());
                return new TestResult(false, "连接失败: " + e.getMessage());
            }
        });
    }

    /**
     * 更新配置
     */
    public Mono<Boolean> updateConfig(ConfigUpdate update) {
        return Mono.fromCallable(() -> {
            try {
                if (update.enabled() != null) {
                    // enabled 需要通过配置文件更新，这里只记录
                    log.info("enabled 配置变更需要重启生效: {}", update.enabled());
                }
                if (update.remoteConfigUrl() != null && !update.remoteConfigUrl().isEmpty()) {
                    this.remoteConfigUrl = update.remoteConfigUrl();
                }
                if (update.fallbackBaseUrl() != null && !update.fallbackBaseUrl().isEmpty()) {
                    this.fallbackBaseUrl = update.fallbackBaseUrl();
                    if (!configLoaded) {
                        this.apiBaseUrl = update.fallbackBaseUrl();
                    }
                }
                if (update.timeout() != null && update.timeout() > 0) {
                    this.timeout = update.timeout();
                }
                log.info("番茄小说配置已更新");
                return true;
            } catch (Exception e) {
                log.error("更新配置失败: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 获取配置快照
     */
    public ConfigSnapshot getConfigSnapshot() {
        return new ConfigSnapshot(
                enabled,
                remoteConfigUrl,
                apiBaseUrl,
                fallbackBaseUrl,
                Collections.unmodifiableMap(endpoints),
                timeout,
                lastLoadTime,
                configLoaded
        );
    }

    // Record 类型定义
    public record TestResult(boolean success, String message) {}
    
    public record ConfigUpdate(
            Boolean enabled,
            String remoteConfigUrl,
            String fallbackBaseUrl,
            Integer timeout
    ) {}
    
    public record ConfigSnapshot(
            boolean enabled,
            String remoteConfigUrl,
            String apiBaseUrl,
            String fallbackBaseUrl,
            Map<String, String> endpoints,
            int timeout,
            LocalDateTime lastLoadTime,
            boolean configLoaded
    ) {}
}
