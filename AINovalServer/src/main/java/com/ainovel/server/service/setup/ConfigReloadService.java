package com.ainovel.server.service.setup;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ainovel.server.config.infrastructure.InfrastructureStatusManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置热更新服务
 * 支持在运行时重新加载部分配置
 */
@Service
public class ConfigReloadService {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloadService.class);
    private static final String CONFIG_FILE_PATH = "config/infrastructure.json";

    private final ObjectMapper objectMapper;
    private final InfrastructureStatusManager statusManager;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ConfigReloadService(
            ObjectMapper objectMapper,
            InfrastructureStatusManager statusManager,
            ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.statusManager = statusManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 重载 Chroma 配置
     * Chroma 支持热更新，无需重启
     */
    public boolean reloadChromaConfig() {
        try {
            JsonNode config = loadConfig();
            if (config == null || !config.has("chroma")) {
                log.warn("Chroma 配置不存在");
                return false;
            }

            JsonNode chroma = config.get("chroma");
            boolean enabled = chroma.path("enabled").asBoolean(false);
            String url = chroma.path("url").asText("");
            String authToken = chroma.path("authToken").asText("");

            // 更新系统属性
            System.setProperty("vectorstore.chroma.enabled", String.valueOf(enabled));
            if (!url.isEmpty()) {
                System.setProperty("vectorstore.chroma.url", url);
            }
            if (!authToken.isEmpty()) {
                System.setProperty("vectorstore.chroma.auth-token", authToken);
            }

            // 发布配置变更事件
            eventPublisher.publishEvent(new ConfigReloadEvent(this, "chroma", chroma));

            // 更新状态管理器
            statusManager.checkChromaConnection();

            log.info("Chroma 配置已热更新: enabled={}, url={}", enabled, url);
            return true;
        } catch (Exception e) {
            log.error("重载 Chroma 配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重载存储配置
     * 注意：OSS 凭证可以热更新，但存储路径变更需要重启
     */
    public boolean reloadStorageConfig() {
        try {
            JsonNode config = loadConfig();
            if (config == null || !config.has("storage")) {
                log.warn("存储配置不存在");
                return false;
            }

            JsonNode storage = config.get("storage");
            String provider = storage.path("provider").asText("local");

            // 更新系统属性
            System.setProperty("storage.default-provider", provider);

            if ("oss".equals(provider) && storage.has("oss")) {
                JsonNode oss = storage.get("oss");
                updatePropertyIfPresent(oss, "endpoint", "storage.aliyun.endpoint");
                updatePropertyIfPresent(oss, "accessKeyId", "storage.aliyun.access-key-id");
                updatePropertyIfPresent(oss, "accessKeySecret", "storage.aliyun.access-key-secret");
                updatePropertyIfPresent(oss, "bucketName", "storage.aliyun.bucket-name");
                updatePropertyIfPresent(oss, "region", "storage.aliyun.region");
            }

            // 发布配置变更事件
            eventPublisher.publishEvent(new ConfigReloadEvent(this, "storage", storage));

            log.info("存储配置已更新: provider={}", provider);
            return true;
        } catch (Exception e) {
            log.error("重载存储配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重载所有可热更新的配置
     */
    public void reloadAll() {
        log.info("开始重载所有配置...");
        reloadChromaConfig();
        reloadStorageConfig();
        log.info("配置重载完成");
    }

    /**
     * 加载配置文件
     */
    private JsonNode loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (!configFile.exists()) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(configFile)) {
                String content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                return objectMapper.readTree(content);
            }
        } catch (Exception e) {
            log.error("加载配置文件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新系统属性（如果配置值存在）
     */
    private void updatePropertyIfPresent(JsonNode node, String jsonKey, String propertyKey) {
        if (node.has(jsonKey)) {
            String value = node.get(jsonKey).asText();
            if (!value.isEmpty()) {
                System.setProperty(propertyKey, value);
            }
        }
    }

    /**
     * 配置重载事件
     */
    public static class ConfigReloadEvent extends org.springframework.context.ApplicationEvent {
        private final String component;
        private final JsonNode config;

        public ConfigReloadEvent(Object source, String component, JsonNode config) {
            super(source);
            this.component = component;
            this.config = config;
        }

        public String getComponent() {
            return component;
        }

        public JsonNode getConfig() {
            return config;
        }
    }
}
