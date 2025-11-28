package com.ainovel.server.config.infrastructure;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 基础设施配置加载器
 * 在 Spring 容器初始化之前加载 config/infrastructure.json 配置
 * 将配置值设置为系统属性，供 Spring 读取
 * 
 * 配置优先级：环境变量 > infrastructure.json > application.yml
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InfrastructureConfigLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureConfigLoader.class);
    
    private static final String CONFIG_FILE_PATH = "config/infrastructure.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        log.info("开始加载基础设施配置...");
        
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) {
            log.info("基础设施配置文件不存在: {}，将使用默认配置并进入向导模式", CONFIG_FILE_PATH);
            return;
        }

        try (InputStream is = new FileInputStream(configFile)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode config = objectMapper.readTree(content);
            
            ConfigurableEnvironment env = applicationContext.getEnvironment();
            
            // 加载 MongoDB 配置
            loadMongoConfig(config, env);
            
            // 加载存储配置
            loadStorageConfig(config, env);
            
            // 加载 Chroma 配置
            loadChromaConfig(config, env);
            
            log.info("基础设施配置加载完成");
            
        } catch (Exception e) {
            log.error("加载基础设施配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载 MongoDB 配置
     */
    private void loadMongoConfig(JsonNode config, ConfigurableEnvironment env) {
        if (!config.has("mongodb")) {
            return;
        }
        
        JsonNode mongodb = config.get("mongodb");
        
        // 只有在环境变量未设置时才使用配置文件的值
        if (mongodb.has("uri") && !mongodb.get("uri").asText().isEmpty()) {
            String envUri = env.getProperty("SPRING_DATA_MONGODB_URI");
            if (envUri == null || envUri.isEmpty()) {
                String uri = mongodb.get("uri").asText();
                System.setProperty("spring.data.mongodb.uri", uri);
                log.info("从配置文件加载 MongoDB URI");
            } else {
                log.info("使用环境变量中的 MongoDB URI");
            }
        }
        
        // 数据库名称
        if (mongodb.has("database") && !mongodb.get("database").asText().isEmpty()) {
            String envDb = env.getProperty("MONGO_DATABASE");
            if (envDb == null || envDb.isEmpty()) {
                System.setProperty("spring.data.mongodb.database", mongodb.get("database").asText());
            }
        }
    }

    /**
     * 加载存储配置
     */
    private void loadStorageConfig(JsonNode config, ConfigurableEnvironment env) {
        if (!config.has("storage")) {
            return;
        }
        
        JsonNode storage = config.get("storage");
        
        // 存储提供者
        if (storage.has("provider")) {
            String envProvider = env.getProperty("STORAGE_PROVIDER");
            if (envProvider == null || envProvider.isEmpty()) {
                System.setProperty("storage.default-provider", storage.get("provider").asText("local"));
                log.info("从配置文件加载存储提供者: {}", storage.get("provider").asText());
            }
        }
        
        // 本地存储路径
        if (storage.has("local") && storage.get("local").has("basePath")) {
            String basePath = storage.get("local").get("basePath").asText();
            if (!basePath.isEmpty()) {
                System.setProperty("storage.local.base-path", basePath);
            }
        }
        
        // OSS 配置
        if (storage.has("oss")) {
            JsonNode oss = storage.get("oss");
            setPropertyIfNotEmpty(oss, "endpoint", "storage.aliyun.endpoint", env, "OSS_ENDPOINT");
            setPropertyIfNotEmpty(oss, "accessKeyId", "storage.aliyun.access-key-id", env, "OSS_ACCESS_KEY_ID");
            setPropertyIfNotEmpty(oss, "accessKeySecret", "storage.aliyun.access-key-secret", env, "OSS_ACCESS_KEY_SECRET");
            setPropertyIfNotEmpty(oss, "bucketName", "storage.aliyun.bucket-name", env, "OSS_BUCKET_NAME");
            setPropertyIfNotEmpty(oss, "region", "storage.aliyun.region", env, "OSS_REGION");
        }
    }

    /**
     * 加载 Chroma 配置
     */
    private void loadChromaConfig(JsonNode config, ConfigurableEnvironment env) {
        if (!config.has("chroma")) {
            return;
        }
        
        JsonNode chroma = config.get("chroma");
        
        // 启用状态
        if (chroma.has("enabled")) {
            String envEnabled = env.getProperty("CHROMA_ENABLED");
            if (envEnabled == null || envEnabled.isEmpty()) {
                System.setProperty("vectorstore.chroma.enabled", 
                    String.valueOf(chroma.get("enabled").asBoolean(false)));
                log.info("从配置文件加载 Chroma 启用状态: {}", chroma.get("enabled").asBoolean());
            }
        }
        
        // Chroma URL
        if (chroma.has("url") && !chroma.get("url").asText().isEmpty()) {
            String envUrl = env.getProperty("CHROMA_URL");
            if (envUrl == null || envUrl.isEmpty()) {
                System.setProperty("vectorstore.chroma.url", chroma.get("url").asText());
            }
        }
        
        // Chroma 认证 Token
        if (chroma.has("authToken") && !chroma.get("authToken").asText().isEmpty()) {
            String envToken = env.getProperty("CHROMA_AUTH_TOKEN");
            if (envToken == null || envToken.isEmpty()) {
                System.setProperty("vectorstore.chroma.auth-token", chroma.get("authToken").asText());
            }
        }
    }

    /**
     * 辅助方法：如果环境变量未设置且配置值非空，则设置系统属性
     */
    private void setPropertyIfNotEmpty(JsonNode node, String jsonKey, String propertyKey, 
                                       ConfigurableEnvironment env, String envKey) {
        if (node.has(jsonKey)) {
            String value = node.get(jsonKey).asText();
            if (!value.isEmpty()) {
                String envValue = env.getProperty(envKey);
                if (envValue == null || envValue.isEmpty()) {
                    System.setProperty(propertyKey, value);
                }
            }
        }
    }
}
