# 📅 问题背景与起因 (Context & Trigger)
本次任务将 `rag-examples` 模块中 `pg` 包的所有示例从 OpenAI 切换为阿里云百炼（DashScope）聊天模型，并要求保证编译通过。环境为 Java 17、Maven 构建，依赖 `langchain4j-community-dashscope:1.10.0-beta18`。
在完成代码替换后执行 `mvn -pl rag-examples -am -DskipTests package` 触发编译失败。

# 🔍 深度分析 (Root Cause Analysis)
**排查思路**：先复现编译错误，确认错误集中在 `QwenChatModel` 包不存在和 builder 方法缺失两类问题；随后检查本地 Maven 依赖 jar 的实际类路径和 builder 支持的方法。
**根本原因**：
1. `QwenChatModel` 的实际包路径为 `dev.langchain4j.community.model.dashscope`，而代码中使用了 `dev.langchain4j.model.dashscope`，导致编译期找不到包。
2. `QwenChatModel` builder 不支持 `.timeout(...)` 与 `.logRequests(...)`，这些方法属于 OpenAI 实现或其他模型的 builder，直接迁移导致方法不存在。
**关键思考**：从“API 习惯一致”假设转为“以实际依赖 jar 为准”进行验证，避免凭经验推断 SDK 结构与能力。

# 💡 关键点与决策 (Key Points & Decisions)
- **技术难点**：DashScope SDK 与 OpenAI SDK 的 builder 能力不完全对齐，且包名不在 `model` 命名空间下。
- **关键决策**：
  - 选择检查依赖 jar（`javap`/`jar tf`）确认真实类路径与可用方法，避免盲目改动。
  - 移除不支持的 builder 方法，而不是硬找替代实现，以保证编译优先通过。

# 🛠️ 处理方式 (Solution Implementation)
- 将所有 `pg` 包示例的 `QwenChatModel` import 改为实际包路径 `dev.langchain4j.community.model.dashscope.QwenChatModel`。
- 删除 `QwenChatModel.builder()` 中不被支持的 `.timeout(...)` 与 `.logRequests(...)` 调用，保持默认行为。
- 其它逻辑保持不变，确保示例仅更换模型提供方。

# ✅ 验证步骤 (Verification Steps)
- 编译验证：
  - `mvn -pl rag-examples -am -DskipTests package`
  - 期望结果：`rag-examples` 模块编译通过（BUILD SUCCESS）。

# 🔙 回滚与应急 (Rollback Plan)
- 若需要回滚：使用 `git checkout -- <file>` 恢复受影响文件，或回退到变更前提交。
- 本次无数据库结构或数据变更，无需数据库回滚。

# 📂 关联改动文件 (File Changes)
- `rag-examples/pom.xml`
- `rag-examples/src/main/java/shared/Utils.java`
- `rag-examples/src/main/java/pg/_1_easy/Easy_RAG_Example_Pg.java`
- `rag-examples/src/main/java/pg/_2_naive/Naive_RAG_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_01_Advanced_RAG_with_Query_Compression_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_02_Advanced_RAG_with_Query_Routing_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_03_Advanced_RAG_with_ReRanking_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_04_Advanced_RAG_with_Metadata_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_05_Advanced_RAG_with_Metadata_Filtering_Examples_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_06_Advanced_RAG_Skip_Retrieval_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_07_Advanced_RAG_Multiple_Retrievers_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_08_Advanced_RAG_Web_Search_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_09_Advanced_RAG_Return_Sources_Example_Pg.java`
- `rag-examples/src/main/java/pg/_3_advanced/_10_Advanced_RAG_SQL_Database_Retreiver_Example_Pg.java`
- `rag-examples/src/main/java/pg/_4_low_level/_01_Low_Level_Naive_RAG_Example_Pg.java`

# ⚠️ 风险点与副作用 (Risks & Side Effects)
- 移除 `timeout` 与 `logRequests` 可能导致默认超时策略变化或日志可见性下降。
- DashScope 模型对请求参数支持不完全与 OpenAI 等价，运行期行为可能有所差异。

# 🚀 后续优化建议 (Future Optimizations)
- 增加统一的模型构建工厂/配置封装，减少示例级别的参数分散与误用。
- 为 DashScope 适配添加编译期或运行期的集成测试，提前发现 API 兼容性问题。
- 在文档中明确不同模型实现的 builder 支持范围，降低迁移风险。
