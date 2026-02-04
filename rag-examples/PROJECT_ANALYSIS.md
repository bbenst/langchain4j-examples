# RAG Examples 项目分析

## 概览
`rag-examples` 是一个围绕 LangChain4j 的 RAG（Retrieval-Augmented Generation）教学项目。它从“易用版 RAG”和“朴素 RAG”入手，逐步扩展到查询压缩、路由、多检索器、重排序、元数据注入/过滤、跳过检索、返回来源、SQL 检索器与 Web 搜索等高级主题。示例可独立运行，主要以对话式交互展示 RAG 行为。

## 技术栈与依赖
- Java 17 + Maven
- 核心依赖：
  - `langchain4j` 与 `langchain4j-open-ai`（核心与模型接入）
  - `langchain4j-easy-rag`（封装式易用 RAG）
  - `langchain4j-cohere`（重排序评分模型）
  - `langchain4j-web-search-engine-tavily`（Web 搜索检索器）
  - `langchain4j-experimental-sql`（SQL 检索器）
  - `langchain4j-embeddings-bge-small-en-v15-q`（本地向量模型）
  - `langchain4j-embedding-store-filter-parser-sql`（元数据过滤）
  - `h2`（内存数据库示例）
  - `logback-classic`（日志）

## 目录结构与职责
- `src/main/java/_1_easy`：一站式 Easy RAG 示例。
- `src/main/java/_2_naive`：朴素 RAG 分步搭建（加载、切分、向量化、检索、拼接提示）。
- `src/main/java/_3_advanced`：高级 RAG 能力集合：
  - 查询压缩（`_01_...Query_Compression`）
  - 查询路由（`_02_...Query_Routing`）
  - 重排序（`_03_...ReRanking`）
  - 元数据注入与过滤（`_04_...Metadata` / `_05_...Metadata_Filtering`）
  - 跳过检索（`_06_...Skip_Retrieval`）
  - 多检索器（`_07_...Multiple_Retrievers`）
  - Web 搜索（`_08_...Web_Search`）
  - 返回来源（`_09_...Return_Sources`）
  - SQL 检索器（`_10_...SQL_Database_Retreiver`）
- `src/main/java/shared`：通用助手接口与工具方法（对话循环、路径工具、API Key 读取）。
- `src/main/resources/documents`：示例文本资料（租车条款、人物传记）。
- `src/main/resources/sql`：SQL 示例建表与数据预填充脚本。

## 关键机制与约定
- **API Key 读取**：`shared/Utils.java` 读取 `OPENAI_API_KEY`，默认值为 `demo`；其他服务使用环境变量（如 `COHERE_API_KEY`、`TAVILY_API_KEY`）。
- **对话驱动**：多数示例用 `startConversationWith(assistant)` 进行交互式提问。
- **Embedding 模型**：多采用 `BgeSmallEnV15QuantizedEmbeddingModel` 本地量化模型。
- **检索增强入口**：高级 RAG 以 `RetrievalAugmentor` 为核心扩展点，便于替换路由、压缩、重排与注入策略。

## 典型示例逻辑（摘要）
- **Easy RAG**：隐藏拆分、向量化与检索细节，快速可用。
- **Naive RAG**：显式完成加载、切分、向量化、向量存储与检索流程。
- **查询压缩**：将上下文对话压缩成可检索的完整查询。
- **查询路由**：针对不同数据源使用不同检索器。
- **重排序**：先粗检索，再用更强模型筛选高相关片段。
- **元数据注入**：把文档来源与索引作为提示注入。
- **元数据过滤**：固定/动态/LLM 生成过滤条件。
- **跳过检索**：判断“无需检索”的问题直接聊天。
- **多检索器**：一次查询命中多个检索源。
- **返回来源**：回答时带出命中文档片段。
- **SQL 检索器**：将自然语言转为 SQL 查询（仅演示，强调风险）。

## 运行与配置要点
- 运行前建议设置：`OPENAI_API_KEY`，如使用重排序或 Web 搜索则需 `COHERE_API_KEY`、`TAVILY_API_KEY`。
- 示例为 `main` 方法入口，可逐个运行。
- SQL 示例使用 H2 内存库，初始化脚本位于 `resources/sql`。

## 适用场景与价值
- 学习 RAG 端到端构建与常见增强策略的最小参考。
- 作为功能目录，便于选择合适的 RAG 组件组合。
- 展示 LangChain4j 的可扩展入口与“低阶到高阶”的实践路径。
