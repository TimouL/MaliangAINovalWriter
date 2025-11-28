package com.ainovel.server.config;

import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.service.prompt.AIFeaturePromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 提示词提供器初始化器
 * 在应用启动时自动初始化所有 Provider 的系统模板
 * 
 * 注意：此初始化器必须在 AIPromptPresetInitializer 之前执行
 */
@Slf4j
@Component
@Order(1) // 确保在 AIPromptPresetInitializer 之前执行
public class PromptProviderInitializer implements ApplicationRunner {

    @Autowired
    private List<AIFeaturePromptProvider> promptProviders;

    @Value("${ainovel.ai.features.setting-tree-generation.init-on-startup:true}")
    private boolean settingTreeGenerationInitOnStartup;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("🚀 开始初始化所有提示词提供器的系统模板...");
        log.info("📊 发现 {} 个提示词提供器", promptProviders.size());
        
        try {
            Flux.fromIterable(promptProviders)
                    .filter(provider -> {
                        if (provider.getFeatureType() == AIFeatureType.SETTING_TREE_GENERATION && !settingTreeGenerationInitOnStartup) {
                            log.info("⏭️ 跳过 SETTING_TREE_GENERATION 提示词提供器的系统模板初始化（开关关闭）");
                            return false;
                        }
                        return true;
                    })
                    .flatMap(provider -> {
                        log.info("🔄 正在初始化提供器: {} ({})", 
                                provider.getClass().getSimpleName(), 
                                provider.getFeatureType());
                        
                        return provider.initializeSystemTemplate()
                                .map(templateId -> {
                                    log.info("✅ 提供器初始化成功: {} -> templateId: {}", 
                                            provider.getFeatureType(), templateId);
                                    return templateId;
                                })
                                .onErrorResume(error -> {
                                    log.error("❌ 提供器初始化失败: {}, error: {}", 
                                            provider.getFeatureType(), error.getMessage(), error);
                                    return reactor.core.publisher.Mono.empty();
                                });
                    })
                    .collectList()
                    .doOnSuccess(templateIds -> {
                        log.info("🎉 所有提示词提供器系统模板初始化完成！成功初始化 {} 个模板", templateIds.size());
                        
                        // 输出初始化统计
                        promptProviders.forEach(provider -> {
                            String templateId = provider.getSystemTemplateId();
                            if (templateId != null) {
                                log.info("📋 {}: {} -> {}", 
                                        provider.getFeatureType(), 
                                        provider.getTemplateIdentifier(), 
                                        templateId);
                            }
                        });
                    })
                    .doOnError(error -> log.error("💥 提示词提供器系统模板初始化过程中发生异常", error))
                    .block(); // 阻塞等待完成，确保在预设初始化前完成
                    
        } catch (Exception e) {
            log.error("💥 初始化提示词提供器系统模板时发生异常", e);
        }
    }

    /**
     * 获取指定功能类型的系统模板ID
     * 
     * @param featureType 功能类型
     * @return 模板ID，如果未找到则返回null
     */
    public String getSystemTemplateId(com.ainovel.server.domain.model.AIFeatureType featureType) {
        return promptProviders.stream()
                .filter(provider -> provider.getFeatureType() == featureType)
                .findFirst()
                .map(AIFeaturePromptProvider::getSystemTemplateId)
                .orElse(null);
    }

    /**
     * 获取所有已初始化的系统模板ID映射
     * 
     * @return 功能类型到模板ID的映射
     */
    public java.util.Map<com.ainovel.server.domain.model.AIFeatureType, String> getAllSystemTemplateIds() {
        return promptProviders.stream()
                .filter(provider -> provider.getSystemTemplateId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        AIFeaturePromptProvider::getFeatureType,
                        AIFeaturePromptProvider::getSystemTemplateId
                ));
    }
}