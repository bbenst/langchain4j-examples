# Agentic Tutorial 项目分析

## 概览
这是一个基于 LangChain4j 的“agentic”工作流教程项目，通过一组按章节编号的示例，逐步演示从单一 agent 到顺序/循环/并行/条件/组合/监督/非 AI agent/人类介入等多种编排模式。示例围绕“候选人招聘流程”场景构建（简历生成、评审、面试组织、邮件沟通等），并配套文本资料用于演示。

## 技术栈与依赖
- Java 17（`maven.compiler.source/target`）
- Maven 工程
- 主要依赖：
  - `langchain4j` 与 `langchain4j-open-ai`（核心与 OpenAI 模型接入）
  - `langchain4j-agentic`（agentic 编排能力）
  - `langchain4j-embeddings-bge-small-en-v15-q`（向量检索示例）
  - `logback-classic`（日志）

## 目录结构与职责
- `src/main/java/_1_basic_agent`：基础 agent 示例（字符串输出与结构化输出）。
- `src/main/java/_2_sequential_workflow`：顺序工作流（typed/untyped）。
- `src/main/java/_3_loop_workflow`：带退出条件的循环与带状态跟踪的高级循环。
- `src/main/java/_4_parallel_workflow`：并行 agent 执行与结果聚合。
- `src/main/java/_5_conditional_workflow`：基于评分的条件分支与异步分支；展示工具调用与 RAG。
- `src/main/java/_6_composed_workflow`：嵌套工作流组合。
- `src/main/java/_7_supervisor_orchestration`：监督式编排（基础与高级）。
- `src/main/java/_8_non_ai_agents`：非 AI 的确定性 agent（纯逻辑/聚合）。
- `src/main/java/_9_human_in_the_loop`：人类介入（验证与对话式流程）。
- `src/main/java/util`：通用工具（模型提供、资源加载、AgenticScope 打印）。
- `src/main/java/util/log`：日志美化与解析（自定义 logback 行为）。
- `src/main/resources/documents`：演示用文本资料（简历、JD、HR 规则等）。
- `src/main/resources/log`：多种 logback 配置。

## 关键机制与约定
- **模型提供**：`util/ChatModelProvider.java` 封装模型实例化，默认读取 `OPENAI_API_KEY`，支持通过 provider 切换到 Cerebras（读取 `CEREBRAS_API_KEY`）。
- **日志控制**：多数示例在静态块中设置 `CustomLogging` 的级别（如 PRETTY），便于观察模型调用。
- **数据加载**：`util/StringLoader` 从 `resources/documents` 读取示例输入。
- **AgenticScope**：示例中通过 `AgenticServices` 构建 agent，将输入/输出/中间状态存储于 scope，实现可追踪的编排链路。

## 典型示例流程（摘要）
- **基础 agent**：将候选人经历文本转为简历（`_1a`），结构化输出版本（`_1b`）。
- **顺序工作流**：分步骤生成/优化简历，演示 typed 与 untyped 链式编排（`_2a`/`_2b`）。
- **循环工作流**：以评分或状态驱动的迭代优化与退出条件（`_3a`/`_3b`）。
- **并行工作流**：多角色并行评审简历后聚合结果（`_4`）。
- **条件分支**：按评分走“面试组织”或“拒信邮件”路径，并展示工具调用与 RAG（`_5a`/`_5b`）。
- **组合工作流**：将多个子流程封装成更高层工作流（`_6`）。
- **监督式编排**：由监督 agent 决定调用哪些专家 agent（`_7a`/`_7b`）。
- **非 AI agent**：以确定性逻辑/聚合器构成工作流部件（`_8`）。
- **人类介入**：在关键节点由人工验证或对话式决策（`_9a`/`_9b`）。

## 运行与配置要点
- 运行前需要设置 `OPENAI_API_KEY`（或使用 Cerebras 时设置 `CEREBRAS_API_KEY`）。
- 示例以 `main` 方法为入口，不同章节可独立运行。
- 日志输出可通过 `util/log` 相关配置调节可读性和详细程度。

## 适用场景与价值
- 教学型示例，覆盖 Agentic 工作流常见结构。
- 适合作为 LangChain4j agentic 设计模式的参考实现。
- 对调试与可观测性提供了日志与 scope 的示范用法。
