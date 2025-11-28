package com.ainovel.server.service.prompt.providers;

import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.service.prompt.BasePromptProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 知识提取-章节大纲生成提示词提供器
 */
@Slf4j
@Component
public class KnowledgeExtractionOutlinePromptProvider extends BasePromptProvider {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个专业的小说分析专家，擅长从小说文本中提取章节大纲和故事结构。
            
            你的任务是：
            1. 仔细阅读提供的小说章节内容
            2. 提取章节的核心情节和关键事件
            3. 总结章节的主要内容和发展脉络
            4. 返回结构化的JSON格式结果
            
            **输出格式要求：**
            请以JSON格式返回结果，包含：
            {
              "title": "章节标题",
              "summary": "章节摘要",
              "keyEvents": ["关键事件1", "关键事件2"],
              "characters": ["出场人物1", "出场人物2"],
              "plotPoints": ["情节点1", "情节点2"]
            }
            
            注意事项：
            - 提取的内容要基于实际文本，不要臆造
            - 摘要要简洁明了，突出重点
            - 关键事件按时间顺序排列
            - 直接输出JSON，不要添加markdown代码块标记
            """;

    private static final String DEFAULT_USER_PROMPT = """
            请分析以下小说章节，提取章节大纲：
            
            【章节内容】
            {{content}}
            
            请返回JSON格式的章节大纲结果。
            """;

    public KnowledgeExtractionOutlinePromptProvider() {
        super(AIFeatureType.KNOWLEDGE_EXTRACTION_OUTLINE);
    }

    @Override
    protected Set<String> initializeSupportedPlaceholders() {
        return Set.of(
            "content",
            "chapterTitle",
            "chapterIndex"
        );
    }

    @Override
    protected Map<String, String> initializePlaceholderDescriptions() {
        return Map.of(
            "content", "要分析的章节内容",
            "chapterTitle", "章节标题（可选）",
            "chapterIndex", "章节序号（可选）"
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
