package com.ainovel.server.config.infrastructure;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * 基础设施状态管理器
 * 管理 MongoDB、Chroma、Storage 等组件的连接状态
 * 决定系统是否进入受限模式
 */
@Component
public class InfrastructureStatusManager {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureStatusManager.class);
    
    public static final String CONFIG_FILE_PATH = "config/infrastructure.json";

    public enum ConnectionStatus {
        CONNECTED,      // 正常连接
        DISCONNECTED,   // 未连接
        CONFIGURING,    // 配置中
        ERROR           // 连接错误
    }

    private final AtomicReference<ConnectionStatus> mongoStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
    private final AtomicReference<ConnectionStatus> chromaStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
    private final AtomicReference<ConnectionStatus> storageStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
    private volatile boolean setupCompleted = false;
    private volatile String lastError = null;

    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private ReactiveMongoTemplate mongoTemplate;
    
    @Value("${vectorstore.chroma.enabled:false}")
    private boolean chromaEnabled;

    public InfrastructureStatusManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadSetupStatus();
    }

    /**
     * 应用启动完成后检测所有连接
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动完成，开始检测基础设施连接状态...");
        checkAllConnections();
    }

    /**
     * 从配置文件加载设置完成状态
     * 如果环境变量 SPRING_DATA_MONGODB_URI 已设置，则认为已完成初始化
     */
    private void loadSetupStatus() {
        // 优先检查环境变量：如果 MongoDB URI 已通过环境变量配置，跳过初始化向导
        String mongoUri = System.getenv("SPRING_DATA_MONGODB_URI");
        if (mongoUri != null && !mongoUri.isEmpty()) {
            this.setupCompleted = true;
            log.info("检测到环境变量 SPRING_DATA_MONGODB_URI，跳过初始化向导");
            return;
        }
        
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                JsonNode config = objectMapper.readTree(configFile);
                if (config.has("setupCompleted")) {
                    this.setupCompleted = config.get("setupCompleted").asBoolean(false);
                }
                log.info("加载基础设施配置状态: setupCompleted={}", setupCompleted);
            } else {
                log.info("基础设施配置文件不存在，需要进行初始化配置");
                this.setupCompleted = false;
            }
        } catch (Exception e) {
            log.warn("加载基础设施配置状态失败: {}", e.getMessage());
            this.setupCompleted = false;
        }
    }

    /**
     * 检测所有组件连接状态
     */
    public void checkAllConnections() {
        checkMongoConnection();
        checkChromaConnection();
        checkStorageConnection();
        
        log.info("基础设施状态检测完成: MongoDB={}, Chroma={}, Storage={}, SetupCompleted={}",
                mongoStatus.get(), chromaStatus.get(), storageStatus.get(), setupCompleted);
    }

    /**
     * 检测 MongoDB 连接
     */
    public void checkMongoConnection() {
        if (mongoTemplate == null) {
            mongoStatus.set(ConnectionStatus.DISCONNECTED);
            log.warn("MongoDB 模板未初始化");
            return;
        }
        
        try {
            // 执行简单的数据库命令测试连接
            mongoTemplate.executeCommand("{ping: 1}")
                .doOnSuccess(result -> {
                    mongoStatus.set(ConnectionStatus.CONNECTED);
                    log.info("MongoDB 连接正常");
                })
                .doOnError(error -> {
                    mongoStatus.set(ConnectionStatus.ERROR);
                    lastError = "MongoDB 连接失败: " + error.getMessage();
                    log.error("MongoDB 连接失败: {}", error.getMessage());
                })
                .subscribe();
        } catch (Exception e) {
            mongoStatus.set(ConnectionStatus.ERROR);
            lastError = "MongoDB 连接异常: " + e.getMessage();
            log.error("MongoDB 连接异常: {}", e.getMessage());
        }
    }

    /**
     * 检测 Chroma 连接
     */
    public void checkChromaConnection() {
        if (!chromaEnabled) {
            chromaStatus.set(ConnectionStatus.DISCONNECTED);
            log.info("Chroma 向量库已禁用");
            return;
        }
        // Chroma 连接检测将在 VectorStoreConfig 中实现
        // 这里暂时标记为已连接（如果启用）
        chromaStatus.set(ConnectionStatus.CONNECTED);
    }

    /**
     * 检测存储服务连接
     */
    public void checkStorageConnection() {
        // 本地存储默认可用
        storageStatus.set(ConnectionStatus.CONNECTED);
    }

    /**
     * 判断是否处于受限模式
     * 受限模式条件：MongoDB 未连接 或 初始化向导未完成
     */
    public boolean isRestrictedMode() {
        return mongoStatus.get() != ConnectionStatus.CONNECTED || !setupCompleted;
    }

    /**
     * 判断是否需要显示初始化向导
     */
    public boolean needsSetupWizard() {
        return !setupCompleted;
    }

    /**
     * 判断 MongoDB 是否已连接
     */
    public boolean isMongoConnected() {
        return mongoStatus.get() == ConnectionStatus.CONNECTED;
    }

    /**
     * 标记初始化向导已完成
     */
    public void markSetupCompleted() {
        this.setupCompleted = true;
        log.info("初始化向导已完成");
    }

    /**
     * 更新 MongoDB 连接状态
     */
    public void updateMongoStatus(ConnectionStatus status) {
        this.mongoStatus.set(status);
    }

    /**
     * 更新 Chroma 连接状态
     */
    public void updateChromaStatus(ConnectionStatus status) {
        this.chromaStatus.set(status);
    }

    /**
     * 更新存储服务连接状态
     */
    public void updateStorageStatus(ConnectionStatus status) {
        this.storageStatus.set(status);
    }

    /**
     * 测试 MongoDB 连接（异步）
     */
    public Mono<Boolean> testMongoConnection(String uri) {
        // 这里需要创建临时连接测试
        // 实际实现将在 SetupService 中完成
        return Mono.just(true);
    }

    // Getters
    public ConnectionStatus getMongoStatus() {
        return mongoStatus.get();
    }

    public ConnectionStatus getChromaStatus() {
        return chromaStatus.get();
    }

    public ConnectionStatus getStorageStatus() {
        return storageStatus.get();
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }

    public String getLastError() {
        return lastError;
    }
    
    /**
     * 获取系统状态摘要
     */
    public SystemStatus getSystemStatus() {
        return new SystemStatus(
            !isRestrictedMode(),
            setupCompleted,
            mongoStatus.get() == ConnectionStatus.CONNECTED,
            storageStatus.get() == ConnectionStatus.CONNECTED,
            chromaEnabled && chromaStatus.get() == ConnectionStatus.CONNECTED,
            lastError
        );
    }

    /**
     * 系统状态数据类
     */
    public record SystemStatus(
        boolean operational,
        boolean setupCompleted,
        boolean mongoConnected,
        boolean storageConnected,
        boolean chromaConnected,
        String lastError
    ) {}
}
