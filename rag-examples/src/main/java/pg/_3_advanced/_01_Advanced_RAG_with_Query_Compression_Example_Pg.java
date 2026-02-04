package pg._3_advanced;

import pg._2_naive.Naive_RAG_Example_Pg;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import shared.Assistant;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.startConversationWith;
import static shared.Utils.toPath;

public class _01_Advanced_RAG_with_Query_Compression_Example_Pg {

    /**
     * Please refer to {@link Naive_RAG_Example_Pg} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example_Pg}。
     * <p>
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     * LangChain4j 中的高级 RAG 说明见： https://github.com/langchain4j/langchain4j/pull/538
     * <p>
     * This example illustrates the implementation of a more sophisticated RAG application
     * 本示例展示了更复杂的 RAG 应用实现，
     * using a technique known as "query compression".
     * 使用名为“查询压缩”的技术。
     * Often, a query from a user is a follow-up question that refers back to earlier parts of the conversation
     * 用户的查询往往是对先前对话的追问，
     * and lacks all the necessary details for effective retrieval.
     * 因而缺少有效检索所需的细节。
     * For example, consider this conversation:
     * 例如，考虑如下对话：
     * User: What is the legacy of John Doe?
     * 用户：John Doe 的遗产是什么？
     * AI: John Doe was a...
     * AI：John Doe 是……
     * User: When was he born?
     * 用户：他什么时候出生？
     * <p>
     * In such scenarios, using a basic RAG approach with a query like "When was he born?"
     * 在这种场景中，使用基础 RAG 且查询为“他什么时候出生？”
     * would likely fail to find articles about John Doe, as it doesn't contain "John Doe" in the query.
     * 可能无法找到有关 John Doe 的文章，因为查询中没有包含“John Doe”。
     * Query compression involves taking the user's query and the preceding conversation, then asking the LLM
     * 查询压缩会结合用户查询与先前对话，并让 LLM
     * to "compress" this into a single, self-contained query.
     * 将其“压缩”为一个自包含的查询。
     * The LLM should generate a query like "When was John Doe born?".
     * LLM 应生成类似“John Doe 什么时候出生？”的查询。
     * This method adds a bit of latency and cost but significantly enhances the quality of the RAG process.
     * 该方法会增加一些延迟与成本，但能显著提升 RAG 过程的质量。
     * It's worth noting that the LLM used for compression doesn't have to be the same as the one
     * 值得注意的是，用于压缩的 LLM 不必与对话所用的 LLM 相同，
     * used for conversation. For instance, you might use a smaller local model trained for summarization.
     * 例如，你可以使用一个用于摘要的更小本地模型。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        Assistant assistant = createAssistant("documents/biography-of-john-doe.txt");

        // First, ask "What is the legacy of John Doe?"
        // 首先，询问“John Doe 的遗产是什么？”
        // Then, ask "When was he born?"
        // 然后，询问“他什么时候出生？”
        // Now, review the logs:
        // 现在查看日志：
        // The first query was not compressed as there was no preceding context to compress.
        // 第一个查询没有被压缩，因为没有可压缩的上下文。
        // The second query, however, was compressed into something like "When was John Doe born?"
        // 第二个查询则被压缩成类似“John Doe 什么时候出生？”这样的查询。
        startConversationWith(assistant);
    }

    /**
     * 创建具备查询压缩能力的助手。
     *
     * @param documentPath 文档路径（类路径相对路径）
     * @return AI 助手实例
     */
    private static Assistant createAssistant(String documentPath) {

        Document document = loadDocument(toPath(documentPath), new TextDocumentParser());

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);

        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();

        // We will create a CompressingQueryTransformer, which is responsible for compressing
        // 我们将创建一个 CompressingQueryTransformer，负责将
        // the user's query and the preceding conversation into a single, stand-alone query.
        // 用户查询和先前对话压缩为一个独立查询。
        // This should significantly improve the quality of the retrieval process.
        // 这将显著提升检索过程的质量。
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();

        // The RetrievalAugmentor serves as the entry point into the RAG flow in LangChain4j.
        // RetrievalAugmentor 是 LangChain4j 中 RAG 流程的入口。
        // It can be configured to customize the RAG behavior according to your requirements.
        // 它可以根据你的需求进行配置以定制 RAG 行为。
        // In subsequent examples, we will explore more customizations.
        // 在后续示例中，我们将探索更多自定义项。
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
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
