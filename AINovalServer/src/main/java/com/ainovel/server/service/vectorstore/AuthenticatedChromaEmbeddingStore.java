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
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
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
        if (builder.authToken != null && !builder.authToken.trim().isEmpty()) {
            log.info("初始化带认证的Chroma嵌入存储，URL: {}, 集合: {}", builder.baseUrl, builder.collectionName);
            this.delegate = createAuthenticatedStore(builder);
        } else {
            log.info("初始化Chroma嵌入存储（无认证），URL: {}, 集合: {}", builder.baseUrl, builder.collectionName);
            this.delegate = ChromaEmbeddingStore.builder()
                    .baseUrl(builder.baseUrl)
                    .collectionName(builder.collectionName)
                    .timeout(builder.timeout)
                    .logRequests(builder.logRequests)
                    .logResponses(builder.logResponses)
                    .build();
        }
    }
    
    /**
     * 创建带认证的存储实例
     * 由于ChromaClient是包级私有的，我们需要通过反射来注入自定义的OkHttpClient
     */
    private EmbeddingStore<TextSegment> createAuthenticatedStore(Builder builder) {
        try {
            // 创建带认证拦截器的OkHttpClient
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                    .callTimeout(builder.timeout)
                    .connectTimeout(builder.timeout)
                    .readTimeout(builder.timeout)
                    .writeTimeout(builder.timeout)
                    .addInterceptor(new ChromaAuthInterceptor(builder.authToken));
            
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
            
            // 使用反射创建ChromaEmbeddingStore并注入自定义HttpClient
            // 由于LangChain4j 1.0.0-beta3的ChromaClient不支持自定义HttpClient，
            // 我们需要使用一个变通方案：通过系统属性或环境变量来配置
            
            // 方案：直接使用标准构建器，但在请求前通过代理方式添加认证
            // 这里我们采用更可靠的方式：创建一个HTTP代理来注入认证头
            
            // 实际上，最简单的方式是使用Retrofit + OkHttp直接调用Chroma API
            // 但为了最小化改动，我们使用ChromaEmbeddingStore并接受其限制
            
            // 注意：由于LangChain4j的限制，我们需要使用自定义的Chroma客户端实现
            return new CustomChromaClient(builder, httpClient);
            
        } catch (Exception e) {
            log.error("创建带认证的Chroma存储失败，回退到无认证模式", e);
            return ChromaEmbeddingStore.builder()
                    .baseUrl(builder.baseUrl)
                    .collectionName(builder.collectionName)
                    .timeout(builder.timeout)
                    .logRequests(builder.logRequests)
                    .logResponses(builder.logResponses)
                    .build();
        }
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
