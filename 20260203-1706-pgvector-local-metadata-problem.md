# 📅 问题背景与起因 (Context & Trigger)
原始需求是新增一个示例类，基于 `PgVectorEmbeddingStoreWithMetadataExample` 的元数据过滤逻辑，同时使用本地 Docker 的 PGVector 连接方式，参考 `PgVectorEmbeddingStoreLocalPgExample`。目标是提供一个本地 PG 环境可运行的元数据检索示例。环境为本仓库 `pgvector-example` 模块，Java 示例，使用 LangChain4j 与 pgvector。

# 🔍 深度分析 (Root Cause Analysis)
排查思路：通过读取现有两个示例类，确认差异点（连接方式 vs 元数据过滤），并根据用户确认的表名、类名、筛选逻辑进行合并。
根本原因：不是缺陷修复，而是功能补齐型新增示例。关键问题在于如何最小改动地复用现有示例结构，避免引入 Testcontainers，同时保持元数据检索路径一致。
关键思考：纠正了“随意重构或抽公共方法”的倾向，选择最小化修改的复制改造方案，确保示例可读性与目的性。

# 💡 关键点与决策 (Key Points & Decisions)
技术难点：无复杂逻辑，主要是确保导入与调用链完整、连接参数与本地示例一致。
关键决策：
- 选择直接复制 `PgVectorEmbeddingStoreWithMetadataExample` 并替换连接方式，而非从本地示例增量注入元数据或抽公共方法。
- 表名使用 `test_db`，与本地示例一致。

# 🛠️ 处理方式 (Solution Implementation)
实现了新类 `PgVectorEmbeddingStoreLocalPgMetadataExample`：
- 使用环境变量 `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD` 读取连接信息，默认值与本地示例一致。
- 继续使用 `AllMiniLmL6V2EmbeddingModel` 构建向量。
- 写入两条带 `userId` 元数据的 `TextSegment`。
- 执行两次基于元数据的过滤检索并输出结果。

# ✅ 验证步骤 (Verification Steps)
未执行手动验证（本地运行环境未确认）。
如需验证：
- 运行命令：
  - `mvn -pl pgvector-example -am -DskipTests exec:java -Dexec.mainClass=PgVectorEmbeddingStoreLocalPgMetadataExample`
- 期望输出：两次检索的 `score` 与对应文本（用户 1 与用户 2）。

# 🔙 回滚与应急 (Rollback Plan)
仅新增示例文件，无数据库结构变更。
回滚方式：删除新文件即可。

# 📂 关联改动文件 (File Changes)
- `pgvector-example/src/main/java/PgVectorEmbeddingStoreLocalPgMetadataExample.java`

# ⚠️ 风险点与副作用 (Risks & Side Effects)
- 需要本地 PGVector 环境与正确的环境变量配置，否则运行会失败。
- 使用默认表名 `test_db` 可能与现有数据冲突或污染开发库。

# 🚀 后续优化建议 (Future Optimizations)
- 可考虑在示例中增加简单的环境提示或连接失败提示，降低上手成本。
- 如果示例过多且重复，可考虑统一抽取连接构建方法，但需要权衡示例可读性。
