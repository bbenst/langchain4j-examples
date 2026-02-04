package pg._3_advanced;

import pg._2_naive.Naive_RAG_Example_Pg;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import shared.Assistant;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.startConversationWith;
import static shared.Utils.toPath;

public class _06_Advanced_RAG_Skip_Retrieval_Example_Pg {


    /**
     * Please refer to {@link Naive_RAG_Example_Pg} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example_Pg}。
     * <p>
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     * LangChain4j 中的高级 RAG 说明见： https://github.com/langchain4j/langchain4j/pull/538
     * <p>
     * This example demonstrates how to conditionally skip retrieval.
     * 本示例演示如何有条件地跳过检索。
     * Sometimes, retrieval is unnecessary, for instance, when a user simply says "Hi".
     * 有时检索并非必要，例如用户只是说“Hi”。
     * <p>
     * There are multiple ways to implement this, but the simplest one is to use a custom {@link QueryRouter}.
     * 实现方式有多种，但最简单的是使用自定义 {@link QueryRouter}。
     * When retrieval should be skipped, QueryRouter will return an empty list,
     * 当需要跳过检索时，QueryRouter 会返回空列表，
     * meaning that the query will not be routed to any {@link ContentRetriever}.
     * 即该查询不会被路由到任何 {@link ContentRetriever}。
     * <p>
     * Decision-making can be implemented in various ways:
     * 决策可通过多种方式实现：
     * - Using rules (e.g., depending on the user's privileges, location, etc.).
     * - 使用规则（例如根据用户权限、位置等）。
     * - Using keywords (e.g., if a query contains specific words).
     * - 使用关键词（例如查询包含特定词）。
     * - Using semantic similarity (see EmbeddingModelTextClassifierExample in this repository).
     * - 使用语义相似度（参见本仓库中的 EmbeddingModelTextClassifierExample）。
     * - Using an LLM to make a decision.
     * - 使用 LLM 做决策。
     * <p>
     * In this example, we will use an LLM to decide whether a user query should do retrieval or not.
     * 本示例将使用 LLM 来判断用户查询是否需要检索。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        Assistant assistant = createAssistant();

        // First, say "Hi"
        // 首先，说“Hi”
        // Notice how this query is not routed to any retrievers.
        // 注意该查询没有被路由到任何检索器。

        // Now, ask "Can I cancel my reservation?"
        // 现在，询问“我可以取消预订吗？”
        // This query has been routed to our retriever.
        // 该查询已被路由到我们的检索器。
        startConversationWith(assistant);
    }

    /**
     * 创建可跳过检索的助手。
     *
     * @return AI 助手实例
     */
    private static Assistant createAssistant() {

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        EmbeddingStore<TextSegment> embeddingStore =
                embed(toPath("documents/miles-of-smiles-terms-of-use.txt"), embeddingModel);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();

        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();

        // Let's create a query router.
        // 创建一个查询路由器。
        QueryRouter queryRouter = new QueryRouter() {

            private final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
                    "Is the following query related to the business of the car rental company? " +
                            "Answer only 'yes', 'no' or 'maybe'. " +
                            "Query: {{it}}"
            );

            @Override
            public Collection<ContentRetriever> route(Query query) {

                Prompt prompt = PROMPT_TEMPLATE.apply(query.text());

                AiMessage aiMessage = chatModel.chat(prompt.toUserMessage()).aiMessage();
                System.out.println("LLM decided: " + aiMessage.text());

                if (aiMessage.text().toLowerCase().contains("no")) {
                    return emptyList();
                }

                return singletonList(contentRetriever);
            }
        };

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * 将文档嵌入并写入向量存储。
     *
     * @param documentPath 文档路径
     * @param embeddingModel 嵌入模型
     * @return 已写入向量的存储
     */
    private static EmbeddingStore<TextSegment> embed(Path documentPath, EmbeddingModel embeddingModel) {
        DocumentParser documentParser = new TextDocumentParser();
        Document document = loadDocument(documentPath, documentParser);

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);
        embeddingStore.addAll(embeddings, segments);
        return embeddingStore;
    }

    /**
     * 创建 PGVector 嵌入存储。
     *
     * @param embeddingModel 嵌入模型，用于确定向量维度
     * @return 嵌入存储
     */
    private static EmbeddingStore<TextSegment> createEmbeddingStore(EmbeddingModel embeddingModel) {
        String host = envOrDefault("PGHOST", "localhost");
        int port = Integer.parseInt(envOrDefault("PGPORT", "5434"));
        String database = envOrDefault("PGDATABASE", "postgres");
        String user = envOrDefault("PGUSER", "postgres");
        String password = envOrDefault("PGPASSWORD", "postgres");

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table("rag_examples_pg")
                .dimension(embeddingModel.dimension())
                .build();
    }

    /**
     * 读取环境变量，若为空则返回默认值。
     *
     * @param name 环境变量名称
     * @param defaultValue 默认值
     * @return 环境变量值或默认值
     */
    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value;
    }
}
