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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * 使用 HttpURLConnection 发送 GET 请求 (参考 Python requests 库的行为)
     * 关键配置:
     * - Connection: keep-alive (保持连接)
     * - Accept-Encoding: identity (禁用压缩，避免解码问题)
     * - 完整的浏览器请求头模拟
     */
    private String sendHttpGet(String url) throws Exception {
        java.net.URL urlObj = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        
        // 模拟浏览器请求头 (与 Python Fanqie-novel-Downloader 保持一致)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("Accept-Encoding", "identity");  // 禁用压缩，避免 gzip 解码问题
        conn.setRequestProperty("Connection", "keep-alive");     // 保持连接
        conn.setRequestProperty("Referer", "https://fanqienovel.com/");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Pragma", "no-cache");

        int responseCode = conn.getResponseCode();
        log.debug("HTTP 请求: url={}, responseCode={}", url, responseCode);
        
        if (responseCode != 200) {
            // 读取错误响应体以获取更多信息
            String errorBody = "";
            try (java.io.InputStream errorStream = conn.getErrorStream()) {
                if (errorStream != null) {
                    errorBody = new String(errorStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
            throw new RuntimeException("HTTP 错误: " + responseCode + ", body: " + errorBody);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            conn.disconnect();
        }
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
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String fullUrl = baseUrl + "?key=" + encodedQuery + "&tab_type=3";
            log.info("搜索番茄小说: {}, fullUrl={}", query, fullUrl);

            String responseBody = sendHttpGet(fullUrl);
            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("API 响应为空");
            }
            log.info("搜索响应长度: {}", responseBody.length());

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

            String responseBody = sendHttpGet(url);
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

            String responseBody = sendHttpGet(url);
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

            String responseBody = sendHttpGet(url);

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
