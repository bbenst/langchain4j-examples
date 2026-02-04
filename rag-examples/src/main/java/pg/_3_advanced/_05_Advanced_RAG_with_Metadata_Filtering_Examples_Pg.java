package pg._3_advanced;

import pg._2_naive.Naive_RAG_Example_Pg;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.builder.sql.LanguageModelSqlFilterBuilder;
import dev.langchain4j.store.embedding.filter.builder.sql.TableDefinition;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.junit.jupiter.api.Test;
import shared.Assistant;
import shared.Utils;

import java.util.function.Function;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元数据过滤相关示例（PGVector + 百炼聊天模型）。
 */
class _05_Advanced_RAG_with_Metadata_Filtering_Examples_Pg {

    /**
     * Please refer to {@link Naive_RAG_Example_Pg} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example_Pg}。
     * More information on metadata filtering can be found here: https://github.com/langchain4j/langchain4j/pull/610
     * 有关元数据过滤的更多信息见： https://github.com/langchain4j/langchain4j/pull/610
     */

    /**
     * 百炼聊天模型，用于生成过滤器与对话回答。
     */
    ChatModel chatModel = QwenChatModel.builder()
            .apiKey(Utils.DASHSCOPE_API_KEY)
            .modelName(Utils.DASHSCOPE_CHAT_MODEL)
            .build();

    /**
     * 本地嵌入模型，用于生成向量。
     */
    EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

    /**
     * 静态元数据过滤示例。
     */
    @Test
    void Static_Metadata_Filter_Example() {

        // given
        // 前置条件
        TextSegment dogsSegment = TextSegment.from("Article about dogs ...", metadata("animal", "dog"));
        TextSegment birdsSegment = TextSegment.from("Article about birds ...", metadata("animal", "bird"));

        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore();
        embeddingStore.add(embeddingModel.embed(dogsSegment).content(), dogsSegment);
        embeddingStore.add(embeddingModel.embed(birdsSegment).content(), birdsSegment);
        // embeddingStore contains segments about both dogs and birds
        // embeddingStore 包含关于狗和鸟的片段

        Filter onlyDogs = metadataKey("animal").isEqualTo("dog");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(onlyDogs) // by specifying the static filter, we limit the search to segments only about dogs
                // 通过指定静态过滤器，我们将搜索限制为仅关于狗的片段
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        // when
        // 执行
        String answer = assistant.answer("Which animal?");

        // then
        // 断言
        assertThat(answer)
                .containsIgnoringCase("dog")
                .doesNotContainIgnoringCase("bird");
    }


    /**
     * 基于用户 ID 进行个性化检索的助手接口。
     */
    interface PersonalizedAssistant {

        /**
         * 与助手进行对话，并根据用户 ID 应用动态过滤。
         *
         * @param userId 用户 ID
         * @param userMessage 用户输入
         * @return 助手回复
         */
        String chat(@MemoryId String userId, @dev.langchain4j.service.UserMessage String userMessage);
    }

    /**
     * 动态元数据过滤示例。
     */
    @Test
    void Dynamic_Metadata_Filter_Example() {

        // given
        // 前置条件
        TextSegment user1Info = TextSegment.from("My favorite color is green", metadata("userId", "1"));
        TextSegment user2Info = TextSegment.from("My favorite color is red", metadata("userId", "2"));

        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore();
        embeddingStore.add(embeddingModel.embed(user1Info).content(), user1Info);
        embeddingStore.add(embeddingModel.embed(user2Info).content(), user2Info);
        // embeddingStore contains information about both first and second user
        // embeddingStore 包含第一位和第二位用户的信息

        Function<Query, Filter> filterByUserId =
                (query) -> metadataKey("userId").isEqualTo(query.metadata().chatMemoryId().toString());

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                // by specifying the dynamic filter, we limit the search to segments that belong only to the current user
                // 通过指定动态过滤器，我们将搜索限制为仅属于当前用户的片段
                .dynamicFilter(filterByUserId)
                .build();

        PersonalizedAssistant personalizedAssistant = AiServices.builder(PersonalizedAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        // when
        // 执行
        String answer1 = personalizedAssistant.chat("1", "Which color would be best for a dress?");

        // then
        // 断言
        assertThat(answer1)
                .containsIgnoringCase("green")
                .doesNotContainIgnoringCase("red");

        // when
        // 执行
        String answer2 = personalizedAssistant.chat("2", "Which color would be best for a dress?");

        // then
        // 断言
        assertThat(answer2)
                .containsIgnoringCase("red")
                .doesNotContainIgnoringCase("green");
    }

    /**
     * 由 LLM 生成 SQL 过滤条件的示例。
     */
    @Test
    void LLM_generated_Metadata_Filter_Example() {

        // given
        // 前置条件
        TextSegment forrestGump = TextSegment.from("Forrest Gump", metadata("genre", "drama").put("year", 1994));
        TextSegment groundhogDay = TextSegment.from("Groundhog Day", metadata("genre", "comedy").put("year", 1993));
        TextSegment dieHard = TextSegment.from("Die Hard", metadata("genre", "action").put("year", 1998));

        // describe metadata keys as if they were columns in the SQL table
        // 将元数据键描述为 SQL 表中的列
        TableDefinition tableDefinition = TableDefinition.builder()
                .name("movies")
                .addColumn("genre", "VARCHAR", "one of: [comedy, drama, action]")
                .addColumn("year", "INT")
                .build();

        LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(chatModel, tableDefinition);

        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore();
        embeddingStore.add(embeddingModel.embed(forrestGump).content(), forrestGump);
        embeddingStore.add(embeddingModel.embed(groundhogDay).content(), groundhogDay);
        embeddingStore.add(embeddingModel.embed(dieHard).content(), dieHard);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .dynamicFilter(query -> sqlFilterBuilder.build(query)) // LLM will generate the filter dynamically
                // LLM 将动态生成过滤器
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        // when
        // 执行
        String answer = assistant.answer("Recommend me a good drama from 90s");

        // then
        // 断言
        assertThat(answer)
                .containsIgnoringCase("Forrest Gump")
                .doesNotContainIgnoringCase("Groundhog Day")
                .doesNotContainIgnoringCase("Die Hard");
    }

    /**
     * 创建 PGVector 嵌入存储。
     *
     * @return 嵌入存储
     */
    private EmbeddingStore<TextSegment> createEmbeddingStore() {
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
