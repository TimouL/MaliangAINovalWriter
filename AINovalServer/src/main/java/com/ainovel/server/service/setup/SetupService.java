package com.ainovel.server.service.setup;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ainovel.server.config.infrastructure.InfrastructureStatusManager;
import com.ainovel.server.domain.model.Role;
import com.ainovel.server.domain.model.User;
import com.ainovel.server.repository.RoleRepository;
import com.ainovel.server.repository.UserRepository;
import com.ainovel.server.service.CreditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 配置向导服务
 * 处理首次启动配置、连接测试、管理员初始化等
 */
@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    
    private static final String CONFIG_FILE_PATH = "config/infrastructure.json";
    
    private final ObjectMapper objectMapper;
    private final InfrastructureStatusManager statusManager;
    
    @Autowired(required = false)
    private UserRepository userRepository;
    
    @Autowired(required = false)
    private RoleRepository roleRepository;
    
    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;
    
    @Autowired(required = false)
    private CreditService creditService;

    public SetupService(ObjectMapper objectMapper, InfrastructureStatusManager statusManager) {
        this.objectMapper = objectMapper;
        this.statusManager = statusManager;
    }

    /**
     * 获取配置状态
     */
    public Mono<SetupStatus> getSetupStatus() {
        return Mono.fromCallable(() -> {
            boolean configFileExists = new File(CONFIG_FILE_PATH).exists();
            boolean setupCompleted = statusManager.isSetupCompleted();
            boolean mongoConnected = statusManager.isMongoConnected();
            
            // 检查管理员是否存在
            boolean adminExists = false;
            if (mongoConnected && userRepository != null) {
                try {
                    adminExists = userRepository.findByUsername("admin")
                        .blockOptional()
                        .isPresent();
                } catch (Exception e) {
                    log.warn("检查管理员存在性失败: {}", e.getMessage());
                }
            }
            
            return new SetupStatus(
                configFileExists,
                setupCompleted,
                mongoConnected,
                true, // storage 默认可用
                false, // chroma 默认禁用
                adminExists
            );
        });
    }

    /**
     * 测试 MongoDB 连接
     */
    public Mono<MongoTestResult> testMongoConnection(String uri) {
        return Mono.fromCallable(() -> {
            MongoClient testClient = null;
            try {
                log.info("测试 MongoDB 连接: {}", maskUri(uri));
                
                ConnectionString connectionString = new ConnectionString(uri);
                MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToClusterSettings(builder -> 
                        builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                    .build();
                
                testClient = MongoClients.create(settings);
                
                // 执行 ping 命令测试连接
                Document pingResult = Flux.from(testClient.getDatabase("admin")
                    .runCommand(new Document("ping", 1)))
                    .blockFirst(java.time.Duration.ofSeconds(5));
                
                if (pingResult != null && pingResult.getDouble("ok") == 1.0) {
                    // 检测数据库状态
                    String databaseStatus = checkDatabaseStatus(testClient, connectionString);
                    log.info("MongoDB 连接成功，数据库状态: {}", databaseStatus);
                    return new MongoTestResult(true, "连接成功", databaseStatus);
                } else {
                    return new MongoTestResult(false, "连接失败：ping 命令返回异常", null);
                }
                
            } catch (Exception e) {
                log.error("MongoDB 连接测试失败: {}", e.getMessage());
                return new MongoTestResult(false, "连接失败: " + e.getMessage(), null);
            } finally {
                if (testClient != null) {
                    testClient.close();
                }
            }
        });
    }

    /**
     * 检测数据库状态
     */
    private String checkDatabaseStatus(MongoClient client, ConnectionString connectionString) {
        try {
            String dbName = connectionString.getDatabase();
            if (dbName == null || dbName.isEmpty()) {
                dbName = "ainovel";
            }
            
            // 检查 users 集合是否存在数据
            Long userCount = Flux.from(client.getDatabase(dbName)
                .getCollection("users")
                .countDocuments())
                .blockFirst(java.time.Duration.ofSeconds(5));
            
            if (userCount == null || userCount == 0) {
                return "empty"; // 空数据库
            }
            
            // 检查是否有管理员用户
            Document adminUser = Flux.from(client.getDatabase(dbName)
                .getCollection("users")
                .find(new Document("username", "admin"))
                .first())
                .blockFirst(java.time.Duration.ofSeconds(5));
            
            if (adminUser == null) {
                return "has_data_no_admin"; // 有数据但无管理员
            }
            
            return "complete"; // 数据完整
            
        } catch (Exception e) {
            log.warn("检测数据库状态失败: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * 测试存储连接
     */
    public Mono<TestResult> testStorageConnection(StorageConfig config) {
        return Mono.fromCallable(() -> {
            if ("local".equalsIgnoreCase(config.provider())) {
                // 本地存储：检查目录是否可写
                File dir = new File(config.localPath() != null ? config.localPath() : "/data/storage");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                if (dir.canWrite()) {
                    return new TestResult(true, "本地存储可用");
                } else {
                    return new TestResult(false, "本地存储目录不可写: " + dir.getAbsolutePath());
                }
            } else if ("oss".equalsIgnoreCase(config.provider())) {
                // OSS 存储：这里简化处理，实际应该测试连接
                // 完整实现需要调用 AliOSSStorageProvider
                if (config.ossEndpoint() == null || config.ossAccessKeyId() == null) {
                    return new TestResult(false, "OSS 配置不完整");
                }
                return new TestResult(true, "OSS 配置已保存，将在启动时验证");
            }
            return new TestResult(false, "未知的存储类型: " + config.provider());
        });
    }

    /**
     * 测试 Chroma 连接
     */
    public Mono<TestResult> testChromaConnection(String url, String authToken) {
        return Mono.fromCallable(() -> {
            try {
                // 简单的 HTTP 请求测试 Chroma 心跳
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
                
                java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url + "/api/v2/heartbeat"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET();
                
                if (authToken != null && !authToken.isEmpty()) {
                    requestBuilder.header("X-Chroma-Token", authToken);
                }
                
                java.net.http.HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() == 200) {
                    return new TestResult(true, "Chroma 连接成功");
                } else {
                    return new TestResult(false, "Chroma 返回错误: " + response.statusCode());
                }
                
            } catch (Exception e) {
                log.error("Chroma 连接测试失败: {}", e.getMessage());
                return new TestResult(false, "连接失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存配置到文件
     */
    public Mono<Boolean> saveConfig(InfrastructureConfig config) {
        return Mono.fromCallable(() -> {
            try {
                // 确保目录存在
                File configDir = new File("config");
                if (!configDir.exists()) {
                    configDir.mkdirs();
                }
                
                // 构建配置 JSON
                ObjectNode root = objectMapper.createObjectNode();
                root.put("version", "1.0");
                root.put("setupCompleted", false); // 向导完成时再设为 true
                root.put("setupTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                // MongoDB 配置
                ObjectNode mongodb = root.putObject("mongodb");
                mongodb.put("uri", config.mongoUri());
                
                // 存储配置
                ObjectNode storage = root.putObject("storage");
                storage.put("provider", config.storageProvider() != null ? config.storageProvider() : "local");
                
                ObjectNode local = storage.putObject("local");
                local.put("basePath", config.localStoragePath() != null ? config.localStoragePath() : "/data/storage");
                
                ObjectNode oss = storage.putObject("oss");
                oss.put("endpoint", config.ossEndpoint() != null ? config.ossEndpoint() : "");
                oss.put("accessKeyId", config.ossAccessKeyId() != null ? config.ossAccessKeyId() : "");
                oss.put("accessKeySecret", config.ossAccessKeySecret() != null ? config.ossAccessKeySecret() : "");
                oss.put("bucketName", config.ossBucketName() != null ? config.ossBucketName() : "");
                
                // Chroma 配置
                ObjectNode chroma = root.putObject("chroma");
                chroma.put("enabled", config.chromaEnabled() != null ? config.chromaEnabled() : false);
                chroma.put("url", config.chromaUrl() != null ? config.chromaUrl() : "");
                chroma.put("authToken", config.chromaAuthToken() != null ? config.chromaAuthToken() : "");
                
                // 写入文件
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE_PATH)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                log.info("配置已保存到: {}", CONFIG_FILE_PATH);
                return true;
                
            } catch (Exception e) {
                log.error("保存配置失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 初始化管理员账户
     */
    public Mono<AdminInitResult> initializeAdmin(String username, String email, String password) {
        if (userRepository == null || roleRepository == null || passwordEncoder == null) {
            return Mono.just(new AdminInitResult(false, "系统组件未就绪，请先完成数据库配置", null));
        }
        
        return roleRepository.findByRoleName("ROLE_ADMIN")
            .switchIfEmpty(Mono.defer(() -> {
                // 创建管理员角色
                Role adminRole = Role.builder()
                    .roleName("ROLE_ADMIN")
                    .displayName("系统管理员")
                    .description("系统管理员，拥有所有权限")
                    .enabled(true)
                    .priority(100)
                    .build();
                return roleRepository.save(adminRole);
            }))
            .flatMap(role -> {
                // 检查用户是否已存在
                return userRepository.findByUsername(username)
                    .flatMap(existingUser -> 
                        Mono.just(new AdminInitResult(false, "用户名已存在", null)))
                    .switchIfEmpty(Mono.defer(() -> {
                        // 创建管理员用户
                        User admin = User.builder()
                            .username(username)
                            .email(email)
                            .password(passwordEncoder.encode(password))
                            .accountStatus(User.AccountStatus.ACTIVE)
                            .build();
                        admin.getRoleIds().add(role.getId());
                        admin.getRoles().add(role.getRoleName());
                        
                        return userRepository.save(admin)
                            .flatMap(savedUser -> {
                                // 赠送初始积分
                                if (creditService != null) {
                                    return creditService.grantNewUserCredits(savedUser.getId())
                                        .thenReturn(new AdminInitResult(true, "管理员创建成功", savedUser.getId()));
                                }
                                return Mono.just(new AdminInitResult(true, "管理员创建成功", savedUser.getId()));
                            });
                    }));
            })
            .onErrorResume(e -> {
                log.error("初始化管理员失败: {}", e.getMessage(), e);
                return Mono.just(new AdminInitResult(false, "初始化失败: " + e.getMessage(), null));
            });
    }

    /**
     * 完成配置向导
     */
    public Mono<Boolean> completeSetup() {
        return Mono.fromCallable(() -> {
            try {
                File configFile = new File(CONFIG_FILE_PATH);
                if (!configFile.exists()) {
                    log.error("配置文件不存在，无法完成向导");
                    return false;
                }
                
                // 读取现有配置
                JsonNode config = objectMapper.readTree(configFile);
                ObjectNode root = (ObjectNode) config;
                root.put("setupCompleted", true);
                root.put("completedTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                // 写回文件
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE_PATH)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                // 更新状态管理器
                statusManager.markSetupCompleted();
                
                log.info("配置向导已完成");
                return true;
                
            } catch (Exception e) {
                log.error("完成配置向导失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 脱敏 URI（隐藏密码）
     */
    private String maskUri(String uri) {
        if (uri == null) return null;
        return uri.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }

    // DTO Records
    public record SetupStatus(
        boolean configFileExists,
        boolean setupCompleted,
        boolean mongoConnected,
        boolean storageConfigured,
        boolean chromaConfigured,
        boolean adminExists
    ) {}

    public record MongoTestResult(
        boolean success,
        String message,
        String databaseStatus
    ) {}

    public record TestResult(
        boolean success,
        String message
    ) {}

    public record StorageConfig(
        String provider,
        String localPath,
        String ossEndpoint,
        String ossAccessKeyId,
        String ossAccessKeySecret,
        String ossBucketName
    ) {}

    public record InfrastructureConfig(
        String mongoUri,
        String storageProvider,
        String localStoragePath,
        String ossEndpoint,
        String ossAccessKeyId,
        String ossAccessKeySecret,
        String ossBucketName,
        Boolean chromaEnabled,
        String chromaUrl,
        String chromaAuthToken
    ) {}

    public record AdminInitResult(
        boolean success,
        String message,
        String userId
    ) {}
}
