package com.ainovel.server.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ainovel.server.config.infrastructure.InfrastructureStatusManager;
import com.ainovel.server.config.infrastructure.InfrastructureStatusManager.SystemStatus;

import reactor.core.publisher.Mono;

/**
 * 系统状态控制器
 * 提供系统运行状态查询接口
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final InfrastructureStatusManager statusManager;

    public SystemStatusController(InfrastructureStatusManager statusManager) {
        this.statusManager = statusManager;
    }

    /**
     * 获取系统状态
     * GET /api/system/status
     * 
     * 返回系统是否处于受限模式、各组件连接状态等
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<SystemStatusResponse>> getStatus() {
        SystemStatus status = statusManager.getSystemStatus();
        
        return Mono.just(ResponseEntity.ok(new SystemStatusResponse(
            status.operational(),
            statusManager.isRestrictedMode(),
            status.setupCompleted(),
            status.mongoConnected(),
            status.storageConnected(),
            status.chromaConnected(),
            status.lastError()
        )));
    }

    /**
     * 系统状态响应
     */
    public record SystemStatusResponse(
        boolean operational,        // 系统是否正常运行
        boolean restrictedMode,     // 是否处于受限模式
        boolean setupCompleted,     // 初始化向导是否完成
        boolean mongoConnected,     // MongoDB 是否连接
        boolean storageConnected,   // 存储服务是否连接
        boolean chromaConnected,    // Chroma 是否连接
        String lastError            // 最后一次错误信息
    ) {}
}
