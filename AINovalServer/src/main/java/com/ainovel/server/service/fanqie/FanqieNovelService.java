package com.ainovel.server.service.fanqie;

import com.ainovel.server.service.fanqie.dto.*;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 番茄小说下载服务接口
 * 提供与番茄小说下载服务的集成功能
 */
public interface FanqieNovelService {
    
    /**
     * 用户登录，获取访问令牌
     * @deprecated 直连API模式下不需要登录
     */
    @Deprecated
    Mono<String> login(String username, String password);
    
    /**
     * 搜索小说
     * 
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    Mono<FanqieSearchResult> searchNovels(String query);
    
    /**
     * 获取小说列表（支持筛选、搜索和排序）
     * 
     * @param request 查询参数
     * @return 小说列表响应
     */
    Mono<FanqieNovelListResponse> getNovelList(FanqieNovelListRequest request);
    
    /**
     * 获取小说详情
     * 
     * @param novelId 小说ID
     * @return 小说详细信息
     */
    Mono<FanqieNovelDetail> getNovelDetail(String novelId);
    
    /**
     * 添加小说并开始下载
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<FanqieDownloadTask> addNovelDownloadTask(String novelId, Integer maxChapters);
    
    /**
     * 获取下载任务列表
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<FanqieTaskList> getDownloadTasks();
    
    /**
     * 获取任务状态（通过Celery Task ID）
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<FanqieDownloadTask> getTaskStatus(String celeryTaskId);
    
    /**
     * 终止下载任务
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<FanqieDownloadTask> terminateTask(Long taskId);
    
    /**
     * 删除任务记录
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<String> deleteTask(Long taskId);
    
    /**
     * 重新下载任务
     * @deprecated 直连API模式不支持下载任务管理
     */
    @Deprecated
    Mono<FanqieDownloadTask> redownloadTask(Long taskId);
    
    /**
     * 获取小说章节列表
     * 
     * @param novelId 小说ID
     * @param page 页码
     * @param perPage 每页数量
     * @param order 排序方式（asc/desc）
     * @return 章节列表
     */
    Mono<FanqieChapterList> getChapterList(String novelId, Integer page, Integer perPage, String order);
    
    /**
     * 获取章节内容
     * 
     * @param novelId 小说ID
     * @param chapterId 章节ID
     * @return 章节详细信息
     */
    Mono<FanqieChapter> getChapterContent(String novelId, String chapterId);
    
    /**
     * 下载小说文件（EPUB）
     * @deprecated 直连API模式不支持EPUB下载
     */
    @Deprecated
    Flux<DataBuffer> downloadNovelFile(String novelId);
    
    /**
     * 获取小说封面图片
     * 
     * @param novelId 小说ID
     * @return 封面图片数据流
     */
    Flux<DataBuffer> getNovelCover(String novelId);
}



