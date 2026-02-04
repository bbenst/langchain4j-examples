package pg._3_advanced;

import pg._2_naive.Naive_RAG_Example_Pg;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
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

public class _03_Advanced_RAG_with_ReRanking_Example_Pg {

    /**
     * Please refer to {@link Naive_RAG_Example_Pg} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example_Pg}。
     * <p>
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     * LangChain4j 中的高级 RAG 说明见： https://github.com/langchain4j/langchain4j/pull/538
     * <p>
     * This example illustrates the implementation of a more advanced RAG application
     * 本示例展示更高级的 RAG 应用实现，
     * using a technique known as "re-ranking".
     * 使用名为“重排序”的技术。
     * <p>
     * Frequently, not all results retrieved by {@link ContentRetriever} are truly relevant to the user query.
     * 通常，由 {@link ContentRetriever} 检索到的结果并不都与用户查询真正相关。
     * This is because, during the initial retrieval stage, it is often preferable to use faster
     * 这是因为在初始检索阶段，通常更倾向于使用更快
     * and more cost-effective models, particularly when dealing with a large volume of data.
     * 且成本更低的模型，尤其是在处理大量数据时。
     * The trade-off is that the retrieval quality may be lower.
     * 代价是检索质量可能会下降。
     * Providing irrelevant information to the LLM can be costly and, in the worst case, lead to hallucinations.
     * 向 LLM 提供无关信息会增加成本，最坏情况下会导致幻觉。
     * Therefore, in the second stage, we can perform re-ranking of the results obtained in the first stage
     * 因此，在第二阶段，我们可以对第一阶段得到的结果进行重排序
     * and eliminate irrelevant results using a more advanced model (e.g., Cohere Rerank).
     * 并使用更高级的模型（如 Cohere Rerank）剔除无关结果。
     * <p>
     * This example requires "langchain4j-cohere" dependency.
     * 本示例需要 "langchain4j-cohere" 依赖。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        Assistant assistant = createAssistant("documents/miles-of-smiles-terms-of-use.txt");

        // First, say "Hi". Observe how all segments retrieved in the first stage were filtered out.
        // 首先，说“Hi”，观察第一阶段检索到的所有片段都被过滤掉。
        // Then, ask "Can I cancel my reservation?" and observe how all but one segment were filtered out.
        // 然后，询问“我可以取消预订吗？”，观察除一个片段外其余都被过滤掉。
        startConversationWith(assistant);
    }

    /**
     * 创建具备重排序能力的助手。
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

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5) // let's get more results
                // 让我们获取更多结果
                .build();

        // To register and get a free API key for Cohere, please visit the following link:
        // 注册并获取 Cohere 的免费 API key，请访问以下链接：
        // https://dashboard.cohere.com/welcome/register
        // Cohere 注册链接
        ScoringModel scoringModel = CohereScoringModel.builder()
                .apiKey(System.getenv("COHERE_API_KEY"))
                .modelName("rerank-multilingual-v3.0")
                .build();

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.8) // we want to present the LLM with only the truly relevant segments for the user's query
                // 我们希望只向 LLM 提供与用户查询真正相关的片段
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentAggregator(contentAggregator)
                .build();

        ChatModel model = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(model)
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
