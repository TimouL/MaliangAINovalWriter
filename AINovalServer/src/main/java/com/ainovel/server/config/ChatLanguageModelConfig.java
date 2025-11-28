package com.ainovel.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天语言模型配置类
 * 
 * @deprecated 此配置类已废弃，模型 API Key 应通过后台"公共模型管理"功能配置。
 *             仅当设置了 ai.openai.api-key 且 ai.openai.enabled=true 时才会创建 Bean。
 */
@Slf4j
@Deprecated
@Configuration
@ConditionalOnProperty(name = "ai.openai.enabled", havingValue = "true", matchIfMissing = false)
public class ChatLanguageModelConfig {

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.chat-model:deepseek/deepseek-v3-base:free}")
    private String openaiChatModel;

    @Value("${ai.openai.temperature:0.7}")
    private double temperature;

    @Value("${ai.openai.max-tokens:1024}")
    private int maxTokens;

    /**
     * 配置聊天语言模型
     *
     * @return 聊天语言模型
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("未配置 ai.openai.api-key，跳过 ChatLanguageModel Bean 创建");
            return null;
        }
        
        log.info("配置ChatLanguageModel，模型：{}", openaiChatModel);

        ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(openaiApiKey)
                .modelName(openaiChatModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .logRequests(true)
                .logResponses(true)
                .build();
        return chatLanguageModel;
    }
}
