package pg._1_easy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import pg._2_naive.Naive_RAG_Example_Pg;
import shared.Assistant;

import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.glob;
import static shared.Utils.startConversationWith;
import static shared.Utils.toPath;

/**
 * 使用 PGVector 作为检索存储的 Easy RAG 示例（阿里云百炼版）。
 */
public class Easy_RAG_Example_Pg {

    /**
     * 百炼聊天模型，用于回答用户问题。
     */
    private static final ChatModel CHAT_MODEL = QwenChatModel.builder()
            .apiKey(DASHSCOPE_API_KEY)
            .modelName(DASHSCOPE_CHAT_MODEL)
            .build();

    /**
     * This example demonstrates how to implement an "Easy RAG" (Retrieval-Augmented Generation) application.
     * 此示例演示如何实现“Easy RAG”（检索增强生成）应用。
     * By "easy" we mean that we won't dive into all the details about parsing, splitting, embedding, etc.
     * 这里的“easy”指我们不会深入讲解解析、切分、嵌入等细节。
     * All the "magic" is hidden inside the "langchain4j-easy-rag" module.
     * 所有“魔法”都隐藏在“langchain4j-easy-rag”模块中。
     * <p>
     * If you want to learn how to do RAG without the "magic" of an "Easy RAG", see {@link Naive_RAG_Example_Pg}.
     * 如果你想学习在没有“Easy RAG”魔法的情况下做 RAG，请参见 {@link Naive_RAG_Example_Pg}。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        // First, let's load documents that we want to use for RAG
        // 首先，加载我们要用于 RAG 的文档
        List<Document> documents = loadDocuments(toPath("documents/"), glob("*.txt"));

        // Second, let's create an assistant that will have access to our documents
        // 其次，创建一个可以访问我们文档的助手
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL) // it should use DashScope LLM
                // 它应使用百炼 LLM
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10)) // it should remember 10 latest messages
                // 它应记住最近的 10 条消息
                .contentRetriever(createContentRetriever(documents)) // it should have access to our documents
                // 它应能够访问我们的文档
                .build();

        // Lastly, let's start the conversation with the assistant. We can ask questions like:
        // 最后，开始与助手对话。可以问这样的问题：
        // - Can I cancel my reservation?
        // - 我可以取消预订吗？
        // - I had an accident, should I pay extra?
        // - 我发生了事故，需要额外支付吗？
        startConversationWith(assistant);
    }

    /**
     * 创建内容检索器，负责文档入库与相似度检索。
     *
     * @param documents 待摄取的文档列表
     * @return 内容检索器
     */
    private static ContentRetriever createContentRetriever(List<Document> documents) {

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);

        // Here, we are ingesting our documents into the store.
        // 这里我们将文档摄取到存储中。
        // Under the hood, a lot of "magic" is happening, but we can ignore it for now.
        // 在底层发生了很多“魔法”，但现在可以先忽略它们。
        EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build()
                .ingest(documents);

        // Lastly, let's create a content retriever from an embedding store.
        // 最后，从嵌入存储创建一个内容检索器。
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
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
