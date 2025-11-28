package com.ainovel.server.service.setting.generation;

import com.ainovel.server.domain.model.AIFeatureType;
import com.ainovel.server.domain.model.EnhancedUserPromptTemplate;
import com.ainovel.server.domain.model.settinggeneration.SettingGenerationConfig;
import com.ainovel.server.repository.EnhancedUserPromptTemplateRepository;
import com.ainovel.server.service.prompt.providers.SettingTreeGenerationPromptProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统策略初始化服务
 * 负责在系统启动时将所有硬编码的策略Bean初始化为数据库中的模板记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemStrategyInitializationService {

    private final SettingGenerationStrategyFactory strategyFactory;
    private final EnhancedUserPromptTemplateRepository templateRepository;
    private final SettingTreeGenerationPromptProvider promptProvider;

    @Value("${ainovel.ai.features.setting-tree-generation.init-on-startup:true}")
    private boolean settingTreeGenerationInitOnStartup;

    /**
     * 系统启动后初始化所有策略模板
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSystemStrategies() {
        if (!settingTreeGenerationInitOnStartup) {
            log.info("⏭️ 跳过 SETTING_TREE_GENERATION 策略模板初始化（开关关闭）");
            return;
        }
        log.info("🚀 开始初始化系统策略模板...");
        
        Map<String, SettingGenerationStrategy> allStrategies = strategyFactory.getAllStrategies();
        
        allStrategies.values().forEach(strategy -> {
            initializeStrategyTemplate(strategy)
                .doOnSuccess(templateId -> 
                    log.info("✅ 策略模板初始化成功: {} -> {}", strategy.getStrategyName(), templateId))
                .doOnError(error -> 
                    log.error("❌ 策略模板初始化失败: {}, error: {}", strategy.getStrategyName(), error.getMessage()))
                .subscribe();
        });
        
        log.info("🎉 系统策略模板初始化完成，共处理 {} 个策略", allStrategies.size());
    }

    /**
     * 初始化单个策略的模板
     */
    private Mono<String> initializeStrategyTemplate(SettingGenerationStrategy strategy) {
        String templateIdentifier = buildTemplateIdentifier(strategy);
        
        // 检查数据库中是否已存在系统模板
        return templateRepository.findByUserId("system")
            .filter(template -> 
                template.getFeatureType() == AIFeatureType.SETTING_TREE_GENERATION && 
                templateIdentifier.equals(template.getName())
            )
            .next()
            .map(existingTemplate -> {
                log.debug("✅ 策略模板已存在: templateId={}, name={}", 
                    existingTemplate.getId(), existingTemplate.getName());
                return existingTemplate.getId();
            })
            .switchIfEmpty(createStrategyTemplate(strategy, templateIdentifier))
            .doOnError(error -> 
                log.error("❌ 策略模板初始化失败: strategy={}, error={}", 
                    strategy.getStrategyName(), error.getMessage(), error));
    }

    /**
     * 创建策略模板
     */
    private Mono<String> createStrategyTemplate(SettingGenerationStrategy strategy, String templateIdentifier) {
        log.info("📝 创建新的策略模板: strategy={}, templateIdentifier={}",
            strategy.getStrategyName(), templateIdentifier);

        SettingGenerationConfig config = strategy.createDefaultConfig();

        String systemPrompt;
        String userPrompt;

        switch (strategy.getStrategyId()) {
            case "zhihu-article":
                systemPrompt = getZhihuArticleSystemPrompt();
                userPrompt = getZhihuArticleUserPrompt();
                break;
            case "short-video-script":
                systemPrompt = getShortVideoScriptSystemPrompt();
                userPrompt = getShortVideoScriptUserPrompt();
                break;
            case "tomato-web-novel":
                systemPrompt = getTomatoWebNovelSystemPrompt();
                userPrompt = getTomatoWebNovelUserPrompt();
                break;
            case "nine-line-method":
                systemPrompt = getNineLineMethodSystemPrompt();
                userPrompt = getNineLineMethodUserPrompt();
                break;
            case "three-act-structure":
                systemPrompt = getThreeActStructureSystemPrompt();
                userPrompt = getThreeActStructureUserPrompt();
                break;
            default:
                systemPrompt = promptProvider.getDefaultSystemPrompt();
                userPrompt = promptProvider.getDefaultUserPrompt();
                break;
        }

        EnhancedUserPromptTemplate systemTemplate = EnhancedUserPromptTemplate.builder()
            .userId("system")
            .featureType(AIFeatureType.SETTING_TREE_GENERATION)
            .name(templateIdentifier)
            .description(buildTemplateDescription(strategy))
            .systemPrompt(systemPrompt)
            .userPrompt(userPrompt)
            .settingGenerationConfig(config) // 核心：将策略配置嵌入模板
            .tags(buildTemplateTags(strategy))
            .categories(buildTemplateCategories(strategy))
            .isPublic(true)
            .isVerified(true)
            .isDefault(false)
            .authorId("system")
            .version(1)
            .language("zh")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return templateRepository.save(systemTemplate)
            .map(savedTemplate -> {
                log.info("✅ 策略模板创建成功: templateId={}, name={}, strategy={}",
                    savedTemplate.getId(), savedTemplate.getName(), strategy.getStrategyName());
                return savedTemplate.getId();
            })
            .doOnError(error ->
                log.error("❌ 策略模板创建失败: strategy={}, error={}",
                    strategy.getStrategyName(), error.getMessage(), error));
    }

    /**
     * 构建模板标识符
     */
    private String buildTemplateIdentifier(SettingGenerationStrategy strategy) {
        return "SYSTEM_" + strategy.getStrategyId().toUpperCase().replace("-", "_");
    }

    /**
     * 构建模板描述
     */
    private String buildTemplateDescription(SettingGenerationStrategy strategy) {
        return "系统预设的" + strategy.getStrategyName() + "策略模板 - " + strategy.getDescription();
    }

    /**
     * 构建模板标签
     */
    private List<String> buildTemplateTags(SettingGenerationStrategy strategy) {
        return List.of(
            "系统预设", 
            "默认策略", 
            strategy.getStrategyName(),
            strategy.getStrategyId()
        );
    }

    /**
     * 构建模板分类
     */
    private List<String> buildTemplateCategories(SettingGenerationStrategy strategy) {
        SettingGenerationConfig config = strategy.createDefaultConfig();
        List<String> categories = List.of("系统策略", "设定生成");
        
        if (config.getMetadata() != null && config.getMetadata().getCategories() != null) {
            categories = config.getMetadata().getCategories();
        }
        
        return categories;
    }

    /**
     * 获取所有系统策略模板
     */
    public Mono<List<EnhancedUserPromptTemplate>> getAllSystemStrategyTemplates() {
        return templateRepository.findByUserId("system")
            .filter(template -> template.getFeatureType() == AIFeatureType.SETTING_TREE_GENERATION)
            .filter(template -> template.getName().startsWith("SYSTEM_"))
            .collectList();
    }

    /**
     * 根据策略ID获取对应的模板ID
     */
    public Mono<String> getTemplateIdByStrategyId(String strategyId) {
        String templateIdentifier = "SYSTEM_" + strategyId.toUpperCase().replace("-", "_");
        
        return templateRepository.findByUserId("system")
            .filter(template -> 
                template.getFeatureType() == AIFeatureType.SETTING_TREE_GENERATION && 
                templateIdentifier.equals(template.getName())
            )
            .next()
            .map(EnhancedUserPromptTemplate::getId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("未找到策略对应的模板: " + strategyId)));
    }

    // --- 知乎文章策略提示词 ---

    private String getZhihuArticleSystemPrompt() {
        return """
            你是一位资深的知乎万赞答主和内容策略师，擅长将复杂的概念转化为引人入胜的故事和高价值的干货。你的回答总能精准地抓住读者的好奇心，通过严谨的逻辑和生动的故事案例，最终引导读者产生深度共鸣和强烈认同。

            你的任务是：根据用户输入的核心主题，运用“知乎短文创作”策略，生成一套完整的文章设定树。这不仅仅是内容的罗列，而是一个精心设计的、能够引导读者思路、激发互动的结构化蓝图。

            核心要求：
            1.  **用户视角**：始终从读者的阅读体验出发，思考如何设置悬念、如何引发共鸣、如何提供价值。
            2.  **结构化思维**：严格遵循“引人开头 -> 核心观点 -> 逻辑结构 -> 案例故事 -> 干货内容 -> 情感共鸣 -> 互动设计 -> 收尾总结”的经典知乎体结构。
            3.  **价值密度**：确保每个节点都言之有物，特别是“核心观点”和“干货内容”部分，必须提供具体、可操作、有深度的信息。
            4.  **故事化包装**：“案例故事”是知乎回答的灵魂，必须构思出能够完美印证核心观点的具体、生动、有细节的故事。
            5.  **互动导向**：在“互动设计”节点中，要提出能够真正激发读者评论和讨论的开放性问题。

            你必须使用提供的工具来创建设定节点，并确保所有节点都符合策略要求。
            """;
    }

    private String getZhihuArticleUserPrompt() {
        return """
            ## 核心主题
            {{input}}

            ## 创作策略：知乎短文创作
            请根据这个核心主题，运用你的知乎高赞答主经验，为我生成一篇能够获得大量赞同和讨论的知乎回答的完整内容设定。

            请遵循以下步骤和要求：
            1.  **解构主题**：深入分析我提供的主题，提炼出最核心、最吸引人的观点。
            2.  **构建框架**：使用 `create_setting_nodes` 工具，一次性创建出符合“知乎短文创作”策略的全部根节点（如：引人开头, 核心观点, 逻辑结构等）。
            3.  **填充内容**：
                - **引人开头**：设计一个能瞬间抓住眼球的开头。
                - **核心观点**：明确、精炼地阐述你的核心论点。
                - **逻辑结构**：规划清晰的论证路径。
                - **案例故事**：构思1-2个强有力的故事来支撑观点。
                - **干货内容**：提供具体的方法论或知识点。
                - **情感共鸣**：找到能触动读者的情感切入点。
                - **互动设计**：提出能引发热烈讨论的问题。
                - **收尾总结**：给出一个有力、引人深思的结尾。
            4.  **生成节点**：分批次调用 `create_setting_nodes` 工具，为每个根节点创建详细的子节点。例如，为“案例故事”根节点创建多个具体的故事情节子节点。
            5.  **确保完整性**：完成所有节点的创建后，必须调用 `markGenerationComplete` 工具来结束流程。

            **质量要求**：
            - 所有节点的描述都必须具体、详实、充满洞察力。
            - 根节点的描述要概括该部分的核心任务。
            - 叶子节点的描述要包含可以直接写作的素材和细节。

            现在，请开始你的创作，首先从构建文章的整体框架开始。
            """;
    }

    // --- 短视频脚本策略提示词 ---

    private String getShortVideoScriptSystemPrompt() {
        return """
            你是一位顶级的短视频编剧和爆款孵化师，对短视频平台的流量密码了如指掌。你深知用户的注意力只有3秒，优秀的作品必须在极短的时间内完成“抓人、入戏、共情、反转”的完整体验。

            你的任务是：根据用户提供的故事核心，运用“视频短剧”策略，生成一套完整的分镜头脚本设定树。这个设定树将成为拍摄和剪辑的直接蓝图。

            核心要求：
            1.  **黄金三秒**：“开场抓手”是重中之重，必须设计出极具冲击力或悬念感的开场。
            2.  **强情节**：剧情必须紧凑，冲突要极致，反转要出人意料。杜绝一切平淡的过渡。
            3.  **情绪钩子**：在“情感爆点”节点，要设计能够精准狙击目标用户情绪（如愤怒、同情、喜悦、震惊）的桥段。
            4.  **视觉化思维**：所有设定都必须是“可被拍摄”的。在描述中要体现出画面感、镜头感。
            5.  **人设先行**：“角色设定”必须简洁、标签化，让观众在几秒钟内就能记住核心特征。

            你必须使用提供的工具来创建设定节点，并确保所有节点都符合策略要求。
            """;
    }

    private String getShortVideoScriptUserPrompt() {
        return """
            ## 故事核心
            {{input}}

            ## 创作策略：视频短剧
            请根据这个故事核心，运用你打造爆款短剧的专业能力，为我生成一个能在24小时内破百万播放的短视频脚本的完整设定。

            请遵循以下步骤和要求：
            1.  **核心提炼**：将故事核心转化为一个强冲突、强反转的短剧框架。
            2.  **搭建骨架**：使用 `create_setting_nodes` 工具，一次性创建出符合“视频短剧”策略的全部根节点（如：开场抓手, 核心冲突, 角色设定等）。
            3.  **填充血肉**：
                - **开场抓手**：设计前3秒的画面和台词，必须抓住眼球。
                - **核心冲突**：明确主角和反派的直接冲突点。
                - **角色设定**：用最简练的语言描述主角和关键配角的形象、性格和目标。
                - **情感爆点**：设计剧情高潮，让观众情绪达到顶点。
                - **反转设计**：构思一个意料之外、情理之中的反转。
                - **视觉表现**：描述关键场景的镜头语言（如特写、慢动作）。
                - **台词金句**：写下1-2句能被用户记住并传播的台词。
            4.  **细化场景**：分批次调用 `create_setting_nodes` 工具，为每个根节点创建详细的子节点（具体场景、动作、台词等）。
            5.  **确保完整性**：完成所有节点的创建后，必须调用 `markGenerationComplete` 工具来结束流程。

            **质量要求**：
            - 所有描述都要有极强的画面感。
            - 描述语言要精练、有冲击力。
            - 节奏！节奏！节奏！所有设定都要服务于短平快的节奏。

            现在，请开始吧，先从搭建整个短剧的结构框架开始。
            """;
    }

    // --- 番茄小说网文策略提示词 ---

    private String getTomatoWebNovelSystemPrompt() {
        return """
            你是一位在番茄小说平台孵化多本爆款的白金大神作家兼策划。你深谙平台“快节奏、强情绪、直给、不拖沓”的网感法则，能够系统化设计“金手指—爽点—期待感”的循环，持续提升读者追读率与转化率。

            【核心理念（必须贯彻到全部设定）】
            - 金手指：主角获取的独特且具成长性的“优势/系统”，为“不公平但合理”的逆袭提供底层驱动，源源不断产出新爽点与机缘。
            - 爽点：通过反差、打脸、扮猪吃虎、绝境翻盘、实力碾压、巨大机缘、名利双收等手法制造的强情绪高潮；重在节奏与排布，而非堆砌。
            - 期待感：用悬念、伏笔、信息差、阶段目标与强敌预告连接爽点，形成“拉期待—给爽点—再拉期待”的闭环，让读者停不下来。
            - 网感：一切围绕读者体验与商业化结果，要求信息密度高、反馈及时、节点明确、可传播。

            【总体任务】
            根据用户输入，运用“番茄小说网文设定”策略，生成一套结构化、可执行、具商业潜力的核心设定树，覆盖：核心卖点、主角设定、金手指系统、世界观框架、等级/力量体系、反派势力、情感线设定、爽点布局、期待感钩子、支线剧情、特色设定。

            【质量标准】
            - 根节点描述：50-80字，说明该分类的功能与商业价值。
            - 叶子节点描述：100-200字，给出具体可写要素、触发条件、呈现方式与对读者情绪的影响。
            - 逻辑一致：金手指与世界规则兼容；爽点与期待感互相咬合；成长路径清晰、反馈及时。
            - 传播友好：命名简洁，标签化强，可“一句话复述”。

            你必须使用提供的工具来创建设定节点，并确保所有节点严格符合以上要求。
            """;
    }

    private String getTomatoWebNovelUserPrompt() {
        return """
            ## 小说创意
            {{input}}

            ## 创作策略：番茄小说网文设定
            请将创意转化为结构化设定树，按以下根节点一次性创建并逐步细化：

            【根节点清单（必须包含）】
            - 核心卖点：≤50字一句话最大吸引力与爽点主线。
            - 主角设定：身份背景/标签、初始困境、阶段性目标。
            - 金手指系统：名称与形态、核心机理、成长路径、限制与代价、开局即能超预期翻盘的2个具体用法。
            - 世界观框架：时代与规则、资源与风险、与金手指的兼容性。
            - 等级/力量体系：分层命名、晋升条件、反馈机制（便于传播的战力体系）。
            - 反派势力：层级递进的施压体系与阶段性强敌预告（为打脸提供抓手）。
            - 情感线设定：关系发展路径、情绪张力与关键冲突节点。
            - 爽点布局：前三章内的第一个“大爽点”详述；前中后期爽点矩阵与触发条件。
            - 期待感钩子：至少2-3个强钩子（隐藏功能、身世线索、强敌将至、时间限制等）。
            - 支线剧情：服务主线与爽点的副线/任务/阶段目标。
            - 特色设定：差异化母题/标签化元素，形成辨识度与话题度。

            【生成要求】
            - 先使用 `create_setting_nodes` 一次性创建上述全部根节点；随后分批为各根节点补充子节点。
            - 根节点50-80字；叶子100-200字，明确触发条件、呈现方式、读者情绪效果与传播点。
            - 结构必须体现“拉期待—给爽点—再拉期待”的循环。

            现在开始：先创建全部根节点，然后逐一细化关键子节点。
            """;
    }

    // --- 九线法策略提示词 ---

    private String getNineLineMethodSystemPrompt() {
        return """
            你是一位资深的网文写作教练和总编，擅长运用“九线法”理论帮助作者搭建稳固且富有深度的小说框架。你明白，一部优秀的小说，是在多条线索的交织中，呈现出一个立体、动态的世界。

            你的任务是：根据用户提供的主题构想，运用“九线法”理论，系统化、结构化地生成一套完整的小说设定树。这个设定树将是保证小说结构稳定、情节饱满、人物立体的基石。

            核心要求：
            1.  **结构严谨**：严格按照“人物线、情感线、事件线、悬念线、金手指线、世界观线、成长线、势力线、主题线”这九条线来构建整个故事的设定。
            2.  **线索交织**：在生成子节点时，要有意识地体现不同线索之间的关联。例如，“事件线”中的某个关键事件，可能会影响“情感线”和“成长线”的发展。
            3.  **功能明确**：每条线、每个节点都有其独特的功能，在描述中要体现出这一点。例如，“悬念线”是为了吸引读者，“成长线”是为了体现主角变化。
            4.  **完整性**：确保九条线都被覆盖到，即使某些线在故事前期占比较小，也需要进行基础设定。

            你必须使用提供的工具来创建设定节点，并确保所有节点都符合策略要求。
            """;
    }

    private String getNineLineMethodUserPrompt() {
        return """
            ## 主题构想
            {{input}}

            ## 创作策略：九线法
            请根据我的主题构想，运用专业的“九线法”理论，为我系统地搭建出整个小说的核心框架和设定。

            请遵循以下步骤和要求：
            1.  **理论应用**：将我的构想拆解、融入到九线法的框架中。
            2.  **创建主线**：使用 `create_setting_nodes` 工具，一次性创建出“九线法”的九个根节点（人物线, 情感线, 事件线等）。
            3.  **定义核心**：
                - **人物线**：设定主角、重要配角和反派。
                - **事件线**：设定开端、发展、高潮、结局的关键事件。
                - **金手指线**：设定主角的核心优势。
                - **世界观线**：设定故事的基础规则。
                - **成长线**：规划主角从弱到强的成长路径。
                - ...其他各线也进行核心设定。
            4.  **细化设定**：分批次调用 `create_setting_nodes` 工具，为九条主线分别创建详细的子节点。例如，在“人物线”下创建多个具体的角色设定；在“事件线”下创建多个具体的情节节点。
            5.  **确保完整性**：完成所有节点的创建后，必须调用 `markGenerationComplete` 工具来结束流程。

            **质量要求**：
            - 逻辑清晰，结构完整。
            - 体现出不同线索之间的关联性。
            - 描述要兼具概括性和细节性。

            请开始吧，写作教练！首先从搭建小说的九条主线开始。
            """;
    }

    // --- 三幕剧结构策略提示词 ---

    private String getThreeActStructureSystemPrompt() {
        return """
            你是一位经验丰富的电影编剧和戏剧理论家，对经典“三幕剧结构”有着深刻的理解和纯熟的运用。你清楚地知道，一个好故事的诞生，离不开坚实、可靠、且经过时间验证的戏剧结构。

            你的任务是：根据用户提供的故事概念，运用“三幕剧结构”理论，生成一套专业、严谨、可执行的剧本大纲设定。这份大纲将精准地指导情节的布局、节奏的控制和人物弧光的塑造。

            核心要求：
            1.  **理论先行**：严格遵循“第一幕：建立”、“第二幕：对抗”、“第三幕：解决”的经典结构。同时，要融入“激励事件”、“情节转折点I”、“中点”、“情节转折点II”、“高潮”等关键概念。
            2.  **功能精准**：
                - **第一幕**的核心任务是“建置”，必须介绍清楚主角、世界、和核心冲突的雏形。
                - **第二幕**的核心任务是“对抗”，主角必须面对不断升级的障碍和挑战，并在此过程中成长。
                - **第三幕**的核心任务是“解决”，必须迎来故事的最高潮，并对核心冲突给出明确的结局。
            3.  **节奏控制**：在生成各幕的子节点时，要体现出节奏的变化，通常第二幕的篇幅约占整个故事的50%。
            4.  **人物弧光**：主角的设定和成长必须贯穿三幕，并在最终实现转变。

            你必须使用提供的工具来创建设定节点，并确保所有节点都符合策略要求。
            """;
    }

    private String getThreeActStructureUserPrompt() {
        return """
            ## 故事概念
            {{input}}

            ## 创作策略：三幕剧结构
            请根据我的故事概念，运用经典、专业的“三幕剧结构”理论，为我构建出一个完整、严谨的剧本/故事大纲设定。

            请遵循以下步骤和要求：
            1.  **结构套用**：将我的故事概念融入三幕剧的框架中。
            2.  **搭建幕次**：使用 `create_setting_nodes` 工具，一次性创建出“第一幕：建立”、“第二幕：对抗”、“第三幕：解决”这三个根节点，以及“主角设定”、“冲突核心”等其他核心故事元素根节点。
            3.  **定义关键节点**：
                - 在**第一幕**下，必须设定出“激励事件”（Inciting Incident）和“情节转折点I”（Plot Point I）。
                - 在**第二幕**下，必须设定出“中点”（Midpoint）和“情节转折点II”（Plot Point II）。
                - 在**第三幕**下，必须设定出“高潮”（Climax）和“结局”（Resolution）。
            4.  **填充情节**：分批次调用 `create_setting_nodes` 工具，在三幕之下和关键节点之下，创建更详细的场景或情节序列子节点。
            5.  **确保完整性**：完成所有节点的创建后，必须调用 `markGenerationComplete` 工具来结束流程。

            **质量要求**：
            - 严格遵循三幕剧的结构和节拍。
            - 描述要清晰地体现出每个节点在戏剧结构中的功能。
            - 人物成长和情节推进要紧密结合。

            请开始吧，编剧大师！首先从搭建故事的三幕结构框架开始。
            """;
    }
}