package _1_easy;

import _2_naive.Naive_RAG_Example;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import shared.Assistant;

import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static shared.Utils.*;

public class Easy_RAG_Example {

    private static final ChatModel CHAT_MODEL = OpenAiChatModel.builder()
            .apiKey(OPENAI_API_KEY)
            .modelName(GPT_4_O_MINI)
            .build();

    /**
     * This example demonstrates how to implement an "Easy RAG" (Retrieval-Augmented Generation) application.
     * 此示例演示如何实现“Easy RAG”（检索增强生成）应用。
     * By "easy" we mean that we won't dive into all the details about parsing, splitting, embedding, etc.
     * 这里的“easy”指我们不会深入讲解解析、切分、嵌入等细节。
     * All the "magic" is hidden inside the "langchain4j-easy-rag" module.
     * 所有“魔法”都隐藏在“langchain4j-easy-rag”模块中。
     * <p>
     * If you want to learn how to do RAG without the "magic" of an "Easy RAG", see {@link Naive_RAG_Example}.
     * 如果你想学习在没有“Easy RAG”魔法的情况下做 RAG，请参见 {@link Naive_RAG_Example}。
     */

    public static void main(String[] args) {

        // First, let's load documents that we want to use for RAG
        // 首先，加载我们要用于 RAG 的文档
        List<Document> documents = loadDocuments(toPath("documents/"), glob("*.txt"));

        // Second, let's create an assistant that will have access to our documents
        // 其次，创建一个可以访问我们文档的助手
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL) // it should use OpenAI LLM
                // 它应使用 OpenAI LLM
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

    private static ContentRetriever createContentRetriever(List<Document> documents) {

        // Here, we create an empty in-memory store for our documents and their embeddings.
        // 这里我们为文档及其嵌入创建一个空的内存存储。
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // Here, we are ingesting our documents into the store.
        // 这里我们将文档摄取到存储中。
        // Under the hood, a lot of "magic" is happening, but we can ignore it for now.
        // 在底层发生了很多“魔法”，但现在可以先忽略它们。
        EmbeddingStoreIngestor.ingest(documents, embeddingStore);

        // Lastly, let's create a content retriever from an embedding store.
        // 最后，从嵌入存储创建一个内容检索器。
        return EmbeddingStoreContentRetriever.from(embeddingStore);
    }
}
