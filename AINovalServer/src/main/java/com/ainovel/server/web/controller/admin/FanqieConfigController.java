package com.ainovel.server.web.controller.admin;

import com.ainovel.server.service.fanqie.FanqieApiConfigService;
import com.ainovel.server.service.fanqie.FanqieApiConfigService.ConfigSnapshot;
import com.ainovel.server.service.fanqie.FanqieApiConfigService.ConfigUpdate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 番茄小说服务配置管理控制器
 */
@RestController
@RequestMapping("/api/v1/admin/config/fanqie")
@PreAuthorize("hasAuthority('ADMIN_MANAGE_CONFIGS') or hasRole('SUPER_ADMIN')")
public class FanqieConfigController {

    private final FanqieApiConfigService configService;

    public FanqieConfigController(FanqieApiConfigService configService) {
        this.configService = configService;
    }

    /**
     * 获取番茄小说服务配置
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ConfigSnapshot>> getConfig() {
        return Mono.just(ResponseEntity.ok(configService.getConfigSnapshot()));
    }

    /**
     * 更新番茄小说服务配置
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UpdateResponse>> updateConfig(@RequestBody ConfigUpdate update) {
        return configService.updateConfig(update)
                .map(success -> ResponseEntity.ok(new UpdateResponse(
                        success,
                        success ? "番茄小说配置已更新" : "更新失败",
                        true)));
    }

    /**
     * 测试第三方 API 连接
     */
    @PostMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<TestResponse>> testConnection() {
        return configService.testApiConnection()
                .map(result -> ResponseEntity.ok(new TestResponse(result.success(), result.message())));
    }

    /**
     * 重新加载远程配置
     */
    @PostMapping(value = "/reload", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ReloadResponse>> reloadConfig() {
        return configService.reloadRemoteConfig()
                .map(success -> ResponseEntity.ok(new ReloadResponse(
                        success,
                        success ? "远程配置已重新加载" : "加载失败")));
    }

    public record UpdateResponse(boolean success, String message, boolean hotReloadable) {}
    public record TestResponse(boolean success, String message) {}
    public record ReloadResponse(boolean success, String message) {}
}
