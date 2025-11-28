package com.ainovel.server.config;

import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.domain.model.AIPromptPreset;
import com.ainovel.server.repository.AIPromptPresetRepository;
import com.ainovel.server.repository.EnhancedUserPromptTemplateRepository;
import com.ainovel.server.web.dto.request.UniversalAIRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.EnumMap;
import java.util.Collections;

/**
 * AI提示词预设初始化器
 * 在应用启动完成后自动初始化系统默认预设
 */
@Slf4j
@Component
@Order(2) // 确保在 PromptProviderInitializer 之后执行
public class AIPromptPresetInitializer implements ApplicationRunner {

    @Autowired
    private AIPromptPresetRepository presetRepository;

    @Autowired
    private EnhancedUserPromptTemplateRepository templateRepository;

    @Autowired
    private PromptProviderInitializer promptProviderInitializer;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ainovel.ai.features.setting-tree-generation.init-on-startup:true}")
    private boolean settingTreeGenerationInitOnStartup;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化系统默认AI预设...");
        
        try {
            initializeSystemPresets()
                    .doOnSuccess(unused -> log.info("系统默认AI预设初始化完成"))
                    .doOnError(error -> log.error("初始化系统默认AI预设失败", error))
                    .block(); // 阻塞等待完成，确保初始化完成后才继续
        } catch (Exception e) {
            log.error("初始化系统默认AI预设时发生异常", e);
        }
    }

    /**
     * 初始化系统预设
     */
    private Mono<Void> initializeSystemPresets() {
        List<Mono<AIPromptPreset>> presetMonos = new ArrayList<>();
        
        // 为每个AI功能类型创建系统预设
        for (AIFeatureType featureType : AIFeatureType.values()) {
            if (featureType == AIFeatureType.SETTING_TREE_GENERATION && !settingTreeGenerationInitOnStartup) {
                log.info("⏭️ 跳过 SETTING_TREE_GENERATION 系统预设初始化（开关关闭）");
                continue;
            }
            presetMonos.addAll(createSystemPresetsForFeature(featureType));
        }
        
        return Flux.merge(presetMonos).then();
    }

    /**
     * 为指定功能类型创建系统预设
     */
    private List<Mono<AIPromptPreset>> createSystemPresetsForFeature(AIFeatureType featureType) {
        List<Mono<AIPromptPreset>> presets = new ArrayList<>();
        
        if (featureType == AIFeatureType.TEXT_EXPANSION) {
            presets.add(createTextExpansionSystemPreset());
        } else if (featureType == AIFeatureType.TEXT_REFACTOR) {
            presets.add(createTextRefactorSystemPreset());
        } else if (featureType == AIFeatureType.TEXT_SUMMARY) {
            presets.add(createTextSummarySystemPreset());
        } else if (featureType == AIFeatureType.AI_CHAT) {
            presets.add(createChatSystemPreset());
        } else if (featureType == AIFeatureType.SCENE_TO_SUMMARY
                || featureType == AIFeatureType.SUMMARY_TO_SCENE
                || featureType == AIFeatureType.NOVEL_GENERATION
                || featureType == AIFeatureType.PROFESSIONAL_FICTION_CONTINUATION) {
            presets.add(createGenericSystemPreset(featureType));
        } else {
            // 为其他功能类型创建通用预设
            presets.add(createGenericSystemPreset(featureType));
        }
        
        return presets;
    }

    /**
     * 创建文本扩写系统预设
     */
    private Mono<AIPromptPreset> createTextExpansionSystemPreset() {
        String presetId = "system-text-expansion-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("expansion")
                                .modelConfigId("default-gpt-3.5")
                                .parameters(Map.of(
                                        "temperature", 0.7,
                                        "max_tokens", 2000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, AIFeatureType.TEXT_EXPANSION, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("标准文本扩写")
                                .presetDescription("系统默认的文本扩写预设，适用于大部分小说内容扩写场景")
                                .presetTags(Arrays.asList("系统预设", "文本扩写", "小说创作"))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的小说创作助手。请根据提供的内容进行扩写，保持故事的连贯性和角色性格的一致性。")
                                .userPrompt("请扩写以下内容：{input}\n\n上下文信息：{context}\n\n要求：\n1. 保持原有的写作风格\n2. 增加更多的细节描述\n3. 让情节发展更加自然流畅")
                                .aiFeatureType(AIFeatureType.TEXT_EXPANSION.name())
                                .templateId(getSystemTemplateId(AIFeatureType.TEXT_EXPANSION))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建文本扩写系统预设失败", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 创建文本重构系统预设
     */
    private Mono<AIPromptPreset> createTextRefactorSystemPreset() {
        String presetId = "system-text-refactor-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("refactor")
                                .modelConfigId("default-gpt-3.5")
                                .parameters(Map.of(
                                        "temperature", 0.6,
                                        "max_tokens", 2000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, AIFeatureType.TEXT_REFACTOR, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("标准文本重构")
                                .presetDescription("系统默认的文本重构预设，用于改善文字表达和故事结构")
                                .presetTags(Arrays.asList("系统预设", "文本重构", "优化"))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的文字编辑。请重构提供的内容，改善文字表达和故事结构，保持原有风格和特色。")
                                .userPrompt("请重构以下内容：{input}\n\n上下文信息：{context}\n\n要求：\n1. 改善文字表达和语言流畅度\n2. 优化故事结构和逻辑\n3. 保持原有的风格特色")
                                .aiFeatureType(AIFeatureType.TEXT_REFACTOR.name())
                                .templateId(getSystemTemplateId(AIFeatureType.TEXT_REFACTOR))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建文本重构系统预设失败", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 创建文本总结系统预设
     */
    private Mono<AIPromptPreset> createTextSummarySystemPreset() {
        String presetId = "system-text-summary-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("summary")
                                .modelConfigId("default-gpt-3.5")
                                .parameters(Map.of(
                                        "temperature", 0.3,
                                        "max_tokens", 1000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, AIFeatureType.TEXT_SUMMARY, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("标准文本总结")
                                .presetDescription("系统默认的文本总结预设，用于提取关键情节和重要信息")
                                .presetTags(Arrays.asList("系统预设", "文本总结", "内容概括"))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的文本分析师。请准确总结提供的内容，提取关键情节和重要信息。")
                                .userPrompt("请总结以下内容：{input}\n\n上下文信息：{context}\n\n要求：\n1. 提取关键情节和重要信息\n2. 保持总结的准确性和完整性\n3. 突出重要的故事转折点")
                                .aiFeatureType(AIFeatureType.TEXT_SUMMARY.name())
                                .templateId(getSystemTemplateId(AIFeatureType.TEXT_SUMMARY))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建文本总结系统预设失败", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 创建聊天系统预设
     */
    private Mono<AIPromptPreset> createChatSystemPreset() {
        String presetId = "system-chat-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("chat")
                                .modelConfigId("default-gpt-3.5")
                                .parameters(Map.of(
                                        "temperature", 0.7,
                                        "max_tokens", 2000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, AIFeatureType.AI_CHAT, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("智能创作助手")
                                .presetDescription("系统默认的AI聊天预设，专业的小说创作助手")
                                .presetTags(Arrays.asList("系统预设", "AI聊天", "创作助手"))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的小说创作助手，具有丰富的文学知识和创作经验。你可以帮助用户进行小说创作的各种任务。")
                                .userPrompt("{prompt}")
                                .aiFeatureType(AIFeatureType.AI_CHAT.name())
                                .templateId(getSystemTemplateId(AIFeatureType.AI_CHAT))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建聊天系统预设失败", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 创建场景生成系统预设
     */
    private Mono<AIPromptPreset> createSceneGenerationSystemPreset() {
        String presetId = "system-scene-generation-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("generation")
                                .modelConfigId("default-gpt-4")
                                .parameters(Map.of(
                                        "temperature", 0.8,
                                        "max_tokens", 3000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, AIFeatureType.SCENE_TO_SUMMARY, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("智能场景生成")
                                .presetDescription("系统默认的场景生成预设，用于创作新的故事场景")
                                .presetTags(Arrays.asList("系统预设", "场景生成", "内容创作"))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的小说创作者。请根据提供的信息创作引人入胜的故事场景，保持故事的连贯性和吸引力。")
                                .userPrompt("请根据以下信息生成场景：{prompt}\n\n背景设定：{context}\n\n要求：\n1. 创作生动有趣的故事情节\n2. 保持角色性格的一致性\n3. 符合整体故事背景和风格")
                                .aiFeatureType(AIFeatureType.SCENE_TO_SUMMARY.name())
                                .templateId(getSystemTemplateId(AIFeatureType.SCENE_TO_SUMMARY))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建场景生成系统预设失败", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 创建通用系统预设
     */
    private Mono<AIPromptPreset> createGenericSystemPreset(AIFeatureType featureType) {
        String presetId = "system-" + featureType.name().toLowerCase().replace("_", "-") + "-default";
        
        return presetRepository.existsByPresetIdAndIsSystemTrue(presetId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("系统预设已存在，跳过创建: {}", presetId);
                        return Mono.empty();
                    }
                    
                    try {
                        UniversalAIRequestDto requestData = UniversalAIRequestDto.builder()
                                .requestType("general")
                                .modelConfigId("default-gpt-3.5")
                                .parameters(Map.of(
                                        "temperature", 0.7,
                                        "max_tokens", 2000
                                ))
                                .build();
                        
                        // 🚀 修复：计算系统预设哈希
                        String presetHash = calculateSystemPresetHash(presetId, featureType, requestData);
                        
                        AIPromptPreset preset = AIPromptPreset.builder()
                                .presetId(presetId)
                                .userId("system")
                                .presetHash(presetHash) // 🚀 修复：设置计算出的哈希值
                                .presetName("默认 " + getFeatureDisplayName(featureType))
                                .presetDescription("系统默认的" + getFeatureDisplayName(featureType) + "预设")
                                .presetTags(Arrays.asList("系统预设", getFeatureDisplayName(featureType)))
                                .isFavorite(false)
                                .isPublic(true)
                                .useCount(0)
                                .requestData(objectMapper.writeValueAsString(requestData))
                                .systemPrompt("你是一位专业的AI助手，可以帮助用户完成各种文本处理任务。")
                                .userPrompt("{prompt}")
                                .aiFeatureType(featureType.name())
                                .templateId(getSystemTemplateId(featureType))
                                .promptCustomized(false)
                                .isSystem(true)
                                .showInQuickAccess(false) // 通用预设默认不显示在快捷访问中
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        log.info("创建系统预设: {}", preset.getPresetName());
                        return presetRepository.save(preset);
                        
                    } catch (Exception e) {
                        log.error("创建通用系统预设失败: featureType={}", featureType, e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * 获取功能类型的显示名称
     */
    private String getFeatureDisplayName(AIFeatureType featureType) {
        return FEATURE_DISPLAY_NAME_MAP.getOrDefault(featureType, featureType.name());
    }

    // 使用 EnumMap 避免 enum switch 产生的合成内部类（如 AIPromptPresetInitializer$1）
    private static final Map<AIFeatureType, String> FEATURE_DISPLAY_NAME_MAP = createFeatureDisplayNameMap();

    private static Map<AIFeatureType, String> createFeatureDisplayNameMap() {
        Map<AIFeatureType, String> map = new EnumMap<>(AIFeatureType.class);
        map.put(AIFeatureType.TEXT_EXPANSION, "文本扩写");
        map.put(AIFeatureType.TEXT_REFACTOR, "文本重构");
        map.put(AIFeatureType.TEXT_SUMMARY, "文本总结");
        map.put(AIFeatureType.AI_CHAT, "AI聊天");
        map.put(AIFeatureType.SCENE_TO_SUMMARY, "场景摘要");
        map.put(AIFeatureType.SUMMARY_TO_SCENE, "摘要生成场景");
        map.put(AIFeatureType.NOVEL_GENERATION, "小说生成");
        map.put(AIFeatureType.PROFESSIONAL_FICTION_CONTINUATION, "专业小说续写");
        return Collections.unmodifiableMap(map);
    }

    /**
     * 获取指定功能类型的系统模板ID
     */
    private String getSystemTemplateId(AIFeatureType featureType) {
        String templateId = promptProviderInitializer.getSystemTemplateId(featureType);
        if (templateId == null) {
            log.warn("⚠️ 未找到功能类型 {} 的系统模板ID，预设将不关联模板", featureType);
        } else {
            log.debug("✅ 获取到功能类型 {} 的系统模板ID: {}", featureType, templateId);
        }
        return templateId;
    }
    
    /**
     * 🚀 新增：为系统预设计算配置哈希值
     * 基于预设的关键配置生成唯一哈希，确保不会产生重复键错误
     */
    private String calculateSystemPresetHash(String presetId, AIFeatureType featureType, UniversalAIRequestDto requestData) {
        try {
            StringBuilder hashInput = new StringBuilder();
            
            // 系统预设的唯一标识
            hashInput.append("system_preset:").append(presetId).append("|");
            hashInput.append("feature_type:").append(featureType.name()).append("|");
            hashInput.append("request_type:").append(requestData.getRequestType()).append("|");
            hashInput.append("model_config:").append(requestData.getModelConfigId()).append("|");
            
            // 参数信息
            if (requestData.getParameters() != null) {
                requestData.getParameters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> hashInput.append(entry.getKey()).append(":").append(entry.getValue()).append("|"));
            }
            
            // 添加系统标识确保与用户预设区分
            hashInput.append("is_system:true");
            
            // 计算SHA-256哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(hashInput.toString().getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算系统预设哈希时发生错误", e);
            // 如果哈希计算失败，生成一个基于时间和预设ID的后备哈希
            return "system_fallback_" + presetId + "_" + System.currentTimeMillis();
        }
    }
}