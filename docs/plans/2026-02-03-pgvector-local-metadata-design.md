# PGVector 本地连接 + 元数据过滤示例设计

## 目标
新增一个示例类，展示“本地 Docker PGVector 连接 + 元数据过滤搜索”。

## 范围
- 仅新增一个示例类。
- 连接方式与 `PgVectorEmbeddingStoreLocalPgExample` 一致：读取 `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`，默认值保持不变。
- 行为与 `PgVectorEmbeddingStoreWithMetadataExample` 一致：
  - 两条带 `userId` 元数据的文本写入向量库。
  - 两次过滤检索（`userId=1` 与 `userId=2`），各输出一次结果。

## 架构与数据流
- 使用 `AllMiniLmL6V2EmbeddingModel` 生成向量，维度由模型提供。
- 使用 `PgVectorEmbeddingStore` 连接本地 PGVector，表名 `test_db`。
- 先写入两条带元数据的 `TextSegment`。
- 构建查询向量后，分别用 `metadataKey("userId").isEqualTo("1")` 和 `isEqualTo("2")` 进行过滤检索。
- 两次检索均打印 `score` 与文本。

## 错误处理
- 维持示例风格，不做额外异常处理。
- 连接失败、表不存在、模型加载失败等错误直接抛出，便于定位环境问题。

## 测试与验证
- 不新增自动化测试。
- 通过手动运行 `main` 验证：本地 Docker PG 启动、环境变量正确、表可写，输出包含两次检索结果。

## 交付物
- 新示例类（类名：`PgVectorEmbeddingStoreLocalPgMetadataExample`）。
- 本设计文档。
