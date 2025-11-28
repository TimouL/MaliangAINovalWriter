 # Chroma 向量库与 RAG 功能说明
 
 本文档详细说明了项目中 Chroma 向量库的用途、配置方法以及部署方案。
 
 ## 1. 功能用途
 
 项目使用 **Chroma 向量数据库** 作为 RAG (Retrieval-Augmented Generation，检索增强生成) 的存储后端，主要用于增强 AI 的上下文理解和生成能力。
 
 ### 核心功能
 *   **知识库拆书 (Knowledge Extraction)**: 
     *   将小说文本拆解为片段 (KnowledgeChunk) 并向量化存储。
     *   帮助 AI 深入理解作品的文风、情节走向和人物性格。
 *   **智能上下文检索 (Context Retrieval)**:
     *   **场景生成摘要**: 检索相关背景信息，辅助 AI 生成精准摘要。
     *   **摘要生成场景**: 检索相关章节和设定，确保生成的正文符合上下文逻辑。
 *   **语义搜索**: 
     *   支持对小说内容进行深度的语义相似度搜索，而非仅限于关键词匹配。
 
 ## 2. 技术实现状态
 
 *   **后端逻辑**: 
     *   代码 (`ChromaVectorStore`, `RagServiceImpl`) 已完整实现向量存储、检索、自动维度调整及错误重试机制。
     *   **嵌入模型 (Embedding)**: 项目内置了本地运行的轻量级模型 **AllMiniLmL6V2** (基于 LangChain4j)，**无需**配置外部 API，无额外费用。
     *   **重排模型 (Rerank)**: 当前版本仅使用向量相似度检索，**未集成**重排步骤。
 *   **默认配置**: 
     *   功能默认处于 **关闭状态** (`enabled: false`)。
 *   **认证鉴权**:
     *   **已支持 Token 认证**：通过 `CHROMA_AUTH_TOKEN` 环境变量或配置 `vectorstore.chroma.auth-token` 来启用认证。
     *   认证头使用 `X-Chroma-Token` 格式，与 Chroma 服务端的 `CHROMA_SERVER_AUTH_CREDENTIALS` 配置对应。
 
 ## 3. 启用配置
 
 ### 3.1 配置 Chroma 服务
 
 **方式一：Docker 部署（推荐）**
 ```bash
 # 无认证启动
 docker run -d --name chroma -p 8000:8000 chromadb/chroma
 
 # 带 Token 认证启动
 docker run -d --name chroma -p 8000:8000 \
   -e CHROMA_SERVER_AUTH_CREDENTIALS_PROVIDER=chromadb.auth.token.TokenConfigServerAuthCredentialsProvider \
   -e CHROMA_SERVER_AUTH_CREDENTIALS=your-secret-token \
   chromadb/chroma
 ```
 
 ### 3.2 修改应用配置
 
 在 `application-prod.yml`（或对应环境配置）中设置：
 
 ```yaml
 vectorstore:
   chroma:
     enabled: ${CHROMA_ENABLED:true}               # [必须] 设为 true 启用功能
     url: ${CHROMA_URL:http://localhost:8000}      # [必须] 指向实际部署地址
     collection: ${CHROMA_COLLECTION:ainovel}      # [可选] 集合名称
     use-random-collection: false                  # [可选] 生产环境建议 false
     reuse-collection: true                        # [可选] 是否重用已存在的集合
     max-retries: 3                                # [可选] 失败重试次数
     retry-delay-ms: 1000                          # [可选] 重试间隔(ms)
     auth-token: ${CHROMA_AUTH_TOKEN:}             # [可选] 认证Token
 
 # RAG 检索配置（可选调优）
 rag:
   document-splitter:
     chunk-size: 1000                              # 文档切分块大小
     chunk-overlap: 200                            # 块重叠大小
   retriever:
     max-results: 5                                # 检索返回最大数量
     min-score: 0.6                                # 最小相似度阈值
 ```
 
 ### 3.3 设置环境变量
 
 ```bash
 # 必须 - Chroma 服务地址
 export CHROMA_URL="http://your-chroma-host:8000"
 
 # 可选 - 启用/禁用开关（生产环境默认 true）
 export CHROMA_ENABLED=true
 
 # 可选 - 如果 Chroma 启用了认证
 export CHROMA_AUTH_TOKEN="your-secret-token"
 ```
 
 ### 3.4 各环境默认状态
 
 | 环境 | 配置文件 | 默认状态 | 说明 |
 |------|----------|----------|------|
 | 开发 | application.yml | **关闭** | 需手动改为 `enabled: true` |
 | 生产 | application-prod.yml | **开启** | 通过 `CHROMA_ENABLED` 环境变量控制 |
 
 ### 3.5 认证配置说明
 
 如果 Chroma 服务端启用了 Token 认证，需在客户端配置对应的 Token：
 
 **方式一：环境变量（推荐）**
 ```bash
 export CHROMA_AUTH_TOKEN="your-secret-token"
 ```
 
 **方式二：配置文件**
 ```yaml
 vectorstore:
   chroma:
     auth-token: "your-secret-token"
 ```
 
 > **注意**: 官方 Docker 镜像默认端口为 `8000`，而项目配置文件默认值可能是 `18000`，请务必核对。
 
 ### 3.6 验证启用状态
 
 启动应用后，查看日志确认配置生效：
 ```
 配置Chroma向量存储，URL: http://..., 集合: ainovel, 认证: 已启用/未启用
 配置LangChain4j Chroma嵌入存储...
 ```
 
 ## 4. 拆书功能 API
 
 启用 Chroma 后，可通过以下 API 使用拆书和 RAG 功能：
 
 | 功能 | API 端点 | 说明 |
 |------|----------|------|
 | 创建知识提取任务 | `POST /api/v1/knowledge-extraction-tasks` | 启动拆书任务 |
 | 查询任务状态 | `GET /api/v1/knowledge-extraction-tasks/{taskId}` | 获取任务进度 |
 | RAG 语义检索 | `POST /api/v1/rag/search` | 基于向量的语义搜索 |
 | 知识库管理 | `/api/v1/knowledge-base/*` | 知识库增删改查 |
 
 ## 5. 部署方案推荐
 
 由于 Chroma 官方暂无成熟的免费托管服务，推荐以下部署方案：
 
 ### 方案 A：本地/服务器 Docker 部署 (最推荐)
 利用项目现有的 `docker-compose` 或单独部署，数据完全可控且无额外费用。
 
 ```bash
 # 简易启动命令
 docker run -d --name chroma -p 8000:8000 chromadb/chroma
 ```
 
 ### 方案 B：Hugging Face Spaces (免费持久化方案)
 *   **方法**: 创建 Docker Space 部署 Chroma。
 *   **持久化**: 利用 HF Dataset 作为后端存储，通过脚本在启动/关闭时同步数据（需自行编写同步逻辑）。
 *   **优点**: 免费计算资源充裕。
 
 ### 方案 C：Fly.io (含免费卷)
 *   **方法**: 部署 Docker 应用并挂载 Persistent Volume。
 *   **优点**: 提供 3GB 免费持久化存储卷。
 *   **风险**: 免费层内存仅 256MB，可能面临 OOM 风险。
 
 ## 6. 总结
 
 1.  **无需外部模型 API**: 嵌入计算本地完成（使用内置 AllMiniLmL6V2 模型）。
 2.  **无需重排服务**: 当前仅使用向量检索。
 3.  **配置即用**: 部署 Chroma -> 设置 `enabled: true` -> 配置 URL 即可生效。
 4.  **生产环境强烈建议启用 Token 认证**，保护向量数据安全。
