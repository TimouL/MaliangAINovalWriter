

package com.ainovel.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import java.util.concurrent.TimeUnit;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
// import java.util.Map; // 移除通用Map转换后不再需要

/**
 * MongoDB配置类
 * 配置MongoDB连接、响应式支持、日志和统计功能
 */
@Configuration
@EnableReactiveMongoRepositories(basePackages = "com.ainovel.server.repository")
@EnableMongoRepositories(basePackages = "com.ainovel.server.repository")
public class MongoConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);
    
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;
    
    @Value("${spring.data.mongodb.database}")
    private String database;
    
    // 注意：这里不注入全局 ObjectMapper 以避免误用于通用 Map 转换
    public MongoConfig() {}
    
    /**
     * 创建MongoDB事件监听器，用于记录MongoDB操作日志
     * 注释掉以减少日志输出
     */
    // @Bean
    // public LoggingEventListener mongoEventListener() {
    //     return new LoggingEventListener();
    // }
    
    /**
     * 创建MongoDB映射调试监听器
     * 注释掉以减少日志输出
     */
    // @Bean
    // public AbstractMongoEventListener<Object> mongoMappingDebugListener() {
    //     return new AbstractMongoEventListener<Object>() {
    //         @Override
    //         public void onAfterLoad(AfterLoadEvent<Object> event) {
    //             if (logger.isTraceEnabled()) {
    //                 logger.trace("📥 MongoDB加载文档: collection={}, document={}", 
    //                     event.getCollectionName(), event.getDocument().keySet());
    //             }
    //         }
    //     };
    // }
    
    /**
     * 自定义ReactiveMongoTemplate，添加查询统计和日志功能
     * @param factory MongoDB数据库工厂
     * @param mappingMongoConverter 自定义的映射转换器（包含点号替换配置）
     * @return 自定义的ReactiveMongoTemplate
     */
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory factory, 
                                                       MappingMongoConverter mappingMongoConverter) {
        // 使用构造函数直接传入自定义的MappingMongoConverter
        ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory, mappingMongoConverter);
        
        // 启用日志记录
        logger.info("✅ 已配置ReactiveMongoTemplate，使用自定义MappingMongoConverter（支持点号替换）");
        return template;
    }
    
    /**
     * 创建MongoDB客户端，添加性能监控和连接池配置
     * @return MongoDB客户端
     */
    @Bean
    public MongoClient reactiveMongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        
        // 配置连接池参数，防止连接池耗尽
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .maxSize(100)              // 最大连接数
                .minSize(10)               // 最小连接数
                .maxWaitTime(30, TimeUnit.SECONDS)    // 等待连接超时
                .maxConnectionIdleTime(60, TimeUnit.SECONDS)  // 空闲连接超时
                .maxConnectionLifeTime(300, TimeUnit.SECONDS) // 连接最大生命周期
                .build();
        
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                .applicationName("AINovalWriter")
                .build();
        
        logger.info("创建MongoDB客户端，连接到: {}, 连接池: maxSize=100, minSize=10", database);
        return MongoClients.create(settings);
    }
    
    /**
     * 创建MongoDB数据库工厂
     * @param mongoClient MongoDB客户端
     * @return MongoDB数据库工厂
     */
    @Bean
    public ReactiveMongoDatabaseFactory reactiveMongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleReactiveMongoDatabaseFactory(mongoClient, database);
    }
    
    /**
     * 创建MongoDB事务管理器
     * @param dbFactory MongoDB数据库工厂
     * @return MongoDB事务管理器
     */
    @Bean
    public ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory dbFactory) {
        return new ReactiveMongoTransactionManager(dbFactory);
    }
    
    /**
     * 配置自定义MongoDB转换器
     * @return 自定义转换器配置
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions(SafeMapConverter safeMapConverter) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        
        // 日期/时间转换器
        converters.add(new DateToInstantConverter());
        converters.add(new InstantToDateConverter());
        
        // 仅保留安全的Map读取与时间类型转换，避免过于宽泛的 Map<->Object 转换导致的Spring Data WARN
        
        // 安全的Map转换器 - 处理类型不匹配问题
        converters.add(safeMapConverter);
        
        logger.info("MongoDB自定义转换器配置完成，总计 {} 个转换器", converters.size());
        
        return new MongoCustomConversions(converters);
    }
    
    /**
     * 配置专门的MongoDB ObjectMapper来处理序列化/反序列化
     * 确保与JsonCreator注解配合工作，解决复杂嵌套对象映射问题
     */
    @Bean("mongoObjectMapper")
    public ObjectMapper mongoObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册JavaTime模块
        mapper.registerModule(new JavaTimeModule());
        
        // 配置反序列化行为
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        logger.info("MongoDB ObjectMapper配置完成，支持JsonCreator构造函数映射");
        
        return mapper;
    }
    
    /**
     * Date到Instant的转换器
     */
    @ReadingConverter
    public static class DateToInstantConverter implements Converter<Date, Instant> {
        @Override
        public Instant convert(Date source) {
            return source == null ? null : source.toInstant();
        }
    }
    
    /**
     * Instant到Date的转换器
     */
    @WritingConverter
    public static class InstantToDateConverter implements Converter<Instant, Date> {
        @Override
        public Date convert(Instant source) {
            return source == null ? null : Date.from(source);
        }
    }
    
    // 注意：通用的 Map<->Object 转换改由业务层的 TaskConversionConfig 控制，
    // 避免在全局转换器中过于宽泛，导致Spring Data发出非存储类型转换的警告。
} 