package pg._4_low_level;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static java.util.stream.Collectors.joining;
import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.toPath;

/**
 * 低层 API 版本的朴素 RAG 示例（PGVector + 百炼聊天模型）。
 */
public class _01_Low_Level_Naive_RAG_Example_Pg {

    /**
     * This example demonstrates how to use low-level LangChain4j APIs to implement RAG.
     * 此示例演示如何使用 LangChain4j 的低层 API 来实现 RAG。
     * Check other packages to see examples of using high-level API (AI Services).
     * 可查看其他包以了解使用高层 API（AI Services）的示例。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        // Load the document that includes the information you'd like to "chat" about with the model.
        // 加载包含你想与模型“聊天”信息的文档。
        DocumentParser documentParser = new TextDocumentParser();
        Document document = loadDocument(toPath("example-files/story-about-happy-carrot.txt"), documentParser);

        // Split document into segments 100 tokens each
        // 将文档切分为每段 100 个 token 的片段
        DocumentSplitter splitter = DocumentSplitters.recursive(
                300,
                0
        );
        List<TextSegment> segments = splitter.split(document);

        // Embed segments (convert them into vectors that represent the meaning) using embedding model
        // 使用嵌入模型对片段进行嵌入（将其转换为表示语义的向量）
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // Store embeddings into embedding store for further search / retrieval
        // 将嵌入存入嵌入存储，便于后续搜索/检索
        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(embeddingModel);
        embeddingStore.addAll(embeddings, segments);

        // Specify the question you want to ask the model
        // 指定你要向模型提问的问题
        String question = "Who is Charlie?";

        // Embed the question
        // 对问题进行嵌入
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // Find relevant embeddings in embedding store by semantic similarity
        // 通过语义相似度在嵌入存储中查找相关嵌入
        // You can play with parameters below to find a sweet spot for your specific use case
        // 你可以调整下面的参数，以找到适合你用例的最佳点
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(3)
                .minScore(0.7)
                .build();
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings = embeddingStore.search(embeddingSearchRequest).matches();

        // Create a prompt for the model that includes question and relevant embeddings
        // 为模型创建包含问题和相关嵌入信息的提示词
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Answer the following question to the best of your ability:\n"
                        + "\n"
                        + "Question:\n"
                        + "{{question}}\n"
                        + "\n"
                        + "Base your answer on the following information:\n"
                        + "{{information}}");

        String information = relevantEmbeddings.stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", information);

        Prompt prompt = promptTemplate.apply(variables);

        // Send the prompt to the DashScope chat model
        // 将提示词发送给百炼聊天模型
        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();
        AiMessage aiMessage = chatModel.chat(prompt.toUserMessage()).aiMessage();

        // See an answer from the model
        // 查看模型的回答
        String answer = aiMessage.text();
        System.out.println(answer); // Charlie is a cheerful carrot living in VeggieVille...
        // Charlie 是一根住在 VeggieVille 的开朗胡萝卜……
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
