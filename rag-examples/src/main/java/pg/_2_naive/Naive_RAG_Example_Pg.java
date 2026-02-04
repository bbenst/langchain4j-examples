package pg._2_naive;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import shared.Assistant;

import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.startConversationWith;
import static shared.Utils.toPath;

/**
 * 朴素 RAG 示例（PGVector 作为向量存储，百炼作为聊天模型）。
 */
public class Naive_RAG_Example_Pg {

    /**
     * This example demonstrates how to implement a naive Retrieval-Augmented Generation (RAG) application.
     * 此示例演示如何实现一个朴素的检索增强生成（RAG）应用。
     * By "naive", we mean that we won't use any advanced RAG techniques.
     * 这里的“naive”指我们不会使用任何高级 RAG 技术。
     * In each interaction with the Large Language Model (LLM), we will:
     * 在每次与大语言模型（LLM）的交互中，我们将：
     * 1. Take the user's query as-is.
     * 1. 直接使用用户的查询。
     * 2. Embed it using an embedding model.
     * 2. 使用嵌入模型对其进行嵌入。
     * 3. Use the query's embedding to search an embedding store (containing small segments of your documents)
     * 3. 用查询的嵌入在嵌入存储（包含文档的小片段）中搜索最相关的 X 个片段
     * for the X most relevant segments.
     * 以获得最相关的 X 个片段。
     * 4. Append the found segments to the user's query.
     * 4. 将找到的片段附加到用户查询后。
     * 5. Send the combined input (user query + segments) to the LLM.
     * 5. 将合并后的输入（用户查询 + 片段）发送给 LLM。
     * 6. Hope that:
     * 6. 并希望：
     * - The user's query is well-formulated and contains all necessary details for retrieval.
     * - 用户的查询表述清晰，包含检索所需的全部细节。
     * - The found segments are relevant to the user's query.
     * - 找到的片段与用户查询相关。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        // Let's create an assistant that will know about our document
        // 创建一个了解我们文档的助手
        Assistant assistant = createAssistant("documents/miles-of-smiles-terms-of-use.txt");

        // Now, let's start the conversation with the assistant. We can ask questions like:
        // 现在开始与助手对话。可以问这样的问题：
        // - Can I cancel my reservation?
        // - 我可以取消预订吗？
        // - I had an accident, should I pay extra?
        // - 我发生了事故，需要额外支付吗？
        startConversationWith(assistant);
    }

    /**
     * 创建具备检索能力的助手。
     *
     * @param documentPath 文档路径（类路径相对路径）
     * @return 可用于交互的助手
     */
    private static Assistant createAssistant(String documentPath) {

        // First, let's create a chat model, also known as a LLM, which will answer our queries.
        // 首先，创建一个聊天模型（也称为 LLM）来回答我们的查询。
        // In this example, we will use DashScope's Qwen model, but you can choose any supported model.
        // 本例将使用百炼的 Qwen 模型，但你可以选择任何受支持的模型。
        // Langchain4j currently supports more than 10 popular LLM providers.
        // Langchain4j 目前支持超过 10 家主流 LLM 提供方。
        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();


        // Now, let's load a document that we want to use for RAG.
        // 现在，加载我们要用于 RAG 的文档。
        // We will use the terms of use from an imaginary car rental company, "Miles of Smiles".
        // 我们将使用一家虚构租车公司“Miles of Smiles”的使用条款。
        // For this example, we'll import only a single document, but you can load as many as you need.
        // 本例只导入一个文档，但你可以按需加载多个文档。
        // LangChain4j offers built-in support for loading documents from various sources:
        // LangChain4j 提供从多种来源加载文档的内置支持：
        // File System, URL, Amazon S3, Azure Blob Storage, GitHub, Tencent COS.
        // 文件系统、URL、Amazon S3、Azure Blob Storage、GitHub、腾讯 COS。
        // Additionally, LangChain4j supports parsing multiple document types:
        // 此外，LangChain4j 支持解析多种文档类型：
        // text, pdf, doc, xls, ppt.
        // text、pdf、doc、xls、ppt。
        // However, you can also manually import your data from other sources.
        // 不过，你也可以从其他来源手动导入数据。
        DocumentParser documentParser = new TextDocumentParser();
        Document document = loadDocument(toPath(documentPath), documentParser);


        // Now, we need to split this document into smaller segments, also known as "chunks."
        // 现在，我们需要把文档拆分成更小的片段，也称为“chunks”。
        // This approach allows us to send only relevant segments to the LLM in response to a user query,
        // 这种做法让我们只把相关片段发送给 LLM 以回应用户查询，
        // rather than the entire document. For instance, if a user asks about cancellation policies,
        // 而不是整篇文档。例如，当用户询问取消政策时，
        // we will identify and send only those segments related to cancellation.
        // 我们只会找出并发送与取消相关的片段。
        // A good starting point is to use a recursive document splitter that initially attempts
        // 一个好的起点是使用递归文档切分器，它会先尝试
        // to split by paragraphs. If a paragraph is too large to fit into a single segment,
        // 按段落切分。如果某段太大无法放入一个片段，
        // the splitter will recursively divide it by newlines, then by sentences, and finally by words,
        // 切分器会递归地按换行、按句子、最后按单词切分，
        // if necessary, to ensure each piece of text fits into a single segment.
        // 如有必要，以确保每段文本都能放入一个片段中。
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);


