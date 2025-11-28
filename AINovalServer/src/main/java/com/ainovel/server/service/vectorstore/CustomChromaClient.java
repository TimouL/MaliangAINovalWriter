package com.ainovel.server.service.vectorstore;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 自定义Chroma客户端实现
 * 直接使用OkHttp调用Chroma REST API，支持Token认证
 * 支持 Chroma 1.0.0+ 的多租户 API v2
 */
@Slf4j
public class CustomChromaClient implements EmbeddingStore<TextSegment> {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DOCUMENT_KEY = "document";
    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String collectionName;
    private final String tenant;
    private final String database;
    private String collectionId;
    
    /**
     * Protected no-arg constructor for potential proxy support.
     * Should not be used directly.
     */
    protected CustomChromaClient() {
        this.httpClient = null;
        this.objectMapper = null;
        this.baseUrl = null;
        this.collectionName = null;
        this.tenant = null;
        this.database = null;
    }
    
    public CustomChromaClient(AuthenticatedChromaEmbeddingStore.Builder builder, OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = normalizeUrl(builder.getBaseUrl());
        this.collectionName = builder.getCollectionName();
        this.tenant = DEFAULT_TENANT;
        this.database = DEFAULT_DATABASE;
        
        // 初始化时获取或创建集合
        initializeCollection();
    }
    
    /**
     * 获取集合操作的基础URL路径 (Chroma 1.0.0+ 多租户 API)
     */
    private String getCollectionsBasePath() {
        return baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections";
    }
    
    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    
    private void initializeCollection() {
        try {
            // 先尝试获取已存在的集合
            this.collectionId = getOrCreateCollection();
            log.info("Chroma集合初始化成功，集合ID: {}, 集合名: {}", collectionId, collectionName);
        } catch (Exception e) {
            log.error("初始化Chroma集合失败", e);
            throw new RuntimeException("初始化Chroma集合失败: " + e.getMessage(), e);
        }
    }
    
    private String getOrCreateCollection() throws IOException {
        String collectionsPath = getCollectionsBasePath();
        
        // 首先尝试获取已存在的集合
        String getUrl = collectionsPath + "/" + collectionName;
        log.debug("尝试获取集合: {}", getUrl);
        Request getRequest = new Request.Builder()
                .url(getUrl)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(getRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                CollectionResponse collection = objectMapper.readValue(
                        response.body().string(), CollectionResponse.class);
                return collection.getId();
            }
        } catch (Exception e) {
            log.debug("集合不存在，将创建新集合: {}", collectionName);
        }
        
