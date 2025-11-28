package com.ainovel.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Chroma认证配置
 */
@Getter
@Component
public class ChromaAuthConfig {
    
    @Value("${vectorstore.chroma.auth-token:}")
    private String authToken;
    
    public boolean isAuthEnabled() {
        return authToken != null && !authToken.trim().isEmpty();
    }
}
