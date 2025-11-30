package com.ainovel.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理器
 * 维护用户ID到SSE Sink的映射，支持主动向指定用户推送事件
 */
@Slf4j
@Service
public class SseConnectionManager {

    // 用户ID -> SSE Sink 映射
    private final Map<String, Sinks.Many<ServerSentEvent<Map<String, Object>>>> userSinks = new ConcurrentHashMap<>();
    
    // 用户ID -> 连接时间戳
    private final Map<String, Long> connectionTimestamps = new ConcurrentHashMap<>();

    /**
     * 注册用户的 SSE Sink
     */
    public void registerSink(String userId, Sinks.Many<ServerSentEvent<Map<String, Object>>> sink) {
        // 如果已有旧连接，先关闭
        Sinks.Many<ServerSentEvent<Map<String, Object>>> oldSink = userSinks.put(userId, sink);
        if (oldSink != null) {
            log.info("[SSE Manager] 用户 {} 已有旧连接，关闭旧连接", userId);
            try {
                oldSink.tryEmitComplete();
            } catch (Exception e) {
                log.warn("[SSE Manager] 关闭旧连接失败: {}", e.getMessage());
            }
        }
        connectionTimestamps.put(userId, System.currentTimeMillis());
        log.info("[SSE Manager] 注册用户 {} 的 SSE 连接，当前总连接数: {}", userId, userSinks.size());
    }

    /**
     * 注销用户的 SSE Sink
     */
    public void unregisterSink(String userId) {
        userSinks.remove(userId);
        connectionTimestamps.remove(userId);
        log.info("[SSE Manager] 注销用户 {} 的 SSE 连接，当前总连接数: {}", userId, userSinks.size());
    }

    /**
     * 强制用户登出（发送 complete 信号）
     * 
     * @param userId 用户ID
     * @param reason 登出原因
     */
    public void forceLogout(String userId, String reason) {
        Sinks.Many<ServerSentEvent<Map<String, Object>>> sink = userSinks.get(userId);
        if (sink != null) {
            log.info("[SSE Manager] 强制用户 {} 登出，原因: {}", userId, reason);
            try {
                // 发送 complete 事件
                ServerSentEvent<Map<String, Object>> completeSse = ServerSentEvent.<Map<String, Object>>builder()
                        .event("complete")
                        .data(Map.of(
                            "data", "[DONE]",
                            "reason", reason,
                            "forceLogout", true
                        ))
                        .build();
                sink.tryEmitNext(completeSse);
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.warn("[SSE Manager] 发送登出信号失败: {}", e.getMessage());
            }
            // 清理连接
            unregisterSink(userId);
        } else {
            log.debug("[SSE Manager] 用户 {} 没有活跃的 SSE 连接", userId);
        }
    }

    /**
     * 向指定用户推送事件
     */
    public boolean pushEvent(String userId, Map<String, Object> event) {
        Sinks.Many<ServerSentEvent<Map<String, Object>>> sink = userSinks.get(userId);
        if (sink != null) {
            try {
                ServerSentEvent<Map<String, Object>> sse = ServerSentEvent.<Map<String, Object>>builder()
                        .event("message")
                        .data(event)
                        .build();
                Sinks.EmitResult result = sink.tryEmitNext(sse);
                return result.isSuccess();
            } catch (Exception e) {
                log.warn("[SSE Manager] 推送事件到用户 {} 失败: {}", userId, e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 检查用户是否有活跃连接
     */
    public boolean hasActiveConnection(String userId) {
        return userSinks.containsKey(userId);
    }

    /**
     * 获取当前活跃连接数
     */
    public int getActiveConnectionCount() {
        return userSinks.size();
    }

    /**
     * 获取用户连接时间戳
     */
    public Long getConnectionTimestamp(String userId) {
        return connectionTimestamps.get(userId);
    }
}