        // Now, we need to embed (also known as "vectorize") these segments.
        // 现在，我们需要对这些片段进行嵌入（也称为“向量化”）。
        // Embedding is needed for performing similarity searches.
        // 进行相似度搜索需要嵌入。
        // For this example, we'll use a local in-process embedding model, but you can choose any supported model.
        // 本例将使用本地进程内嵌入模型，但你可以选择任何受支持的模型。
        // Langchain4j currently supports more than 10 popular embedding model providers.
        // Langchain4j 目前支持超过 10 家主流嵌入模型提供方。
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();


        // Next, we will store these embeddings in an embedding store (also known as a "vector database").
        // 接下来，我们将这些嵌入存储到嵌入存储（也称为“向量数据库”）中。
        // This store will be used to search for relevant segments during each interaction with the LLM.
        // 在每次与 LLM 交互时，该存储将用于搜索相关片段。
        // For simplicity, this example uses an in-memory embedding store, but you can choose from any supported store.
        // 为简单起见，本例使用内存嵌入存储，但你可以选择任何受支持的存储。
        // Langchain4j currently supports more than 15 popular embedding stores.
        // Langchain4j 目前支持超过 15 种主流嵌入存储。
        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);
        embeddingStore.addAll(embeddings, segments);

        // We could also use EmbeddingStoreIngestor to hide manual steps above behind a simpler API.
        // 我们也可以使用 EmbeddingStoreIngestor 将上述手动步骤封装在更简单的 API 背后。
        // See an example of using EmbeddingStoreIngestor in _01_Advanced_RAG_with_Query_Compression_Example.
        // 在 _01_Advanced_RAG_with_Query_Compression_Example 中可以看到使用 EmbeddingStoreIngestor 的示例。


        // The content retriever is responsible for retrieving relevant content based on a user query.
        // 内容检索器负责根据用户查询检索相关内容。
        // Currently, it is capable of retrieving text segments, but future enhancements will include support for
        // 目前它可以检索文本片段，但未来会支持
        // additional modalities like images, audio, and more.
        // 图像、音频等更多模态。
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2) // on each interaction we will retrieve the 2 most relevant segments
                // 每次交互将检索最相关的 2 个片段
                .minScore(0.5) // we want to retrieve segments at least somewhat similar to user query
                // 我们希望检索至少与用户查询有一定相似度的片段
                .build();


        // Optionally, we can use a chat memory, enabling back-and-forth conversation with the LLM
        // 可选地，我们可以使用聊天记忆，以支持与 LLM 的往返对话
        // and allowing it to remember previous interactions.
        // 并让它记住之前的交互。
        // Currently, LangChain4j offers two chat memory implementations:
        // 目前，LangChain4j 提供两种聊天记忆实现：
        // MessageWindowChatMemory and TokenWindowChatMemory.
        // MessageWindowChatMemory 和 TokenWindowChatMemory。
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);


        // The final step is to build our AI Service,
        // 最后一步是构建我们的 AI 服务，
        // configuring it to use the components we've created above.
        // 配置它使用我们上面创建的组件。
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemory(chatMemory)
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
