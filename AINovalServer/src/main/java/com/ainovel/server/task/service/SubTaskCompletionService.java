package com.ainovel.server.task.service;

import com.ainovel.server.task.event.internal.TaskCompletedEvent;
import com.ainovel.server.task.event.internal.TaskFailedEvent;
import com.ainovel.server.task.model.BackgroundTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子任务完成通知服务
 * 
 * 用于替代轮询机制，通过事件驱动的方式等待子任务完成。
 * 主任务可以注册等待的子任务列表，当所有子任务完成时收到通知。
 */
@Slf4j
@Service
public class SubTaskCompletionService {
    
    private final TaskStateService taskStateService;
    
    // 存储每个父任务的等待上下文
    // key: parentTaskId, value: 等待上下文
    private final ConcurrentHashMap<String, SubTaskWaitContext> waitContexts = new ConcurrentHashMap<>();
    
    public SubTaskCompletionService(TaskStateService taskStateService) {
        this.taskStateService = taskStateService;
    }
    
    /**
     * 等待所有子任务完成
     * 
     * @param parentTaskId 父任务ID
     * @param subTaskIds 子任务ID列表
     * @param timeout 超时时间
     * @return 所有子任务的结果
     */
    public Mono<List<SubTaskResult>> waitForSubTasks(
            String parentTaskId, 
            List<String> subTaskIds,
            Duration timeout) {
        
        if (subTaskIds == null || subTaskIds.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        log.info("注册子任务等待: parentTaskId={}, subTaskCount={}, timeout={}s", 
                parentTaskId, subTaskIds.size(), timeout.getSeconds());
        
        // 创建等待上下文
        SubTaskWaitContext context = new SubTaskWaitContext(parentTaskId, new HashSet<>(subTaskIds));
        waitContexts.put(parentTaskId, context);
        
        // 先检查是否有子任务已经完成（避免事件丢失）
        return checkAlreadyCompletedTasks(context)
                .then(context.getSink().asMono()
                        .timeout(timeout)
                        .doOnSuccess(results -> {
                            log.info("所有子任务已完成: parentTaskId={}, resultCount={}", 
                                    parentTaskId, results.size());
                            waitContexts.remove(parentTaskId);
                        })
                        .doOnError(error -> {
                            log.error("等待子任务失败: parentTaskId={}, error={}", 
                                    parentTaskId, error.getMessage());
                            waitContexts.remove(parentTaskId);
                        })
                        .doOnCancel(() -> {
                            log.warn("等待子任务被取消: parentTaskId={}", parentTaskId);
                            waitContexts.remove(parentTaskId);
                        }));
    }
    
    /**
     * 检查是否有子任务已经完成（防止事件先于注册到达）
     */
    private Mono<Void> checkAlreadyCompletedTasks(SubTaskWaitContext context) {
        return reactor.core.publisher.Flux.fromIterable(context.getPendingTaskIds())
                .flatMap(taskId -> taskStateService.getTask(taskId)
                        .filter(task -> isTerminalStatus(task.getStatus().name()))
                        .doOnNext(task -> {
                            log.debug("发现已完成的子任务: taskId={}, status={}", taskId, task.getStatus());
                            SubTaskResult result = new SubTaskResult(
                                    taskId, 
                                    task.getStatus().name(), 
                                    task.getResult(),
                                    task.getErrorInfo());
                            context.onSubTaskCompleted(result);
                        }))
                .then();
    }
    
    /**
     * 监听子任务完成事件
     */
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        String parentTaskId = event.getParentTaskId();
        if (parentTaskId == null || parentTaskId.isEmpty()) {
            return;
        }
        
        SubTaskWaitContext context = waitContexts.get(parentTaskId);
        if (context == null) {
            log.debug("收到子任务完成事件但无等待上下文: parentTaskId={}, taskId={}", 
                    parentTaskId, event.getTaskId());
            return;
        }
        
        log.info("收到子任务完成事件: parentTaskId={}, taskId={}", parentTaskId, event.getTaskId());
        
        SubTaskResult result = new SubTaskResult(
                event.getTaskId(), 
                "COMPLETED", 
                event.getResult(),
                null);
        context.onSubTaskCompleted(result);
    }
    
    /**
     * 监听子任务失败事件
     */
    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        String parentTaskId = event.getParentTaskId();
        if (parentTaskId == null || parentTaskId.isEmpty()) {
            return;
        }
        
        SubTaskWaitContext context = waitContexts.get(parentTaskId);
        if (context == null) {
            log.debug("收到子任务失败事件但无等待上下文: parentTaskId={}, taskId={}", 
                    parentTaskId, event.getTaskId());
            return;
        }
        
        log.info("收到子任务失败事件: parentTaskId={}, taskId={}, error={}", 
                parentTaskId, event.getTaskId(), event.getErrorInfo());
        
        SubTaskResult result = new SubTaskResult(
                event.getTaskId(), 
                "FAILED", 
                null,
                event.getErrorInfo());
        context.onSubTaskCompleted(result);
    }
    
    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }
    
    /**
     * 子任务等待上下文
     */
    private static class SubTaskWaitContext {
        private final String parentTaskId;
        private final Set<String> pendingTaskIds;
        private final List<SubTaskResult> completedResults;
        private final Sinks.One<List<SubTaskResult>> sink;
        
        public SubTaskWaitContext(String parentTaskId, Set<String> subTaskIds) {
            this.parentTaskId = parentTaskId;
            this.pendingTaskIds = new HashSet<>(subTaskIds);
            this.completedResults = Collections.synchronizedList(new ArrayList<>());
            this.sink = Sinks.one();
        }
        
        public Set<String> getPendingTaskIds() {
            return new HashSet<>(pendingTaskIds);
        }
        
        public Sinks.One<List<SubTaskResult>> getSink() {
            return sink;
        }
        
        public synchronized void onSubTaskCompleted(SubTaskResult result) {
            if (!pendingTaskIds.contains(result.getTaskId())) {
                return;
            }
            
            pendingTaskIds.remove(result.getTaskId());
            completedResults.add(result);
            
            log.debug("子任务完成: parentTaskId={}, taskId={}, pending={}", 
                    parentTaskId, result.getTaskId(), pendingTaskIds.size());
            
            if (pendingTaskIds.isEmpty()) {
                sink.tryEmitValue(new ArrayList<>(completedResults));
            }
        }
    }
    
    /**
     * 子任务结果
     */
    public static class SubTaskResult {
        private final String taskId;
        private final String status;
        private final Object result;
        private final Object errorInfo;
        
        public SubTaskResult(String taskId, String status, Object result, Object errorInfo) {
            this.taskId = taskId;
            this.status = status;
            this.result = result;
            this.errorInfo = errorInfo;
        }
        
        public String getTaskId() {
            return taskId;
        }
        
        public String getStatus() {
            return status;
        }
        
        public Object getResult() {
            return result;
        }
        
        public Object getErrorInfo() {
            return errorInfo;
        }
        
        public boolean isSuccess() {
            return "COMPLETED".equals(status);
        }
    }
}
