package com.ainovel.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 番茄小说服务配置（直连第三方API模式）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "fanqie.api")
public class FanqieNovelConfig {
    
    /**
     * 是否启用番茄小说服务
     */
    private boolean enabled = true;
    
    /**
     * 远程配置URL（用于动态获取API地址）
     */
    private String remoteConfigUrl = "https://qbin.me/r/fpoash/";
    
    /**
     * 备用API基础URL（远程配置加载失败时使用）
     */
    private String fallbackBaseUrl = "http://qkfqapi.vv9v.cn";
    
    /**
     * 请求超时时间（秒）
     */
    private int timeout = 30;
}

