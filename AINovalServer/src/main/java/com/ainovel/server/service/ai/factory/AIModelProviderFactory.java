package com.ainovel.server.service.ai.factory;

import com.ainovel.server.config.ProxyConfig;
import com.ainovel.server.service.ai.AIModelProvider;
import com.ainovel.server.service.ai.AnthropicModelProvider;
import com.ainovel.server.service.ai.GrokModelProvider;
import com.ainovel.server.service.ai.TracingAIModelProviderDecorator;
import com.ainovel.server.service.ai.langchain4j.AnthropicLangChain4jModelProvider;
// import com.ainovel.server.service.ai.genai.GoogleGenAIGeminiModelProvider; // 不再使用 REST 回退
// import com.ainovel.server.service.ai.genai.GoogleGenAIGeminiSdkProvider;
import com.ainovel.server.service.ai.langchain4j.LangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.OpenAILangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.GeminiLangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.OpenRouterLangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.SiliconFlowLangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.TogetherAILangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.DoubaoLangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.ZhipuLangChain4jModelProvider;
import com.ainovel.server.service.ai.langchain4j.QwenLangChain4jModelProvider;
import com.ainovel.server.service.ai.genai.GoogleGenAIGeminiModelProvider;
import com.ainovel.server.service.ai.observability.ChatModelListenerManager;
import com.ainovel.server.service.ai.observability.TraceContextManager;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * AI模型提供商工厂类
 * 使用工厂方法模式创建不同类型的AI模型提供商实例
 * 现在使用装饰器模式为所有Provider添加追踪功能
 */
@Slf4j
@Component
public class AIModelProviderFactory {

    private final ProxyConfig proxyConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatModelListenerManager listenerManager;
    private final TraceContextManager traceContextManager;

    @Autowired
    public AIModelProviderFactory(ProxyConfig proxyConfig, 
                                 ApplicationEventPublisher eventPublisher,
                                 ChatModelListenerManager listenerManager,
                                 TraceContextManager traceContextManager) {
        this.proxyConfig = proxyConfig;
        this.eventPublisher = eventPublisher;
        this.listenerManager = listenerManager;
        this.traceContextManager = traceContextManager;
        
        log.info("🚀 AIModelProviderFactory 初始化完成，监听器管理器: {}", listenerManager.getListenerInfo());
    }

    /**
     * 创建AI模型提供商实例
     *
     * @param providerName 提供商名称
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     * @return 经过追踪装饰的AI模型提供商实例
     */
    public AIModelProvider createProvider(String providerName, String modelName, String apiKey, String apiEndpoint) {
        return createProvider(providerName, modelName, apiKey, apiEndpoint, true);
    }

    /**
     * 创建AI模型提供商实例（可选择是否启用可观测性/监听器/追踪装饰）
     *
     * @param providerName 提供商名称
     * @param modelName 模型名称
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     * @param enableObservability 是否启用监听器与追踪装饰（true=启用，false=禁用）
     * @return AI模型提供商实例（可能已被追踪装饰器包装）
     */
    public AIModelProvider createProvider(String providerName, String modelName, String apiKey, String apiEndpoint, boolean enableObservability) {
        if (enableObservability) {
            log.info("创建AI模型提供商: {}, 模型: {}", providerName, modelName);
        } else {
            log.debug("创建AI模型提供商（禁用可观测）: {}, 模型: {}", providerName, modelName);
        }

        // 1. 创建具体的、未被装饰的Provider实例，并按需注入监听器管理器
        ChatModelListenerManager lm = enableObservability ? listenerManager : null;

        // 统一端点优先：有自定义端点时使用用户端点，否则回退官方默认
        String endpoint = StringUtils.hasText(apiEndpoint) ? apiEndpoint : null;

        AIModelProvider concreteProvider = switch (providerName.toLowerCase()) {
            case "openai" -> new OpenAILangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "anthropic" -> new AnthropicLangChain4jModelProvider(modelName, apiKey, endpoint, lm);
            case "gemini" -> {
                // LangChain4j Gemini 不支持自定义 baseUrl；有端点时改用 REST Provider 以尊重用户端点
                if (StringUtils.hasText(endpoint)) {
                    yield new GoogleGenAIGeminiModelProvider(modelName, apiKey, endpoint);
                }
                yield new GeminiLangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            }
            //case "gemini-rest" -> new com.ainovel.server.service.ai.genai.GoogleGenAIGeminiModelProvider(modelName, apiKey, apiEndpoint);
            case "openrouter" -> new OpenRouterLangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "siliconflow" -> new SiliconFlowLangChain4jModelProvider(modelName, apiKey, endpoint, lm);
            case "togetherai" -> new TogetherAILangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "doubao", "ark", "volcengine", "bytedance" -> new DoubaoLangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "zhipu", "glm" -> new ZhipuLangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "qwen", "dashscope", "tongyi", "alibaba" -> new QwenLangChain4jModelProvider(modelName, apiKey, endpoint, proxyConfig, lm);
            case "x-ai", "grok" -> new GrokModelProvider(modelName, apiKey, endpoint, proxyConfig);
            case "anthropic-native" -> new AnthropicModelProvider(modelName, apiKey, endpoint);
            default -> throw new IllegalArgumentException("不支持的AI提供商: " + providerName);
        };

        // 仅对 REST 适配的 Gemini 实现设置代理，避免 LangChain4j 构造器已注入 ProxyConfig 时重复初始化
        if ("gemini-rest".equalsIgnoreCase(providerName) && proxyConfig != null && proxyConfig.isEnabled()) {
            try {
                concreteProvider.setProxy(proxyConfig.getHost(), proxyConfig.getPort());
            } catch (Exception e) {
                log.warn("为Gemini REST Provider设置代理失败: {}", e.getMessage());
            }
        }

        // 2. 可观测性：按需使用追踪装饰器
        if (enableObservability) {
            boolean isLangChain4j = isLangChain4jProvider(providerName);
            TracingAIModelProviderDecorator decoratedProvider = new TracingAIModelProviderDecorator(
                    concreteProvider, eventPublisher, traceContextManager, isLangChain4j);
            log.debug("已为Provider {}:{} 添加追踪装饰器", providerName, modelName);
            return decoratedProvider;
        } else {
            // 禁用可观测性：直接返回具体Provider（不注入监听器、不包裹装饰器）
            return concreteProvider;
        }
    }

