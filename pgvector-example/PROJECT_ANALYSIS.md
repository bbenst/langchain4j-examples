# 项目分析（pgvector-example）

## 概览
该项目是一个基于 Java 17 的 Maven 示例工程，展示如何使用 LangChain4j 的 `PgVectorEmbeddingStore` 将文本向量写入/检索到 pgvector（PostgreSQL 扩展）中。项目包含两个可执行示例：
- **基础向量检索**：写入两段文本，进行相似度搜索并输出最相关结果。
- **带元数据过滤的检索**：写入带 `userId` 元数据的文本，按元数据过滤后进行相似度搜索。

## 目录结构
- `pom.xml`：Maven 配置与依赖管理
- `src/main/java/PgVectorEmbeddingStoreExample.java`：基础检索示例
- `src/main/java/PgVectorEmbeddingStoreWithMetadataExample.java`：元数据过滤示例

## 关键依赖与作用（来自 pom.xml）
- `langchain4j-pgvector`：LangChain4j 对 pgvector 的存储实现
- `langchain4j-embeddings-all-minilm-l6-v2`：内置 ONNX 版 MiniLM 向量模型（本地推理）
- `testcontainers-postgresql`：启动带 pgvector 镜像的临时 PostgreSQL 容器
- `slf4j-simple`：简易日志实现

## 核心流程（两份示例共同点）
1. 使用 Testcontainers 启动 `pgvector/pgvector:pg16` 容器。
2. 初始化 `AllMiniLmL6V2EmbeddingModel`，获取向量维度。
3. 构建 `PgVectorEmbeddingStore`，连接容器中的 PostgreSQL，并设置表名与向量维度。
4. 将 `TextSegment` 转为向量后写入存储。
5. 构造查询向量并执行相似度检索。
6. 输出最高相似度的结果及分数。

## 示例一：基础检索（PgVectorEmbeddingStoreExample）
- 写入两条文本：
  - “I like football.”
  - “The weather is good today.”
- 使用查询：“What is your favourite sport?”
- 期望返回与足球相关的文本片段。

## 示例二：元数据过滤检索（PgVectorEmbeddingStoreWithMetadataExample）
- 写入两条文本并附加元数据 `userId`：
  - userId=1： “I like football.”
  - userId=2： “I like basketball.”
- 使用相同查询向量，但分别按 `userId` 过滤：
  - 过滤 userId=1 → 返回 football
  - 过滤 userId=2 → 返回 basketball

## 运行方式（基于 Maven）
可直接运行对应的 `main` 方法（IDE 内或命令行执行）。示例依赖 Docker 来启动 pgvector 镜像。若本机未安装 Docker 或无法拉取镜像，运行会失败。

## 风险与注意事项
- **Docker 依赖**：Testcontainers 需要可用的 Docker 守护进程。
- **首次运行耗时**：`pgvector/pgvector:pg16` 镜像首次拉取可能较慢。
- **资源释放**：容器在 `try-with-resources` 中关闭，正常情况下可自动回收。
- **向量维度一致性**：`PgVectorEmbeddingStore` 的 `dimension` 必须与模型输出一致。

## 可扩展方向
- 将 `table` 参数改为更有意义的表名，并增加索引策略。
- 在元数据中加入更多字段（如文档来源、时间戳）。
- 扩展检索策略（例如调大 `maxResults`、加入阈值过滤）。
- 替换为远程嵌入模型或其他本地模型。
