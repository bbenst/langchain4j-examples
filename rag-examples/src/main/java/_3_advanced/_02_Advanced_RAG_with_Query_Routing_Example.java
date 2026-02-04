package _3_advanced;

import _2_naive.Naive_RAG_Example;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import shared.Assistant;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static shared.Utils.*;

public class _02_Advanced_RAG_with_Query_Routing_Example {

    /**
     * Please refer to {@link Naive_RAG_Example} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example}。
     * <p>
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     * LangChain4j 中的高级 RAG 说明见： https://github.com/langchain4j/langchain4j/pull/538
     * <p>
     * This example showcases the implementation of a more advanced RAG application
     * 本示例展示更高级的 RAG 应用实现，
     * using a technique known as "query routing".
     * 使用名为“查询路由”的技术。
     * <p>
     * Often, private data is spread across multiple sources and formats.
     * 私有数据通常分散在多个来源和格式中。
     * This might include internal company documentation on Confluence, your project's code in a Git repository,
     * 这可能包括 Confluence 上的公司内部文档、Git 仓库中的项目代码，
     * a relational database with user data, or a search engine with the products you sell, among others.
     * 含有用户数据的关系型数据库，或你销售产品的搜索引擎等。
     * In a RAG flow that utilizes data from multiple sources, you will likely have multiple
     * 在使用多来源数据的 RAG 流程中，你很可能会有多个
     * {@link EmbeddingStore}s or {@link ContentRetriever}s.
     * {@link EmbeddingStore} 或 {@link ContentRetriever}。
     * While you could route each user query to all available {@link ContentRetriever}s,
     * 虽然你可以把每个用户查询路由到所有可用的 {@link ContentRetriever}，
     * this approach might be inefficient and counterproductive.
     * 但这种方式可能低效且适得其反。
     * <p>
     * "Query routing" is the solution to this challenge. It involves directing a query to the most appropriate
     * “查询路由”是解决这一挑战的方法，它会将查询指向最合适的
     * {@link ContentRetriever} (or several). Routing can be implemented in various ways:
     * {@link ContentRetriever}（或多个）。路由可用多种方式实现：
     * - Using rules (e.g., depending on the user's privileges, location, etc.).
     * - 使用规则（例如根据用户权限、位置等）。
     * - Using keywords (e.g., if a query contains words X1, X2, X3, route it to {@link ContentRetriever} X, etc.).
     * - 使用关键词（例如查询包含 X1、X2、X3，则路由到 {@link ContentRetriever} X）。
     * - Using semantic similarity (see EmbeddingModelTextClassifierExample in this repository).
     * - 使用语义相似度（参见本仓库中的 EmbeddingModelTextClassifierExample）。
     * - Using an LLM to make a routing decision.
     * - 使用 LLM 做路由决策。
     * <p>
     * For scenarios 1, 2, and 3, you can implement a custom {@link QueryRouter}.
     * 对于场景 1、2、3，你可以实现自定义 {@link QueryRouter}。
     * For scenario 4, this example will demonstrate how to use a {@link LanguageModelQueryRouter}.
     * 对于场景 4，本示例将演示如何使用 {@link LanguageModelQueryRouter}。
     */

    public static void main(String[] args) {

        Assistant assistant = createAssistant();

        // First, ask "What is the legacy of John Doe?"
        // 首先，询问“John Doe 的遗产是什么？”
        // Then, ask "Can I cancel my reservation?"
        // 然后，询问“我可以取消预订吗？”
        // Now, see the logs to observe how the queries are routed to different retrievers.
        // 现在查看日志，观察查询如何被路由到不同的检索器。
        startConversationWith(assistant);
    }

    private static Assistant createAssistant() {

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        // Let's create a separate embedding store specifically for biographies.
        // 为人物传记专门创建一个独立的嵌入存储。
        EmbeddingStore<TextSegment> biographyEmbeddingStore =
                embed(toPath("documents/biography-of-john-doe.txt"), embeddingModel);
        ContentRetriever biographyContentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(biographyEmbeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();

        // Additionally, let's create a separate embedding store dedicated to terms of use.
        // 另外，为使用条款创建一个独立的嵌入存储。
        EmbeddingStore<TextSegment> termsOfUseEmbeddingStore =
                embed(toPath("documents/miles-of-smiles-terms-of-use.txt"), embeddingModel);
        ContentRetriever termsOfUseContentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(termsOfUseEmbeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName(GPT_4_O_MINI)
                .build();

        // Let's create a query router.
        // 创建一个查询路由器。
        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(biographyContentRetriever, "biography of John Doe");
        retrieverToDescription.put(termsOfUseContentRetriever, "terms of use of car rental company");
        QueryRouter queryRouter = new LanguageModelQueryRouter(chatModel, retrieverToDescription);

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private static EmbeddingStore<TextSegment> embed(Path documentPath, EmbeddingModel embeddingModel) {
        DocumentParser documentParser = new TextDocumentParser();
        Document document = loadDocument(documentPath, documentParser);

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);
        return embeddingStore;
    }
}
