package com.ainovel.server.service.fanqie.impl;

import com.ainovel.server.service.fanqie.FanqieApiConfigService;
import com.ainovel.server.service.fanqie.FanqieNovelService;
import com.ainovel.server.service.fanqie.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 番茄小说服务实现 - 直连第三方 API 模式
 * 通过 qkfqapi.vv9v.cn 第三方 API 获取番茄小说数据
 */
@Slf4j
@Service
public class FanqieNovelServiceImpl implements FanqieNovelService {

    private final FanqieApiConfigService configService;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    public FanqieNovelServiceImpl(FanqieApiConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        initWebClient();
    }

    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(configService.getTimeout()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .compress(true)
                .keepAlive(true);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/javascript, */*; q=0.01")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
                .defaultHeader("Referer", "https://fanqienovel.com/")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .build();

        log.info("番茄小说服务初始化完成（直连第三方API模式），API: {}", configService.getApiBaseUrl());
    }

    @Override
    public Mono<String> login(String username, String password) {
        log.warn("直连API模式下login方法已废弃");
        return Mono.just("");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<FanqieSearchResult> searchNovels(String query) {
        if (!configService.isEnabled()) {
            return Mono.error(new RuntimeException("番茄小说服务未启用"));
        }

        String baseUrl = configService.getFullUrl("search");
        String fullUrl = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("key", query)
                .queryParam("tab_type", "3")
                .build()
                .toUriString();
        log.info("搜索番茄小说: {}, apiBaseUrl={}, fullUrl={}", query, configService.getApiBaseUrl(), fullUrl);

        return webClient.get()
                .uri(fullUrl)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> {
                            // 只对网络连接错误重试
                            String msg = throwable.getMessage();
                            return msg != null && (msg.contains("Connection") || msg.contains("prematurely closed"));
                        })
                        .doBeforeRetry(signal -> log.warn("番茄小说API请求失败，正在重试 ({}/3): {}", 
                                signal.totalRetries() + 1, signal.failure().getMessage())))
                .flatMap(response -> {
                    try {
                        Map<String, Object> json = objectMapper.readValue(response, Map.class);
                        if ((Integer) json.getOrDefault("code", 0) != 200) {
                            return Mono.error(new RuntimeException("API错误: " + json.get("message")));
                        }

                        Map<String, Object> data = (Map<String, Object>) json.get("data");
                        List<Map<String, Object>> items = (List<Map<String, Object>>) data.getOrDefault("data", new ArrayList<>());

                        List<FanqieNovelInfo> results = new ArrayList<>();
                        for (Map<String, Object> item : items) {
                            results.add(FanqieNovelInfo.builder()
                                    .id(String.valueOf(item.get("book_id")))
                                    .title((String) item.get("book_name"))
                                    .author((String) item.get("author"))
                                    .cover((String) item.get("thumb_url"))
                                    .description((String) item.get("abstract"))
                                    .category((String) item.get("category"))
                                    .build());
                        }

                        FanqieSearchResult result = new FanqieSearchResult();
                        result.setResults(results);
                        log.info("搜索成功，找到 {} 个结果", results.size());
                        return Mono.just(result);
                    } catch (Exception e) {
                        log.error("解析搜索结果失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<FanqieNovelListResponse> getNovelList(FanqieNovelListRequest request) {
        // 直连模式不支持本地小说列表，返回空
        log.warn("直连API模式不支持getNovelList，请使用searchNovels");
        FanqieNovelListResponse response = new FanqieNovelListResponse();
        response.setNovels(new ArrayList<>());
        response.setTotal(0);
        response.setPage(1);
        response.setPages(0);
        return Mono.just(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<FanqieNovelDetail> getNovelDetail(String novelId) {
        if (!configService.isEnabled()) {
            return Mono.error(new RuntimeException("番茄小说服务未启用"));
        }

        String url = configService.getFullUrl("detail") + "?book_id=" + novelId;
        log.info("获取番茄小说详情: {}", novelId);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        Map<String, Object> json = objectMapper.readValue(response, Map.class);
                        if ((Integer) json.getOrDefault("code", 0) != 200) {
                            return Mono.error(new RuntimeException("API错误: " + json.get("message")));
                        }

                        Map<String, Object> data = (Map<String, Object>) json.get("data");

                        FanqieNovelDetail detail = new FanqieNovelDetail();
                        detail.setId(String.valueOf(data.get("book_id")));
                        detail.setTitle((String) data.get("book_name"));
                        detail.setAuthor((String) data.get("author"));
                        detail.setCoverImageUrl((String) data.get("thumb_url"));
                        detail.setDescription((String) data.get("abstract"));
                        detail.setTags((String) data.get("category"));
                        detail.setStatus((String) data.getOrDefault("creation_status", "连载中"));

                        log.info("获取小说详情成功: {}", detail.getTitle());
                        return Mono.just(detail);
                    } catch (Exception e) {
                        log.error("解析小说详情失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<FanqieDownloadTask> addNovelDownloadTask(String novelId, Integer maxChapters) {
        log.warn("直连API模式不支持下载任务管理");
        return Mono.error(new UnsupportedOperationException("直连API模式不支持下载任务"));
    }

    @Override
    public Mono<FanqieTaskList> getDownloadTasks() {
        log.warn("直连API模式不支持下载任务管理");
        FanqieTaskList taskList = new FanqieTaskList();
        taskList.setTasks(new ArrayList<>());
        return Mono.just(taskList);
    }

    @Override
    public Mono<FanqieDownloadTask> getTaskStatus(String celeryTaskId) {
        log.warn("直连API模式不支持下载任务管理");
        return Mono.error(new UnsupportedOperationException("直连API模式不支持任务状态查询"));
    }

    @Override
    public Mono<FanqieDownloadTask> terminateTask(Long taskId) {
        log.warn("直连API模式不支持下载任务管理");
        return Mono.error(new UnsupportedOperationException("直连API模式不支持任务终止"));
    }

    @Override
    public Mono<String> deleteTask(Long taskId) {
        log.warn("直连API模式不支持下载任务管理");
        return Mono.error(new UnsupportedOperationException("直连API模式不支持任务删除"));
    }

    @Override
    public Mono<FanqieDownloadTask> redownloadTask(Long taskId) {
        log.warn("直连API模式不支持下载任务管理");
        return Mono.error(new UnsupportedOperationException("直连API模式不支持重新下载"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<FanqieChapterList> getChapterList(String novelId, Integer page, Integer perPage, String order) {
        if (!configService.isEnabled()) {
            return Mono.error(new RuntimeException("番茄小说服务未启用"));
        }

        String url = configService.getFullUrl("book") + "?book_id=" + novelId;
        log.info("获取番茄小说章节列表: novelId={}", novelId);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        Map<String, Object> json = objectMapper.readValue(response, Map.class);
                        if ((Integer) json.getOrDefault("code", 0) != 200) {
                            return Mono.error(new RuntimeException("API错误: " + json.get("message")));
                        }

                        Map<String, Object> data = (Map<String, Object>) json.get("data");
                        List<Map<String, Object>> items = (List<Map<String, Object>>) data.getOrDefault("item_list", new ArrayList<>());

                        List<FanqieChapter> chapters = new ArrayList<>();
                        int index = 1;
                        for (Map<String, Object> item : items) {
                            chapters.add(FanqieChapter.builder()
                                    .id(String.valueOf(item.get("item_id")))
                                    .title((String) item.get("title"))
                                    .novelId(novelId)
                                    .index(index++)
                                    .build());
                        }

                        // 简单分页处理
                        int pageNum = page != null ? page : 1;
                        int pageSize = perPage != null ? perPage : 50;
                        int start = (pageNum - 1) * pageSize;
                        int end = Math.min(start + pageSize, chapters.size());

                        FanqieChapterList chapterList = new FanqieChapterList();
                        chapterList.setChapters(start < chapters.size() ? chapters.subList(start, end) : new ArrayList<>());
                        chapterList.setTotal(chapters.size());
                        chapterList.setPage(pageNum);
                        chapterList.setPages((int) Math.ceil((double) chapters.size() / pageSize));

                        log.info("获取章节列表成功，共 {} 章", chapters.size());
                        return Mono.just(chapterList);
                    } catch (Exception e) {
                        log.error("解析章节列表失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<FanqieChapter> getChapterContent(String novelId, String chapterId) {
        if (!configService.isEnabled()) {
            return Mono.error(new RuntimeException("番茄小说服务未启用"));
        }

        String url = configService.getFullUrl("content") + "?item_id=" + chapterId + "&tab=%E5%B0%8F%E8%AF%B4";
        log.info("获取番茄小说章节内容: novelId={}, chapterId={}", novelId, chapterId);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        Map<String, Object> json = objectMapper.readValue(response, Map.class);
                        if ((Integer) json.getOrDefault("code", 0) != 200) {
                            return Mono.error(new RuntimeException("API错误: " + json.get("message")));
                        }

                        Map<String, Object> data = (Map<String, Object>) json.get("data");

                        FanqieChapter chapter = new FanqieChapter();
                        chapter.setId(chapterId);
                        chapter.setTitle((String) data.getOrDefault("title", ""));
                        chapter.setContent((String) data.getOrDefault("content", ""));
                        chapter.setNovelId(novelId);

                        log.info("获取章节内容成功: {}", chapter.getTitle());
                        return Mono.just(chapter);
                    } catch (Exception e) {
                        log.error("解析章节内容失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Flux<DataBuffer> downloadNovelFile(String novelId) {
        log.warn("直连API模式不支持EPUB下载");
        return Flux.error(new UnsupportedOperationException("直连API模式不支持EPUB下载"));
    }

    @Override
    public Flux<DataBuffer> getNovelCover(String novelId) {
        if (!configService.isEnabled()) {
            return Flux.error(new RuntimeException("番茄小说服务未启用"));
        }

        // 先获取详情拿到封面URL，再下载
        return getNovelDetail(novelId)
                .flatMapMany(detail -> {
                    if (detail.getCoverImageUrl() == null || detail.getCoverImageUrl().isEmpty()) {
                        return Flux.error(new RuntimeException("封面URL为空"));
                    }
                    return webClient.get()
                            .uri(detail.getCoverImageUrl())
                            .retrieve()
                            .bodyToFlux(DataBuffer.class);
                });
    }
}
