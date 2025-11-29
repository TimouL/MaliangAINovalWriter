package com.ainovel.server.service.fanqie.impl;

import com.ainovel.server.service.fanqie.FanqieApiConfigService;
import com.ainovel.server.service.fanqie.FanqieNovelService;
import com.ainovel.server.service.fanqie.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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

        return Mono.fromCallable(() -> {
            String baseUrl = configService.getFullUrl("search");
            String fullUrl = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("key", query)
                    .queryParam("tab_type", "3")
                    .build()
                    .toUriString();
            log.info("搜索番茄小说: {}, apiBaseUrl={}, fullUrl={}", query, configService.getApiBaseUrl(), fullUrl);

            // 使用 RestTemplate 替代 WebClient，避免 Netty 兼容性问题
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json, text/javascript, */*; q=0.01");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            headers.set("Referer", "https://fanqienovel.com/");
            headers.set("X-Requested-With", "XMLHttpRequest");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("API 响应为空");
            }

            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            if ((Integer) json.getOrDefault("code", 0) != 200) {
                throw new RuntimeException("API错误: " + json.get("message"));
            }

            // 解析响应: data.search_tabs[tab_type=3].data[].book_data[0]
            Map<String, Object> data = (Map<String, Object>) json.get("data");
            List<Map<String, Object>> searchTabs = (List<Map<String, Object>>) data.getOrDefault("search_tabs", new ArrayList<>());
            
            List<FanqieNovelInfo> results = new ArrayList<>();
            for (Map<String, Object> tab : searchTabs) {
                Integer tabType = (Integer) tab.get("tab_type");
                if (tabType != null && tabType == 3) {
                    List<Map<String, Object>> tabData = (List<Map<String, Object>>) tab.get("data");
                    if (tabData != null) {
                        for (Map<String, Object> item : tabData) {
                            List<Map<String, Object>> bookDataList = (List<Map<String, Object>>) item.get("book_data");
                            if (bookDataList != null && !bookDataList.isEmpty()) {
                                Map<String, Object> bookData = bookDataList.get(0);
                                results.add(FanqieNovelInfo.builder()
                                        .id(String.valueOf(bookData.get("book_id")))
                                        .title((String) bookData.get("book_name"))
                                        .author((String) bookData.get("author"))
                                        .cover((String) bookData.get("thumb_url"))
                                        .description((String) bookData.get("abstract"))
                                        .category((String) bookData.get("category"))
                                        .build());
                            }
                        }
                    }
                    break;
                }
            }

            FanqieSearchResult result = new FanqieSearchResult();
            result.setResults(results);
            log.info("搜索成功，找到 {} 个结果", results.size());
            return result;
        }).retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(1))
                .filter(throwable -> {
                    String msg = throwable.getMessage();
                    return msg != null && (msg.contains("Connection") || msg.contains("timeout") || msg.contains("refused"));
                })
                .doBeforeRetry(signal -> log.warn("番茄小说API请求失败，正在重试 ({}/3): {}",
                        signal.totalRetries() + 1, signal.failure().getMessage())))
        .onErrorResume(e -> {
            log.error("搜索番茄小说失败: {}", e.getMessage());
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

        return Mono.fromCallable(() -> {
            String url = configService.getFullUrl("detail") + "?book_id=" + novelId;
            log.info("获取番茄小说详情: {}", novelId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set("Referer", "https://fanqienovel.com/");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("API 响应为空");
            }

            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            if ((Integer) json.getOrDefault("code", 0) != 200) {
                throw new RuntimeException("API错误: " + json.get("message"));
            }

            // 解析响应: data.data.xxx (参考 Fanqie-novel-Downloader)
            Map<String, Object> level1Data = (Map<String, Object>) json.get("data");
            Map<String, Object> bookData = level1Data;
            if (level1Data.containsKey("data") && level1Data.get("data") instanceof Map) {
                bookData = (Map<String, Object>) level1Data.get("data");
            }

            FanqieNovelDetail detail = new FanqieNovelDetail();
            detail.setId(String.valueOf(bookData.get("book_id")));
            detail.setTitle((String) bookData.get("book_name"));
            detail.setAuthor((String) bookData.get("author"));
            detail.setCoverImageUrl((String) bookData.get("thumb_url"));
            detail.setDescription((String) bookData.get("abstract"));
            detail.setTags((String) bookData.get("category"));
            detail.setStatus((String) bookData.getOrDefault("creation_status", "连载中"));

            log.info("获取小说详情成功: {}", detail.getTitle());
            return detail;
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

        return Mono.fromCallable(() -> {
            String url = configService.getFullUrl("book") + "?book_id=" + novelId;
            log.info("获取番茄小说章节列表: novelId={}", novelId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set("Referer", "https://fanqienovel.com/");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("API 响应为空");
            }

            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            if ((Integer) json.getOrDefault("code", 0) != 200) {
                throw new RuntimeException("API错误: " + json.get("message"));
            }

            // 解析响应: data.data.chapterListWithVolume[][] (参考 Fanqie-novel-Downloader)
            Map<String, Object> level1Data = (Map<String, Object>) json.get("data");
            Map<String, Object> level2Data = level1Data;
            if (level1Data.containsKey("data") && level1Data.get("data") instanceof Map) {
                level2Data = (Map<String, Object>) level1Data.get("data");
            }

            // 从 chapterListWithVolume 展平所有章节
            List<FanqieChapter> chapters = new ArrayList<>();
            List<List<Map<String, Object>>> chaptersByVolume = 
                    (List<List<Map<String, Object>>>) level2Data.getOrDefault("chapterListWithVolume", new ArrayList<>());
            
            int index = 1;
            for (List<Map<String, Object>> volumeChapters : chaptersByVolume) {
                if (volumeChapters != null) {
                    for (Map<String, Object> item : volumeChapters) {
                        // 注意: 字段名是 itemId (驼峰)，不是 item_id
                        chapters.add(FanqieChapter.builder()
                                .id(String.valueOf(item.get("itemId")))
                                .title((String) item.get("title"))
                                .novelId(novelId)
                                .index(index++)
                                .build());
                    }
                }
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
            return chapterList;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<FanqieChapter> getChapterContent(String novelId, String chapterId) {
        if (!configService.isEnabled()) {
            return Mono.error(new RuntimeException("番茄小说服务未启用"));
        }

        return Mono.fromCallable(() -> {
            // 参考 Fanqie-novel-Downloader: params = {"tab": "小说", "item_id": item_id}
            String url = configService.getFullUrl("content") + "?item_id=" + chapterId + "&tab=%E5%B0%8F%E8%AF%B4";
            log.info("获取番茄小说章节内容: novelId={}, chapterId={}", novelId, chapterId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set("Referer", "https://fanqienovel.com/");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("API 响应为空");
            }

            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            if ((Integer) json.getOrDefault("code", 0) != 200) {
                throw new RuntimeException("API错误: " + json.get("message"));
            }

            // 解析响应: data.content (参考 Fanqie-novel-Downloader)
            Map<String, Object> data = (Map<String, Object>) json.get("data");

            FanqieChapter chapter = new FanqieChapter();
            chapter.setId(chapterId);
            chapter.setTitle((String) data.getOrDefault("title", ""));
            chapter.setContent((String) data.getOrDefault("content", ""));
            chapter.setNovelId(novelId);

            log.info("获取章节内容成功，内容长度: {}", chapter.getContent() != null ? chapter.getContent().length() : 0);
            return chapter;
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