    /**
     * 工具调用专用 Provider 工厂：
     * - gemini/gemini-rest 强制返回 LangChain4j 实现（支持工具规范的直连调用）
     * - 其他 provider 复用默认 createProvider 逻辑
     */
    public AIModelProvider createToolCallProvider(String providerName, String modelName, String apiKey, String apiEndpoint) {
        String p = providerName != null ? providerName.toLowerCase() : "";
        if ("gemini".equals(p) || "gemini-rest".equals(p)) {
            // 工具调用分支：强制使用 LangChain4j Gemini Provider（函数调用直连）
            AIModelProvider concrete;
            if (StringUtils.hasText(apiEndpoint)) {
                concrete = new GoogleGenAIGeminiModelProvider(modelName, apiKey, apiEndpoint);
            } else {
                concrete = new GeminiLangChain4jModelProvider(modelName, apiKey, apiEndpoint, proxyConfig, listenerManager);
            }
            TracingAIModelProviderDecorator decorated = new TracingAIModelProviderDecorator(
                    concrete, eventPublisher, traceContextManager, true /* is LangChain4j */);
            log.debug("工具调用分支: 使用 LangChain4j Gemini Provider 包装追踪: {}", modelName);
            return decorated;
        }
        return createProvider(providerName, modelName, apiKey, apiEndpoint);
    }

    

    /**
     * 通过提供商名称判断是否使用LangChain4j实现
     *
     * @param providerName 提供商名称
     * @return 是否使用LangChain4j实现
     */
    public boolean isLangChain4jProvider(String providerName) {
        String lowerCaseProvider = providerName.toLowerCase();
        
        return switch (lowerCaseProvider) {
            case "openai", "anthropic", "openrouter", "siliconflow", "togetherai" -> true;
            case "gemini" -> false;
            case "doubao", "ark", "volcengine", "bytedance", "zhipu", "glm", "qwen", "dashscope", "tongyi", "alibaba" -> true;
            default -> false;
        };
    }

    /**
     * 获取提供商类型
     * 注意：由于现在所有Provider都被TracingAIModelProviderDecorator包装，
     * 这个方法需要获取被装饰的原始Provider类型
     *
     * @param provider AI模型提供商实例
     * @return 提供商类型
     */
    public String getProviderType(AIModelProvider provider) {
        // 如果是装饰器，获取被装饰的原始Provider
        if (provider instanceof TracingAIModelProviderDecorator) {
            // 通过反射或者添加getter方法获取被装饰的对象
            // 这里简化处理，直接通过provider名称判断
            String providerName = provider.getProviderName().toLowerCase();
            return switch (providerName) {
                case "openai", "anthropic", "openrouter", "siliconflow", "togetherai",
                     "doubao", "ark", "volcengine", "bytedance", "zhipu", "glm", "qwen", "dashscope", "tongyi", "alibaba" -> "langchain4j";
                case "gemini" -> "genai";
                case "x-ai", "grok" -> "x-ai";
                default -> "unknown";
            };
        }
        
        // 原有逻辑保持不变（虽然现在基本不会执行到这里）
        if (provider instanceof LangChain4jModelProvider) {
            return "langchain4j";
        } else if (provider instanceof GrokModelProvider) {
            return "x-ai";
        } else if (provider instanceof AnthropicModelProvider) {
            return "anthropic-native";
        } else {
            return "unknown";
        }
    }
} 