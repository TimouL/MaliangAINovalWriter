package com.ainovel.server.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ainovel.server.controller.AdminModelConfigController.CreditRateUpdate;
import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.domain.model.ModelPricing;
import com.ainovel.server.domain.model.PublicModelConfig;
import com.ainovel.server.dto.PublicModelConfigDetailsDTO;
import com.ainovel.server.repository.ModelPricingRepository;
import com.ainovel.server.repository.PublicModelConfigRepository;
import com.ainovel.server.service.ApiKeyValidator;
import com.ainovel.server.service.PublicModelConfigService;
import com.ainovel.server.service.ai.pricing.TokenUsageTrackingService;
import com.ainovel.server.web.dto.response.PublicModelResponseDto;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 公共模型配置服务实现
 */
@Slf4j
@Service
public class PublicModelConfigServiceImpl implements PublicModelConfigService {
    
    private final PublicModelConfigRepository publicModelConfigRepository;
    private final ModelPricingRepository modelPricingRepository;
    private final TokenUsageTrackingService tokenUsageTrackingService;
    private final ApiKeyValidator apiKeyValidator;
    private final StringEncryptor encryptor;
    
    @Autowired
    public PublicModelConfigServiceImpl(PublicModelConfigRepository publicModelConfigRepository,
                                       ModelPricingRepository modelPricingRepository,
                                       TokenUsageTrackingService tokenUsageTrackingService,
                                       ApiKeyValidator apiKeyValidator,
                                       StringEncryptor encryptor) {
        this.publicModelConfigRepository = publicModelConfigRepository;
        this.modelPricingRepository = modelPricingRepository;
        this.tokenUsageTrackingService = tokenUsageTrackingService;
        this.apiKeyValidator = apiKeyValidator;
        this.encryptor = encryptor;
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> createConfig(PublicModelConfig config) {
        return publicModelConfigRepository.existsByProviderAndModelId(config.getProvider(), config.getModelId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("模型配置已存在: " + config.getProvider() + ":" + config.getModelId()));
                    }
                    
                    // 加密所有API Key
                    encryptApiKeys(config);
                    
                    config.setCreatedAt(LocalDateTime.now());
                    config.setUpdatedAt(LocalDateTime.now());
                    
                    return publicModelConfigRepository.save(config);
                });
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> updateConfig(String id, PublicModelConfig config) {
        return publicModelConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + id)))
                .flatMap(existingConfig -> {
                    // 更新字段
                    existingConfig.setDisplayName(config.getDisplayName());
                    existingConfig.setEnabled(config.getEnabled());
                    existingConfig.setEnabledForFeatures(config.getEnabledForFeatures());
                    existingConfig.setCreditRateMultiplier(config.getCreditRateMultiplier());
                    existingConfig.setMaxConcurrentRequests(config.getMaxConcurrentRequests());
                    existingConfig.setDailyRequestLimit(config.getDailyRequestLimit());
                    existingConfig.setHourlyRequestLimit(config.getHourlyRequestLimit());
                    existingConfig.setPriority(config.getPriority());
                    existingConfig.setDescription(config.getDescription());
                    existingConfig.setTags(config.getTags());
                    existingConfig.setApiEndpoint(config.getApiEndpoint());
                    
                    // 如果有新的API Key列表，则加密后更新
                    if (config.getApiKeys() != null) {
                        encryptApiKeys(config);
                        existingConfig.setApiKeys(config.getApiKeys());
                    }
                    
                    existingConfig.setUpdatedAt(LocalDateTime.now());
                    
                    return publicModelConfigRepository.save(existingConfig);
                });
    }
    
    @Override
    @Transactional
    public Mono<Void> deleteConfig(String id) {
        return publicModelConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + id)))
                .flatMap(config -> publicModelConfigRepository.deleteById(id));
    }
    
    @Override
    public Mono<PublicModelConfig> findById(String id) {
        return publicModelConfigRepository.findById(id);
    }
    
    @Override
    public Flux<PublicModelConfig> findAll() {
        return publicModelConfigRepository.findAll();
    }
    
    @Override
    public Flux<PublicModelConfig> findAllEnabled() {
        return publicModelConfigRepository.findByEnabledTrue();
    }

    @Override
    public Flux<PublicModelResponseDto> getPublicModels() {
        log.info("获取公共模型列表");
        
        return publicModelConfigRepository.findByEnabledTrueOrderByPriorityDesc()
                .flatMap(config -> {
                    // 并行获取定价信息
                    Mono<ModelPricing> pricingMono = modelPricingRepository
                            .findByProviderAndModelIdAndActiveTrue(config.getProvider(), config.getModelId())
                            .switchIfEmpty(Mono.empty());
                    
                    return pricingMono
                            .map(pricing -> convertToPublicModelResponseDto(config, pricing))
                            .defaultIfEmpty(convertToPublicModelResponseDto(config, null));
                })
                .doOnNext(dto -> log.debug("转换公共模型: {}:{}", dto.getProvider(), dto.getModelId()))
                .doOnComplete(() -> log.info("公共模型列表获取完成"));
    }
    
    @Override
    public Mono<PublicModelConfig> findByProviderAndModelId(String provider, String modelId) {
        return publicModelConfigRepository.findByProviderAndModelId(provider, modelId);
    }
    
    @Override
    public Flux<PublicModelConfig> findByFeatureType(AIFeatureType featureType) {
        return publicModelConfigRepository.findByEnabledTrueAndEnabledForFeaturesContaining(featureType);
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> toggleStatus(String id, boolean enabled) {
        return publicModelConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + id)))
                .flatMap(config -> {
                    config.setEnabled(enabled);
                    config.setUpdatedAt(LocalDateTime.now());
                    return publicModelConfigRepository.save(config);
                });
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> addEnabledFeature(String id, AIFeatureType featureType) {
        return publicModelConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + id)))
                .flatMap(config -> {
                    config.addEnabledFeature(featureType);
                    config.setUpdatedAt(LocalDateTime.now());
                    return publicModelConfigRepository.save(config);
                });
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> removeEnabledFeature(String id, AIFeatureType featureType) {
        return publicModelConfigRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + id)))
                .flatMap(config -> {
                    config.removeEnabledFeature(featureType);
                    config.setUpdatedAt(LocalDateTime.now());
                    return publicModelConfigRepository.save(config);
                });
    }
    
    @Override
    @Transactional
    public Flux<PublicModelConfig> batchUpdateCreditRates(List<CreditRateUpdate> updates) {
        return Flux.fromIterable(updates)
                .flatMap(update -> 
                    publicModelConfigRepository.findById(update.getConfigId())
                            .flatMap(config -> {
                                config.setCreditRateMultiplier(update.getCreditRateMultiplier());
                                config.setUpdatedAt(LocalDateTime.now());
                                return publicModelConfigRepository.save(config);
                            })
                );
    }
    
    @Override
    public Mono<Boolean> existsByProviderAndModelId(String provider, String modelId) {
        return publicModelConfigRepository.existsByProviderAndModelId(provider, modelId);
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> validateConfig(String configId) {
        return publicModelConfigRepository.findById(configId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + configId)))
                .flatMap(config -> {
                    log.info("开始验证公共模型配置: {} - {}", config.getProvider(), config.getModelId());
                    
                    if (config.getApiKeys() == null || config.getApiKeys().isEmpty()) {
                        log.warn("模型配置没有API Key: {}", configId);
                        config.setIsValidated(false);
                        return publicModelConfigRepository.save(config);
                    }
                    
                    // 验证所有API Key
                    return Flux.fromIterable(config.getApiKeys())
                            .flatMap(entry -> validateSingleApiKey(config, entry))
                            .collectList()
                            .flatMap(validatedEntries -> {
                                config.setApiKeys(validatedEntries);
                                config.updateValidationStatus();
                                config.setUpdatedAt(LocalDateTime.now());
                                return publicModelConfigRepository.save(config);
                            });
                });
    }
    
    @Override
    public Mono<String> getActiveDecryptedApiKey(String provider, String modelId) {
        return publicModelConfigRepository.findByProviderAndModelId(provider, modelId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("公共模型配置不存在: " + provider + ":" + modelId)))
                .flatMap(config -> {
                    if (!config.getEnabled()) {
                        return Mono.error(new IllegalStateException("公共模型已禁用: " + provider + ":" + modelId));
                    }
                    
                    PublicModelConfig.ApiKeyEntry randomValidKey = config.getRandomValidApiKey();
                    if (randomValidKey == null) {
                        return Mono.error(new IllegalStateException("公共模型没有可用的API Key: " + provider + ":" + modelId));
                    }
                    
                    try {
                        String decryptedKey = encryptor.decrypt(randomValidKey.getApiKey());
                        log.debug("为公共模型 {}:{} 获取到可用的API Key", provider, modelId);
                        return Mono.just(decryptedKey);
                    } catch (Exception e) {
                        log.error("解密公共模型API Key失败: " + provider + ":" + modelId, e);
                        return Mono.error(new IllegalStateException("API Key解密失败"));
                    }
                });
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> addApiKey(String configId, String apiKey, String note) {
        if (!StringUtils.hasText(apiKey)) {
            return Mono.error(new IllegalArgumentException("API Key不能为空"));
        }
        
        return publicModelConfigRepository.findById(configId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + configId)))
                .flatMap(config -> {
                    try {
                        String encryptedKey = encryptor.encrypt(apiKey);
                        config.addApiKey(encryptedKey, note);
                        config.setUpdatedAt(LocalDateTime.now());
                        return publicModelConfigRepository.save(config);
                    } catch (Exception e) {
                        log.error("加密API Key失败", e);
                        return Mono.error(new IllegalStateException("API Key加密失败"));
                    }
                });
    }
    
    @Override
    @Transactional
    public Mono<PublicModelConfig> removeApiKey(String configId, String apiKeyId) {
        return publicModelConfigRepository.findById(configId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + configId)))
                .flatMap(config -> {
                    // 查找要删除的Key (按实际内容删除，因为ApiKeyEntry没有ID字段)
                    config.getApiKeys().removeIf(entry -> {
                        try {
                            String decryptedKey = encryptor.decrypt(entry.getApiKey());
                            return decryptedKey.equals(apiKeyId);
                        } catch (Exception e) {
                            log.warn("解密API Key失败，跳过该Key", e);
                            return false;
                        }
                    });
                    
                    config.updateValidationStatus();
                    config.setUpdatedAt(LocalDateTime.now());
                    return publicModelConfigRepository.save(config);
                });
    }

    @Override
    public Mono<PublicModelConfigDetailsDTO> getConfigDetails(String configId) {
        return publicModelConfigRepository.findById(configId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模型配置不存在: " + configId)))
                .flatMap(config -> {
                    // 并行获取定价信息和使用统计
                    Mono<ModelPricing> pricingMono = modelPricingRepository
                            .findByProviderAndModelIdAndActiveTrue(config.getProvider(), config.getModelId())
                            .switchIfEmpty(Mono.empty());
                    
                    Mono<TokenUsageTrackingService.TokenUsageStatistics> usageStatsMono = 
                            tokenUsageTrackingService.getProviderUsageStatistics(
                                config.getProvider() + ":" + config.getModelId(),
                                LocalDateTime.now().minusDays(30),
                                LocalDateTime.now())
                            .switchIfEmpty(Mono.empty());
                    
                    return Mono.zip(
                            Mono.just(config),
                            pricingMono.defaultIfEmpty(new ModelPricing()), // 提供默认值
                            usageStatsMono.defaultIfEmpty(createEmptyUsageStats()) // 提供默认值
                    ).map(tuple -> convertToDetailsDTO(tuple.getT1(), tuple.getT2(), tuple.getT3()));
                });
    }
    
    /**
     * 转换为公共模型响应DTO（安全版本，不含敏感信息）
     */
    private PublicModelResponseDto convertToPublicModelResponseDto(PublicModelConfig config, ModelPricing pricing) {
        // 构建性能指标
        PublicModelResponseDto.PerformanceMetrics performanceMetrics = null;
        if (pricing != null && pricing.getId() != null) {
            performanceMetrics = PublicModelResponseDto.PerformanceMetrics.builder()
                    .maxContextLength(pricing.getMaxContextTokens())
                    .maxOutputLength(pricing.getMaxContextTokens()) // 使用maxContextTokens作为最大输出长度的近似值
                    .averageResponseTime(null) // 可以从使用统计中获取
                    .inputPricePerThousandTokens(pricing.getInputPricePerThousandTokens() != null ? 
                                                 pricing.getInputPricePerThousandTokens().doubleValue() : null)
                    .outputPricePerThousandTokens(pricing.getOutputPricePerThousandTokens() != null ? 
                                                  pricing.getOutputPricePerThousandTokens().doubleValue() : null)
                    .build();
        }
        
        // 构建限制信息 (从配置中获取)
        PublicModelResponseDto.LimitationInfo limitations = PublicModelResponseDto.LimitationInfo.builder()
                .requestsPerMinute(config.getHourlyRequestLimit() != null && config.getHourlyRequestLimit() > 0 ? 
                                  config.getHourlyRequestLimit() / 60 : null)
                .requestsPerDay(config.getDailyRequestLimit() != null && config.getDailyRequestLimit() > 0 ? 
                               config.getDailyRequestLimit() : null)
                .requestsPerMonth(null) // 可以从配置中扩展
                .specialLimitations(null) // 可以从描述中提取
                .build();
        
        // 转换支持的功能为字符串列表
        List<String> supportedFeatures = config.getEnabledForFeatures() != null ?
                config.getEnabledForFeatures().stream()
                        .map(AIFeatureType::name)
                        .collect(Collectors.toList()) : List.of();
        
        return PublicModelResponseDto.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .modelId(config.getModelId())
                .displayName(config.getDisplayName())
                .description(config.getDescription())
                .creditRateMultiplier(config.getCreditRateMultiplier())
                .supportedFeatures(supportedFeatures)
                .tags(config.getTags())
                .performanceMetrics(performanceMetrics)
                .limitations(limitations)
                .priority(config.getPriority())
                .recommended(config.getPriority() != null && config.getPriority() >= 5) // 优先级>=5的标记为推荐
                .build();
    }
    
    /**
     * 加密配置中的所有API Key
     */
    private void encryptApiKeys(PublicModelConfig config) {
        if (config.getApiKeys() != null) {
            for (PublicModelConfig.ApiKeyEntry entry : config.getApiKeys()) {
                if (StringUtils.hasText(entry.getApiKey())) {
                    try {
                        entry.setApiKey(encryptor.encrypt(entry.getApiKey()));
                    } catch (Exception e) {
                        log.error("加密API Key失败", e);
                        throw new IllegalStateException("API Key加密失败");
                    }
                }
            }
        }
    }
    
    /**
     * 验证单个API Key
     */
    private Mono<PublicModelConfig.ApiKeyEntry> validateSingleApiKey(PublicModelConfig config, PublicModelConfig.ApiKeyEntry entry) {
        try {
            String decryptedKey = encryptor.decrypt(entry.getApiKey());
            return apiKeyValidator.validate(null, config.getProvider(), config.getModelId(), decryptedKey, config.getApiEndpoint())
                    .map(isValid -> {
                        entry.setIsValid(isValid);
                        entry.setLastValidatedAt(LocalDateTime.now());
                        if (isValid) {
                            entry.setValidationError(null);
                            log.info("API Key验证成功: {} - {}", config.getProvider(), config.getModelId());
                        } else {
                            entry.setValidationError("API Key验证失败");
                            log.warn("API Key验证失败:端点 {} -类型： {} -模型： {}", config.getApiEndpoint(), config.getProvider(), config.getModelId());
                        }
                        return entry;
                    })
                    .onErrorResume(error -> {
                        entry.setIsValid(false);
                        entry.setValidationError("验证过程出错: " + error.getMessage());
                        entry.setLastValidatedAt(LocalDateTime.now());
                        log.error("API Key验证出错: {} - {}", config.getProvider(), config.getModelId(), error);
                        return Mono.just(entry);
                    });
        } catch (Exception e) {
            entry.setIsValid(false);
            entry.setValidationError("API Key解密失败");
            entry.setLastValidatedAt(LocalDateTime.now());
            log.error("API Key解密失败: {} - {}", config.getProvider(), config.getModelId(), e);
            return Mono.just(entry);
        }
    }
    
    @Override
    public Flux<PublicModelConfigDetailsDTO> findAllWithDetails() {
        return publicModelConfigRepository.findAll()
                .flatMap(config -> {
                    // 并行获取定价信息和使用统计
                    Mono<ModelPricing> pricingMono = modelPricingRepository
                            .findByProviderAndModelIdAndActiveTrue(config.getProvider(), config.getModelId())
                            .switchIfEmpty(Mono.empty());
                    
                    Mono<TokenUsageTrackingService.TokenUsageStatistics> usageStatsMono = 
                            tokenUsageTrackingService.getProviderUsageStatistics(
                                config.getProvider() + ":" + config.getModelId(),
                                LocalDateTime.now().minusDays(30),
                                LocalDateTime.now())
                            .switchIfEmpty(Mono.empty());
                    
                    return Mono.zip(
                            Mono.just(config),
                            pricingMono.defaultIfEmpty(new ModelPricing()), // 提供默认值
                            usageStatsMono.defaultIfEmpty(createEmptyUsageStats()) // 提供默认值
                    ).map(tuple -> convertToDetailsDTO(tuple.getT1(), tuple.getT2(), tuple.getT3()));
                });
    }
    
    /**
     * 转换为详细DTO
     */
    private PublicModelConfigDetailsDTO convertToDetailsDTO(PublicModelConfig config, 
                                                           ModelPricing pricing, 
                                                           TokenUsageTrackingService.TokenUsageStatistics usageStats) {
        // 转换API Key状态（不包含实际Key值）
        List<PublicModelConfigDetailsDTO.ApiKeyStatusDTO> apiKeyStatuses = config.getApiKeys() != null 
                ? config.getApiKeys().stream()
                        .map(entry -> PublicModelConfigDetailsDTO.ApiKeyStatusDTO.builder()
                                .isValid(entry.getIsValid())
                                .validationError(entry.getValidationError())
                                .lastValidatedAt(entry.getLastValidatedAt())
                                .note(entry.getNote())
                                .build())
                        .collect(Collectors.toList())
                : List.of();
        
        // 构建定价信息DTO
        PublicModelConfigDetailsDTO.PricingInfoDTO pricingInfo = null;
        if (pricing != null && pricing.getId() != null) { // 检查是否有实际的定价数据
            pricingInfo = PublicModelConfigDetailsDTO.PricingInfoDTO.builder()
                    .modelName(pricing.getModelName())
                    .inputPricePerThousandTokens(pricing.getInputPricePerThousandTokens())
                    .outputPricePerThousandTokens(pricing.getOutputPricePerThousandTokens())
                    .unifiedPricePerThousandTokens(pricing.getUnifiedPricePerThousandTokens())
                    .maxContextTokens(pricing.getMaxContextTokens())
                    .supportsStreaming(pricing.getSupportsStreaming())
                    .pricingUpdatedAt(pricing.getUpdatedAt())
                    .hasPricingData(true)
                    .build();
        } else {
            pricingInfo = PublicModelConfigDetailsDTO.PricingInfoDTO.builder()
                    .hasPricingData(false)
                    .build();
        }
        
        // 构建使用统计DTO
        PublicModelConfigDetailsDTO.UsageStatisticsDTO usageStatisticsDTO = 
                PublicModelConfigDetailsDTO.UsageStatisticsDTO.builder()
                        .totalRequests(usageStats.totalRequests())
                        .totalInputTokens(usageStats.totalInputTokens())
                        .totalOutputTokens(usageStats.totalOutputTokens())
                        .totalTokens(usageStats.totalTokens())
                        .totalCost(usageStats.totalCost() != null ? usageStats.totalCost() : BigDecimal.ZERO)
                        .averageCostPerRequest(usageStats.averageCostPerRequest() != null ? usageStats.averageCostPerRequest() : BigDecimal.ZERO)
                        .averageCostPerToken(usageStats.averageCostPerToken() != null ? usageStats.averageCostPerToken() : BigDecimal.ZERO)
                        .last30DaysRequests(usageStats.totalRequests()) // 假设统计的就是30天数据
                        .last30DaysCost(usageStats.totalCost() != null ? usageStats.totalCost() : BigDecimal.ZERO)
                        .hasUsageData(usageStats.totalRequests() > 0)
                        .build();
        
        return PublicModelConfigDetailsDTO.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .modelId(config.getModelId())
                .displayName(config.getDisplayName())
                .enabled(config.getEnabled())
                .apiEndpoint(config.getApiEndpoint())
                .isValidated(config.getIsValidated())
                .apiKeyPoolStatus(config.getApiKeyPoolStatus())
                .apiKeyStatuses(apiKeyStatuses)
                .enabledForFeatures(config.getEnabledForFeatures())
                .creditRateMultiplier(config.getCreditRateMultiplier())
                .maxConcurrentRequests(config.getMaxConcurrentRequests())
                .dailyRequestLimit(config.getDailyRequestLimit())
                .hourlyRequestLimit(config.getHourlyRequestLimit())
                .priority(config.getPriority())
                .description(config.getDescription())
                .tags(config.getTags())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .pricingInfo(pricingInfo)
                .usageStatistics(usageStatisticsDTO)
                .build();
    }
    
    /**
     * 创建空的使用统计
     */
    private TokenUsageTrackingService.TokenUsageStatistics createEmptyUsageStats() {
        return new TokenUsageTrackingService.TokenUsageStatistics(
                "provider", "", LocalDateTime.now().minusDays(30), LocalDateTime.now(),
                0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null
        );
    }
}