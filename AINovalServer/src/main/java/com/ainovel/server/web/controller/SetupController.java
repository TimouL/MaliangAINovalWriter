package com.ainovel.server.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.service.setup.SetupService;
import com.ainovel.server.service.setup.SetupService.AdminInitResult;
import com.ainovel.server.service.setup.SetupService.InfrastructureConfig;
import com.ainovel.server.service.setup.SetupService.MongoTestResult;
import com.ainovel.server.service.setup.SetupService.SetupStatus;
import com.ainovel.server.service.setup.SetupService.StorageConfig;
import com.ainovel.server.service.setup.SetupService.TestResult;

import reactor.core.publisher.Mono;

/**
 * 配置向导控制器
 * 提供首次启动配置向导的 API
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    /**
     * 获取配置状态
     * GET /api/setup/status
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<SetupStatus>> getStatus() {
        return setupService.getSetupStatus()
            .map(ResponseEntity::ok);
    }

    /**
     * 测试 MongoDB 连接
     * POST /api/setup/test-mongodb
     */
    @PostMapping(value = "/test-mongodb", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MongoTestResult>> testMongoDB(@RequestBody MongoTestRequest request) {
        return setupService.testMongoConnection(request.uri())
            .map(ResponseEntity::ok);
    }

    /**
     * 测试存储连接
     * POST /api/setup/test-storage
     */
    @PostMapping(value = "/test-storage",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TestResult>> testStorage(@RequestBody StorageTestRequest request) {
        StorageConfig config = new StorageConfig(
            request.provider(),
            request.localPath(),
            request.ossEndpoint(),
            request.ossAccessKeyId(),
            request.ossAccessKeySecret(),
            request.ossBucketName()
        );
        return setupService.testStorageConnection(config)
            .map(ResponseEntity::ok);
    }

    /**
     * 测试 Chroma 连接
     * POST /api/setup/test-chroma
     */
    @PostMapping(value = "/test-chroma",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TestResult>> testChroma(@RequestBody ChromaTestRequest request) {
        return setupService.testChromaConnection(request.url(), request.authToken())
            .map(ResponseEntity::ok);
    }

    /**
     * 保存配置
     * POST /api/setup/save-config
     */
    @PostMapping(value = "/save-config",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<SaveConfigResponse>> saveConfig(@RequestBody SaveConfigRequest request) {
        InfrastructureConfig config = new InfrastructureConfig(
            request.mongoUri(),
            request.storageProvider(),
            request.localStoragePath(),
            request.ossEndpoint(),
            request.ossAccessKeyId(),
            request.ossAccessKeySecret(),
            request.ossBucketName(),
            request.chromaEnabled(),
            request.chromaUrl(),
            request.chromaAuthToken()
        );
        
        return setupService.saveConfig(config)
            .map(success -> ResponseEntity.ok(new SaveConfigResponse(success, 
                success ? "配置保存成功" : "配置保存失败")));
    }

    /**
     * 初始化管理员
     * POST /api/setup/init-admin
     */
    @PostMapping(value = "/init-admin",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AdminInitResult>> initAdmin(@RequestBody InitAdminRequest request) {
        return setupService.initializeAdmin(request.username(), request.email(), request.password())
            .map(ResponseEntity::ok);
    }

    /**
     * 完成配置向导
     * POST /api/setup/complete
     */
    @PostMapping(value = "/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<CompleteResponse>> complete() {
        return setupService.completeSetup()
            .map(success -> ResponseEntity.ok(new CompleteResponse(success,
                success ? "配置完成，系统已就绪" : "完成配置失败")));
    }

    // Request/Response Records
    
    public record MongoTestRequest(String uri) {}
    
    public record StorageTestRequest(
        String provider,
        String localPath,
        String ossEndpoint,
        String ossAccessKeyId,
        String ossAccessKeySecret,
        String ossBucketName
    ) {}
    
    public record ChromaTestRequest(String url, String authToken) {}
    
    public record SaveConfigRequest(
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
    
    public record InitAdminRequest(String username, String email, String password) {}
    
    public record SaveConfigResponse(boolean success, String message) {}
    
    public record CompleteResponse(boolean success, String message) {}
}
