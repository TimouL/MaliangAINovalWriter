package com.ainovel.server.service.vectorstore;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 支持Token认证的Chroma嵌入存储
 * 通过OkHttp Interceptor在每个请求中注入认证头
 */
@Slf4j
public class AuthenticatedChromaEmbeddingStore implements EmbeddingStore<TextSegment> {
    
    private final EmbeddingStore<TextSegment> delegate;
    
    private AuthenticatedChromaEmbeddingStore(Builder builder) {
        boolean hasAuth = builder.authToken != null && !builder.authToken.trim().isEmpty();
        log.info("初始化Chroma嵌入存储（API v2），URL: {}, 集合: {}, 认证: {}", 
                builder.baseUrl, builder.collectionName, hasAuth ? "已启用" : "未启用");
        // 统一使用 CustomChromaClient (支持 Chroma API v2)
        this.delegate = createChromaStore(builder, hasAuth);
    }
    
    /**
     * 创建Chroma存储实例（使用自定义客户端，支持API v2）
     */
    private EmbeddingStore<TextSegment> createChromaStore(Builder builder, boolean withAuth) {
        // 创建OkHttpClient
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .writeTimeout(builder.timeout);
        
        // 如果启用认证，添加认证拦截器
        if (withAuth) {
            httpClientBuilder.addInterceptor(new ChromaAuthInterceptor(builder.authToken));
        }
        
        if (builder.logRequests) {
            httpClientBuilder.addInterceptor(chain -> {
                Request request = chain.request();
                log.debug("Chroma请求: {} {}", request.method(), request.url());
                return chain.proceed(request);
            });
        }
        
        if (builder.logResponses) {
            httpClientBuilder.addInterceptor(chain -> {
                Response response = chain.proceed(chain.request());
                log.debug("Chroma响应: {} {}", response.code(), response.message());
                return response;
            });
        }
        
        OkHttpClient httpClient = httpClientBuilder.build();
        
        // 使用自定义的Chroma客户端（支持API v2）
        return new CustomChromaClient(builder, httpClient);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String baseUrl;
        private String collectionName;
        private Duration timeout = Duration.ofSeconds(5);
        private boolean logRequests;
        private boolean logResponses;
        private String authToken;
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }
        
        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }
        
        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }
        
        // Getters for CustomChromaClient
        public String getBaseUrl() { return baseUrl; }
        public String getCollectionName() { return collectionName; }
        public Duration getTimeout() { return timeout; }
        public boolean isLogRequests() { return logRequests; }
        public boolean isLogResponses() { return logResponses; }
        public String getAuthToken() { return authToken; }
        
        public AuthenticatedChromaEmbeddingStore build() {
            return new AuthenticatedChromaEmbeddingStore(this);
        }
    }
    
    /**
     * Chroma认证拦截器
     */
    private static class ChromaAuthInterceptor implements Interceptor {
        private final String authToken;
        
        public ChromaAuthInterceptor(String authToken) {
            this.authToken = authToken;
        }
        
        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request.Builder requestBuilder = originalRequest.newBuilder()
                    .header("X-Chroma-Token", authToken);
            return chain.proceed(requestBuilder.build());
        }
    }
    
    // 委托方法实现
    @Override
    public String add(Embedding embedding) {
        return delegate.add(embedding);
    }
    
    @Override
    public void add(String id, Embedding embedding) {
        delegate.add(id, embedding);
    }
    
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return delegate.add(embedding, textSegment);
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return delegate.addAll(embeddings);
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        return delegate.addAll(embeddings, textSegments);
    }
    
    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        delegate.addAll(ids, embeddings, textSegments);
    }
    
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return delegate.search(request);
    }
    
    @Override
    public void removeAll(Collection<String> ids) {
        delegate.removeAll(ids);
    }
    
    @Override
    public void removeAll(Filter filter) {
        delegate.removeAll(filter);
    }
    
    @Override
    public void removeAll() {
        delegate.removeAll();
    }
}
