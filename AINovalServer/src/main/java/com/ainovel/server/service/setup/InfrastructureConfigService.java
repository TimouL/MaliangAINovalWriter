package com.ainovel.server.service.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.ainovel.server.config.infrastructure.InfrastructureStatusManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;

/**
 * 基础设施配置服务
 * 提供配置的读取、更新和热重载功能
 */
@Service
public class InfrastructureConfigService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureConfigService.class);
    private static final String CONFIG_FILE_PATH = "config/infrastructure.json";

    private final ObjectMapper objectMapper;
    private final InfrastructureStatusManager statusManager;
    private final Environment environment;
    
    // 从环境变量/配置注入 Chroma 配置
    @Value("${vectorstore.chroma.enabled:false}")
    private boolean chromaEnabled;
    
    @Value("${vectorstore.chroma.url:}")
    private String chromaUrl;
    
    @Value("${vectorstore.chroma.auth-token:}")
    private String chromaAuthToken;
    
    // MongoDB 连接池配置（从 MongoConfig 硬编码值读取，后续可改为环境变量）
    @Value("${spring.data.mongodb.pool.max-size:50}")
    private int mongoPoolMaxSize;
    
    @Value("${spring.data.mongodb.pool.min-size:5}")
    private int mongoPoolMinSize;
    
    @Value("${spring.data.mongodb.pool.max-wait-time:10}")
    private int mongoPoolMaxWaitTime;
    
    @Value("${spring.data.mongodb.pool.max-idle-time:60}")
    private int mongoPoolMaxIdleTime;
    
    // 任务系统配置
    @Value("${task.local.concurrency:2000}")
    private int taskLocalConcurrency;
    
    @Value("${task.transport:local}")
    private String taskTransport;

    @Autowired
    public InfrastructureConfigService(ObjectMapper objectMapper, InfrastructureStatusManager statusManager, Environment environment) {
        this.objectMapper = objectMapper;
        this.statusManager = statusManager;
        this.environment = environment;
    }
    
    /**
     * 检测配置来源
     * @param envVarName 环境变量名
     * @return "env" 如果是环境变量, "config" 如果是配置文件, "default" 如果是默认值
     */
    private String detectConfigSource(String envVarName) {
        // 检查系统环境变量
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return "env";
        }
        // 检查 Spring Environment（可能来自配置文件）
        String propValue = environment.getProperty(envVarName);
        if (propValue != null && !propValue.isEmpty()) {
            return "config";
        }
        return "default";
    }

    /**
     * 获取所有基础设施配置（脱敏）
     * 优先使用环境变量/Spring配置，其次使用配置文件
     */
    public Mono<InfrastructureConfigDTO> getConfig() {
        return Mono.fromCallable(() -> {
            File configFile = new File(CONFIG_FILE_PATH);
            JsonNode config;
            if (!configFile.exists()) {
                // 配置文件不存在，使用空配置（环境变量仍会被读取）
                config = objectMapper.createObjectNode();
            } else {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    String content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                    config = objectMapper.readTree(content);
                }
            }
            return parseConfig(config);
        });
    }

    /**
     * 解析配置并脱敏
     */
    private InfrastructureConfigDTO parseConfig(JsonNode config) {
        InfrastructureConfigDTO dto = new InfrastructureConfigDTO();

        // MongoDB 配置
        if (config.has("mongodb")) {
            JsonNode mongodb = config.get("mongodb");
            dto.setMongoUri(maskUri(mongodb.path("uri").asText("")));
        }
        dto.setMongoConnected(statusManager.isMongoConnected());
        
        // MongoDB 连接池配置及来源
        dto.setMongoPoolMaxSize(this.mongoPoolMaxSize);
        dto.setMongoPoolMinSize(this.mongoPoolMinSize);
        dto.setMongoPoolMaxWaitTime(this.mongoPoolMaxWaitTime);
        dto.setMongoPoolMaxIdleTime(this.mongoPoolMaxIdleTime);
        dto.setMongoPoolSource(detectConfigSource("SPRING_DATA_MONGODB_POOL_MAX_SIZE"));
        
        // 任务系统配置及来源
        dto.setTaskTransport(this.taskTransport);
        dto.setTaskLocalConcurrency(this.taskLocalConcurrency);
        dto.setTaskConfigSource(detectConfigSource("TASK_LOCAL_CONCURRENCY"));

        // 存储配置
        if (config.has("storage")) {
            JsonNode storage = config.get("storage");
            dto.setStorageProvider(storage.path("provider").asText("local"));
            
            if (storage.has("local")) {
                dto.setLocalStoragePath(storage.get("local").path("basePath").asText(""));
            }
            
            if (storage.has("oss")) {
                JsonNode oss = storage.get("oss");
                dto.setOssEndpoint(oss.path("endpoint").asText(""));
                dto.setOssAccessKeyId(maskSecret(oss.path("accessKeyId").asText("")));
                dto.setOssBucketName(oss.path("bucketName").asText(""));
            }
        }

        // Chroma 配置 - 优先使用环境变量/Spring配置，其次使用配置文件
        dto.setChromaEnabled(this.chromaEnabled);
        dto.setChromaUrl(this.chromaUrl != null && !this.chromaUrl.isEmpty() 
            ? this.chromaUrl 
            : (config.has("chroma") ? config.get("chroma").path("url").asText("") : ""));
        String tokenValue = this.chromaAuthToken != null && !this.chromaAuthToken.isEmpty()
            ? this.chromaAuthToken
            : (config.has("chroma") ? config.get("chroma").path("authToken").asText("") : "");
        dto.setChromaAuthToken(maskSecret(tokenValue));

        // 状态信息
        dto.setSetupCompleted(config.path("setupCompleted").asBoolean(false));
        dto.setLastModified(config.path("completedTime").asText(config.path("setupTime").asText("")));

        return dto;
    }

    /**
     * 更新存储配置
     */
    public Mono<Boolean> updateStorageConfig(StorageConfigUpdate update) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode config = readConfig();
                ObjectNode root = (ObjectNode) config;
                ObjectNode storage = root.has("storage") 
                    ? (ObjectNode) root.get("storage") 
                    : root.putObject("storage");

                storage.put("provider", update.provider());

                if ("local".equals(update.provider())) {
                    ObjectNode local = storage.has("local") 
                        ? (ObjectNode) storage.get("local") 
                        : storage.putObject("local");
                    local.put("basePath", update.localPath() != null ? update.localPath() : "/data/storage");
                } else if ("oss".equals(update.provider())) {
                    ObjectNode oss = storage.has("oss") 
                        ? (ObjectNode) storage.get("oss") 
                        : storage.putObject("oss");
                    if (update.ossEndpoint() != null) {
                        oss.put("endpoint", update.ossEndpoint());
                    }
                    if (update.ossAccessKeyId() != null && !update.ossAccessKeyId().startsWith("***")) {
                        oss.put("accessKeyId", update.ossAccessKeyId());
                    }
                    if (update.ossAccessKeySecret() != null && !update.ossAccessKeySecret().isEmpty()) {
                        oss.put("accessKeySecret", update.ossAccessKeySecret());
                    }
                    if (update.ossBucketName() != null) {
                        oss.put("bucketName", update.ossBucketName());
                    }
                }

                root.put("lastModified", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writeConfig(root);
                log.info("存储配置已更新: provider={}", update.provider());
                return true;
            } catch (Exception e) {
                log.error("更新存储配置失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 更新 Chroma 配置
     */
    public Mono<Boolean> updateChromaConfig(ChromaConfigUpdate update) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode config = readConfig();
                ObjectNode root = (ObjectNode) config;
                ObjectNode chroma = root.has("chroma") 
                    ? (ObjectNode) root.get("chroma") 
                    : root.putObject("chroma");

                chroma.put("enabled", update.enabled());
                
                if (update.url() != null) {
                    chroma.put("url", update.url());
                }
                if (update.authToken() != null && !update.authToken().startsWith("***")) {
                    chroma.put("authToken", update.authToken());
                }

                root.put("lastModified", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writeConfig(root);
                log.info("Chroma 配置已更新: enabled={}", update.enabled());
                return true;
            } catch (Exception e) {
                log.error("更新 Chroma 配置失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 更新 MongoDB 连接池配置
     */
    public Mono<Boolean> updateMongoPoolConfig(MongoPoolConfigUpdate update) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode config = readConfig();
                ObjectNode root = (ObjectNode) config;
                ObjectNode mongoPool = root.has("mongoPool") 
                    ? (ObjectNode) root.get("mongoPool") 
                    : root.putObject("mongoPool");

                if (update.maxSize() != null) {
                    mongoPool.put("maxSize", update.maxSize());
                }
                if (update.minSize() != null) {
                    mongoPool.put("minSize", update.minSize());
                }
                if (update.maxWaitTime() != null) {
                    mongoPool.put("maxWaitTime", update.maxWaitTime());
                }
                if (update.maxIdleTime() != null) {
                    mongoPool.put("maxIdleTime", update.maxIdleTime());
                }

                root.put("lastModified", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writeConfig(root);
                log.info("MongoDB 连接池配置已更新: maxSize={}, minSize={}", update.maxSize(), update.minSize());
                return true;
            } catch (Exception e) {
                log.error("更新 MongoDB 连接池配置失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 更新任务系统配置
     */
    public Mono<Boolean> updateTaskConfig(TaskConfigUpdate update) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode config = readConfig();
                ObjectNode root = (ObjectNode) config;
                ObjectNode task = root.has("task") 
                    ? (ObjectNode) root.get("task") 
                    : root.putObject("task");

                if (update.transport() != null) {
                    task.put("transport", update.transport());
                }
                if (update.localConcurrency() != null) {
                    task.put("localConcurrency", update.localConcurrency());
                }

                root.put("lastModified", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writeConfig(root);
                log.info("任务系统配置已更新: transport={}, concurrency={}", update.transport(), update.localConcurrency());
                return true;
            } catch (Exception e) {
                log.error("更新任务系统配置失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 读取配置文件
     */
    private JsonNode readConfig() throws Exception {
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) {
            // 创建默认配置
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", "1.0");
            root.put("setupCompleted", false);
            return root;
        }
        try (FileInputStream fis = new FileInputStream(configFile)) {
            String content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(content);
        }
    }

    /**
     * 写入配置文件
     */
    private void writeConfig(JsonNode config) throws Exception {
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE_PATH)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 脱敏 URI
     */
    private String maskUri(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        return uri.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }

    /**
     * 脱敏密钥
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) return "";
        if (secret.length() <= 4) return "***";
        return secret.substring(0, 4) + "***";
    }

    // DTO 类

    public static class InfrastructureConfigDTO {
        private String mongoUri;
        private boolean mongoConnected;
        // MongoDB 连接池配置
        private int mongoPoolMaxSize;
        private int mongoPoolMinSize;
        private int mongoPoolMaxWaitTime;
        private int mongoPoolMaxIdleTime;
        private String mongoPoolSource; // 配置来源: env/config/default
        // 存储配置
        private String storageProvider;
        private String localStoragePath;
        private String ossEndpoint;
        private String ossAccessKeyId;
        private String ossBucketName;
        // Chroma 配置
        private boolean chromaEnabled;
        private String chromaUrl;
        private String chromaAuthToken;
        // 任务系统配置
        private String taskTransport;
        private int taskLocalConcurrency;
        private String taskConfigSource; // 配置来源: env/config/default
        // 元数据
        private boolean setupCompleted;
        private String lastModified;

        public static InfrastructureConfigDTO empty() {
            InfrastructureConfigDTO dto = new InfrastructureConfigDTO();
            dto.setStorageProvider("local");
            dto.setChromaEnabled(false);
            dto.setSetupCompleted(false);
            dto.setMongoPoolMaxSize(50);
            dto.setMongoPoolMinSize(5);
            dto.setMongoPoolMaxWaitTime(10);
            dto.setMongoPoolMaxIdleTime(60);
            dto.setMongoPoolSource("default");
            dto.setTaskTransport("local");
            dto.setTaskLocalConcurrency(2000);
            dto.setTaskConfigSource("default");
            return dto;
        }

        // Getters and Setters - MongoDB
        public String getMongoUri() { return mongoUri; }
        public void setMongoUri(String mongoUri) { this.mongoUri = mongoUri; }
        public boolean isMongoConnected() { return mongoConnected; }
        public void setMongoConnected(boolean mongoConnected) { this.mongoConnected = mongoConnected; }
        public int getMongoPoolMaxSize() { return mongoPoolMaxSize; }
        public void setMongoPoolMaxSize(int mongoPoolMaxSize) { this.mongoPoolMaxSize = mongoPoolMaxSize; }
        public int getMongoPoolMinSize() { return mongoPoolMinSize; }
        public void setMongoPoolMinSize(int mongoPoolMinSize) { this.mongoPoolMinSize = mongoPoolMinSize; }
        public int getMongoPoolMaxWaitTime() { return mongoPoolMaxWaitTime; }
        public void setMongoPoolMaxWaitTime(int mongoPoolMaxWaitTime) { this.mongoPoolMaxWaitTime = mongoPoolMaxWaitTime; }
        public int getMongoPoolMaxIdleTime() { return mongoPoolMaxIdleTime; }
        public void setMongoPoolMaxIdleTime(int mongoPoolMaxIdleTime) { this.mongoPoolMaxIdleTime = mongoPoolMaxIdleTime; }
        public String getMongoPoolSource() { return mongoPoolSource; }
        public void setMongoPoolSource(String mongoPoolSource) { this.mongoPoolSource = mongoPoolSource; }
        // Getters and Setters - Storage
        public String getStorageProvider() { return storageProvider; }
        public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
        public String getLocalStoragePath() { return localStoragePath; }
        public void setLocalStoragePath(String localStoragePath) { this.localStoragePath = localStoragePath; }
        public String getOssEndpoint() { return ossEndpoint; }
        public void setOssEndpoint(String ossEndpoint) { this.ossEndpoint = ossEndpoint; }
        public String getOssAccessKeyId() { return ossAccessKeyId; }
        public void setOssAccessKeyId(String ossAccessKeyId) { this.ossAccessKeyId = ossAccessKeyId; }
        public String getOssBucketName() { return ossBucketName; }
        public void setOssBucketName(String ossBucketName) { this.ossBucketName = ossBucketName; }
        // Getters and Setters - Chroma
        public boolean isChromaEnabled() { return chromaEnabled; }
        public void setChromaEnabled(boolean chromaEnabled) { this.chromaEnabled = chromaEnabled; }
        public String getChromaUrl() { return chromaUrl; }
        public void setChromaUrl(String chromaUrl) { this.chromaUrl = chromaUrl; }
        public String getChromaAuthToken() { return chromaAuthToken; }
        public void setChromaAuthToken(String chromaAuthToken) { this.chromaAuthToken = chromaAuthToken; }
        // Getters and Setters - Task System
        public String getTaskTransport() { return taskTransport; }
        public void setTaskTransport(String taskTransport) { this.taskTransport = taskTransport; }
        public int getTaskLocalConcurrency() { return taskLocalConcurrency; }
        public void setTaskLocalConcurrency(int taskLocalConcurrency) { this.taskLocalConcurrency = taskLocalConcurrency; }
        public String getTaskConfigSource() { return taskConfigSource; }
        public void setTaskConfigSource(String taskConfigSource) { this.taskConfigSource = taskConfigSource; }
        // Getters and Setters - Metadata
        public boolean isSetupCompleted() { return setupCompleted; }
        public void setSetupCompleted(boolean setupCompleted) { this.setupCompleted = setupCompleted; }
        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    }

    public record StorageConfigUpdate(
        String provider,
        String localPath,
        String ossEndpoint,
        String ossAccessKeyId,
        String ossAccessKeySecret,
        String ossBucketName
    ) {}

    public record ChromaConfigUpdate(
        boolean enabled,
        String url,
        String authToken
    ) {}

    public record MongoPoolConfigUpdate(
        Integer maxSize,
        Integer minSize,
        Integer maxWaitTime,
        Integer maxIdleTime
    ) {}

    public record TaskConfigUpdate(
        String transport,
        Integer localConcurrency
    ) {}
}