        // 集合不存在，创建新集合
        log.debug("创建集合: {}", collectionsPath);
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", collectionName);
        
        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(createBody), JSON);
        Request createRequest = new Request.Builder()
                .url(collectionsPath)
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                throw new IOException("创建集合失败: " + response.code() + " - " + errorBody);
            }
            
            CollectionResponse collection = objectMapper.readValue(
                    response.body().string(), CollectionResponse.class);
            return collection.getId();
        }
    }
    
    @Override
    public String add(Embedding embedding) {
        String id = UUID.randomUUID().toString();
        add(id, embedding);
        return id;
    }
    
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(Collections.singletonList(id), 
                Collections.singletonList(embedding), 
                Collections.singletonList(null));
    }
    
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        addInternal(Collections.singletonList(id), 
                Collections.singletonList(embedding), 
                Collections.singletonList(textSegment));
        return id;
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(e -> UUID.randomUUID().toString())
                .collect(Collectors.toList());
        List<TextSegment> segments = new ArrayList<>(Collections.nCopies(embeddings.size(), null));
        addInternal(ids, embeddings, segments);
        return ids;
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = embeddings.stream()
                .map(e -> UUID.randomUUID().toString())
                .collect(Collectors.toList());
        addInternal(ids, embeddings, textSegments);
        return ids;
    }
    
    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        addInternal(ids, embeddings, textSegments);
    }
    
    private void addInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (ids.isEmpty()) {
            return;
        }
        
        try {
            String url = getCollectionsBasePath() + "/" + collectionId + "/add";
            
            List<List<Float>> embeddingsList = embeddings.stream()
                    .map(this::toFloatList)
                    .collect(Collectors.toList());
            
            List<String> documents = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();
            
            for (TextSegment segment : textSegments) {
                if (segment != null) {
                    documents.add(segment.text());
                    Map<String, String> meta = new HashMap<>();
                    meta.put(DOCUMENT_KEY, segment.text());
                    if (segment.metadata() != null) {
                        segment.metadata().toMap().forEach((k, v) -> meta.put(k, String.valueOf(v)));
                    }
                    metadatas.add(meta);
                } else {
                    documents.add("");
                    metadatas.add(Collections.singletonMap(DOCUMENT_KEY, ""));
                }
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", ids);
            requestBody.put("embeddings", embeddingsList);
            requestBody.put("documents", documents);
            requestBody.put("metadatas", metadatas);
            
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    throw new RuntimeException("添加嵌入失败: " + response.code() + " - " + errorBody);
                }
            }
            
            log.debug("成功添加 {} 条嵌入到集合 {}", ids.size(), collectionName);
            
        } catch (Exception e) {
            log.error("添加嵌入到Chroma失败", e);
            throw new RuntimeException("添加嵌入失败: " + e.getMessage(), e);
        }
    }
    
    private List<Float> toFloatList(Embedding embedding) {
        float[] vector = embedding.vector();
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }
    
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        try {
            String url = getCollectionsBasePath() + "/" + collectionId + "/query";
            
            List<List<Float>> queryEmbeddings = Collections.singletonList(
                    toFloatList(request.queryEmbedding()));
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query_embeddings", queryEmbeddings);
            requestBody.put("n_results", request.maxResults());
            requestBody.put("include", List.of("documents", "metadatas", "distances"));
            
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    throw new RuntimeException("查询嵌入失败: " + response.code() + " - " + errorBody);
                }
                
                QueryResponse queryResponse = objectMapper.readValue(
                        response.body().string(), QueryResponse.class);
                
                List<EmbeddingMatch<TextSegment>> matches = parseQueryResponse(queryResponse);
                return new EmbeddingSearchResult<>(matches);
            }
            
        } catch (Exception e) {
            log.error("从Chroma查询嵌入失败", e);
            throw new RuntimeException("查询嵌入失败: " + e.getMessage(), e);
        }
    }
    
    private List<EmbeddingMatch<TextSegment>> parseQueryResponse(QueryResponse response) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        
        if (response.getIds() == null || response.getIds().isEmpty() || 
            response.getIds().get(0) == null || response.getIds().get(0).isEmpty()) {
            return matches;
        }
        
        List<String> ids = response.getIds().get(0);
        List<String> documents = response.getDocuments() != null && !response.getDocuments().isEmpty() 
                ? response.getDocuments().get(0) : Collections.nCopies(ids.size(), "");
        List<Map<String, Object>> metadatas = response.getMetadatas() != null && !response.getMetadatas().isEmpty()
                ? response.getMetadatas().get(0) : Collections.nCopies(ids.size(), new HashMap<>());
        List<Double> distances = response.getDistances() != null && !response.getDistances().isEmpty()
                ? response.getDistances().get(0) : Collections.nCopies(ids.size(), 0.0);
        
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String document = i < documents.size() ? documents.get(i) : "";
            Map<String, Object> meta = i < metadatas.size() ? metadatas.get(i) : new HashMap<>();
            Double distance = i < distances.size() ? distances.get(i) : 0.0;
            
            // Chroma返回的是距离，转换为相似度分数 (1 - distance/2，因为余弦距离范围是[0,2])
            double score = 1.0 - (distance / 2.0);
            
            // 从元数据中获取文档内容（如果document为空）
            String text = document;
            if ((text == null || text.isEmpty()) && meta.containsKey(DOCUMENT_KEY)) {
                text = String.valueOf(meta.get(DOCUMENT_KEY));
            }
            
            Metadata metadata = new Metadata();
            if (meta != null) {
                meta.forEach((k, v) -> {
                    if (!DOCUMENT_KEY.equals(k) && v != null) {
                        metadata.put(k, String.valueOf(v));
                    }
                });
            }
            
            TextSegment segment = TextSegment.from(text != null ? text : "", metadata);
            matches.add(new EmbeddingMatch<>(score, id, null, segment));
        }
        
        return matches;
    }
    
    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        try {
            String url = getCollectionsBasePath() + "/" + collectionId + "/delete";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", new ArrayList<>(ids));
            
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.warn("删除嵌入失败: {} - {}", response.code(), errorBody);
                }
            }
            
            log.debug("成功从集合 {} 删除 {} 条嵌入", collectionName, ids.size());
            
        } catch (Exception e) {
            log.error("从Chroma删除嵌入失败", e);
            throw new RuntimeException("删除嵌入失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void removeAll(Filter filter) {
        log.warn("Chroma不支持按过滤器删除，此操作将被忽略");
    }
    
    @Override
    public void removeAll() {
        log.warn("删除全部嵌入操作需谨慎，建议使用删除并重新创建集合的方式");
        // 为安全起见，不实现删除全部功能
    }
    
    // ===== 响应数据类 =====
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CollectionResponse {
        private String id;
        private String name;
        private Map<String, Object> metadata;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class QueryResponse {
        private List<List<String>> ids;
        private List<List<String>> documents;
        private List<List<Map<String, Object>>> metadatas;
        private List<List<Double>> distances;
        @JsonProperty("embeddings")
        private List<List<List<Float>>> embeddings;
    }
}
