# 中文 RAG 优化计划（PG + DashScope）

## 计划标题
中文 RAG 优化：切换为可控检索 + DashScope 中文 Embedding（不做重排）

## 简要总结
从 Easy RAG 迁移到显式“切分→入库→检索”流程，替换英文向量为 DashScope Qwen Embedding，确保文档与查询向量类型区分，并针对中文调整切分与检索参数，以提升召回与相关性。

## 公共 API/接口变更
- 新增环境变量常量（建议）
  - `shared/Utils.java` 增加 `DASHSCOPE_EMBEDDING_MODEL` 与可选 `DASHSCOPE_EMBEDDING_DIMENSION`，供 Embedding 选择与维度控制。
  - 默认 `DASHSCOPE_EMBEDDING_MODEL="text-embedding-v3"`（SDK 默认），如果有更合适的模型名，可通过环境变量覆盖。

## 实施步骤
1. 代码结构从 Easy RAG 迁移为显式流程
1.1. 使用 `TextDocumentParser` + `DocumentSplitters.recursive(...)` 显式切分中文文档。
1.2. 使用 `EmbeddingStoreIngestor` 手动入库。
1.3. 显式构建 `EmbeddingStoreContentRetriever` 并设置 `maxResults`、`minScore` 等参数。

2. 替换 Embedding 模型为 DashScope Qwen Embedding
2.1. 引入 `dev.langchain4j.community.model.dashscope.QwenEmbeddingModel`。
2.2. 构建两个 EmbeddingModel 实例：
- 文档模型：`textType=DOCUMENT`
- 查询模型：`textType=QUERY`
2.3. 文档入库使用“文档模型”，检索使用“查询模型”，确保 DashScope 的 query/document 类型区分正确生效。

3. 中文切分与检索参数基线
3.1. 切分建议：`DocumentSplitters.recursive(300, 50)` 作为起点（中文更适合短段落 + 小重叠）。
3.2. 检索参数建议：`maxResults=8`，`minScore=0.6`，根据实际召回再微调。

4. 约束与兼容
4.1. 保持 PGVector 作为向量库不变。
4.2. 仍使用 `QwenChatModel` 作为生成模型。

## 测试与验证
1. 使用 `documents/` 中至少 2-3 条中文问答样例进行手工评测：
- 是否能召回包含答案的中文片段
- 是否能避免召回无关英文片段
2. 观察检索返回片段数量与相关性，按需调整 `maxResults` 与 `minScore`。

## 显式假设与默认
1. 接受默认 Embedding 模型名为 `text-embedding-v3`，并允许通过环境变量覆盖。
2. 不启用重排序模型（Cohere/LLM rerank）。
3. 文档均为 UTF-8 编码的中文文本。
