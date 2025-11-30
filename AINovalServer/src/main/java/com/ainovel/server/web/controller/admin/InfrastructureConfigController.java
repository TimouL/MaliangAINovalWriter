package com.ainovel.server.web.controller.admin;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.config.infrastructure.InfrastructureStatusManager;
import com.ainovel.server.service.setup.InfrastructureConfigService;
import com.ainovel.server.service.setup.InfrastructureConfigService.ChromaConfigUpdate;
import com.ainovel.server.service.setup.InfrastructureConfigService.InfrastructureConfigDTO;
import com.ainovel.server.service.setup.InfrastructureConfigService.MongoPoolConfigUpdate;
import com.ainovel.server.service.setup.InfrastructureConfigService.StorageConfigUpdate;
import com.ainovel.server.service.setup.InfrastructureConfigService.TaskConfigUpdate;
import com.ainovel.server.service.setup.SetupService;

import reactor.core.publisher.Mono;

/**
 * 基础设施配置管理控制器
 * 提供管理后台的配置查看和修改功能
 */
@RestController
@RequestMapping("/api/v1/admin/config")
public class InfrastructureConfigController {

    private final InfrastructureConfigService configService;
    private final SetupService setupService;
    private final InfrastructureStatusManager statusManager;

    public InfrastructureConfigController(
            InfrastructureConfigService configService,
            SetupService setupService,
            InfrastructureStatusManager statusManager) {
        this.configService = configService;
        this.setupService = setupService;
        this.statusManager = statusManager;
    }

    /**
     * 获取所有基础设施配置
     * GET /api/v1/admin/config/infrastructure
     */
    @GetMapping(value = "/infrastructure", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<InfrastructureConfigDTO>> getConfig() {
        return configService.getConfig()
            .map(ResponseEntity::ok);
    }

    /**
     * 更新存储配置
     * PUT /api/v1/admin/config/storage
     */
    @PutMapping(value = "/storage", 
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UpdateResponse>> updateStorage(@RequestBody StorageConfigUpdate update) {
        return configService.updateStorageConfig(update)
            .map(success -> ResponseEntity.ok(new UpdateResponse(
                success,
                success ? "存储配置已更新，部分配置需要重启后生效" : "更新失败",
                false // 存储配置需要重启
            )));
    }

    /**
     * 更新 Chroma 配置
     * PUT /api/v1/admin/config/chroma
     */
    @PutMapping(value = "/chroma",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UpdateResponse>> updateChroma(@RequestBody ChromaConfigUpdate update) {
        return configService.updateChromaConfig(update)
            .map(success -> ResponseEntity.ok(new UpdateResponse(
                success,
                success ? "Chroma 配置已更新" : "更新失败",
                true // Chroma 支持热更新
            )));
    }

    /**
     * 测试存储连接
     * POST /api/v1/admin/config/test/storage
     */
    @PostMapping(value = "/test/storage",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TestResponse>> testStorage(@RequestBody StorageConfigUpdate config) {
        return setupService.testStorageConnection(new SetupService.StorageConfig(
                config.provider(),
                config.localPath(),
                config.ossEndpoint(),
                config.ossAccessKeyId(),
                config.ossAccessKeySecret(),
                config.ossBucketName()
            ))
            .map(result -> ResponseEntity.ok(new TestResponse(result.success(), result.message())));
    }

    /**
     * 测试 Chroma 连接
     * POST /api/v1/admin/config/test/chroma
     */
    @PostMapping(value = "/test/chroma",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TestResponse>> testChroma(@RequestBody ChromaTestRequest request) {
        return setupService.testChromaConnection(request.url(), request.authToken())
            .map(result -> ResponseEntity.ok(new TestResponse(result.success(), result.message())));
    }

    /**
     * 重载配置（热更新）
     * POST /api/v1/admin/config/reload/{component}
     */
    @PostMapping(value = "/reload/chroma", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ReloadResponse>> reloadChroma() {
        // 触发 Chroma 重新连接
        statusManager.checkChromaConnection();
        return Mono.just(ResponseEntity.ok(new ReloadResponse(true, "Chroma 配置已重载")));
    }

    @PostMapping(value = "/reload/storage", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ReloadResponse>> reloadStorage() {
        // 存储配置重载需要重启
        return Mono.just(ResponseEntity.ok(new ReloadResponse(false, "存储配置需要重启应用后生效")));
    }

    /**
     * 更新 MongoDB 连接池配置
     * PUT /api/v1/admin/config/mongo-pool
     */
    @PutMapping(value = "/mongo-pool",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UpdateResponse>> updateMongoPool(@RequestBody MongoPoolConfigUpdate update) {
        return configService.updateMongoPoolConfig(update)
            .map(success -> ResponseEntity.ok(new UpdateResponse(
                success,
                success ? "MongoDB 连接池配置已保存到配置文件，需要重启应用后生效。注意：如果环境变量已设置，将优先使用环境变量配置。" : "更新失败",
                false // 需要重启
            )));
    }

    /**
     * 更新任务系统配置
     * PUT /api/v1/admin/config/task
     */
    @PutMapping(value = "/task",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UpdateResponse>> updateTask(@RequestBody TaskConfigUpdate update) {
        return configService.updateTaskConfig(update)
            .map(success -> ResponseEntity.ok(new UpdateResponse(
                success,
                success ? "任务系统配置已保存到配置文件，需要重启应用后生效。注意：如果环境变量已设置，将优先使用环境变量配置。" : "更新失败",
                false // 需要重启
            )));
    }

    // Response Records
    public record UpdateResponse(boolean success, String message, boolean hotReloadable) {}
    public record TestResponse(boolean success, String message) {}
    public record ReloadResponse(boolean success, String message) {}
    public record ChromaTestRequest(String url, String authToken) {}
}
