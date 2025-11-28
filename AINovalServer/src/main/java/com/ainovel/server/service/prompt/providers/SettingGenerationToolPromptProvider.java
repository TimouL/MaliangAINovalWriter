package com.ainovel.server.service.prompt.providers;

import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.service.prompt.BasePromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 设定生成工具提示词提供器
 */
@Slf4j
@Component
public class SettingGenerationToolPromptProvider extends BasePromptProvider {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个专业的小说设定生成助手，擅长根据用户需求生成各类小说设定。
            
            你的任务是：
            1. 理解用户的设定需求
            2. 根据指定的设定类型生成相应内容
            3. 确保生成的设定具有创意性和可用性
            4. 返回结构化的JSON格式结果
            
            **输出格式要求：**
            请以JSON格式返回结果，包含：
            {
              "name": "设定名称",
              "type": "设定类型",
              "description": "详细描述",
              "attributes": {}
            }
            
            注意事项：
            - 生成的设定要符合小说创作需求
            - 描述要详细具体，便于在创作中使用
            - 直接输出JSON，不要添加markdown代码块标记
            """;

    private static final String DEFAULT_USER_PROMPT = """
            请根据以下要求生成小说设定：
            
            【设定类型】
            {{settingType}}
            
            【用户需求】
            {{userRequest}}
            
            【参考上下文】
            {{context}}
            
            请返回JSON格式的设定结果。
            """;

    public SettingGenerationToolPromptProvider() {
        super(AIFeatureType.SETTING_GENERATION_TOOL);
    }

    @Override
    protected Set<String> initializeSupportedPlaceholders() {
        return Set.of(
            "settingType",
            "userRequest",
            "context"
        );
    }

    @Override
    protected Map<String, String> initializePlaceholderDescriptions() {
        return Map.of(
            "settingType", "要生成的设定类型（如：人物、地点、物品等）",
            "userRequest", "用户的具体需求描述",
            "context", "参考上下文信息（可选）"
        );
    }

    @Override
    public String getDefaultSystemPrompt() {
        return DEFAULT_SYSTEM_PROMPT;
    }

    @Override
    public String getDefaultUserPrompt() {
        return DEFAULT_USER_PROMPT;
    }
}
