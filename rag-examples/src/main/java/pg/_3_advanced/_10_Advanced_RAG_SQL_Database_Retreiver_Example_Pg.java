package pg._3_advanced;

import pg._2_naive.Naive_RAG_Example_Pg;

import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.h2.jdbcx.JdbcDataSource;
import shared.Assistant;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static shared.Utils.DASHSCOPE_API_KEY;
import static shared.Utils.DASHSCOPE_CHAT_MODEL;
import static shared.Utils.startConversationWith;
import static shared.Utils.toPath;

public class _10_Advanced_RAG_SQL_Database_Retreiver_Example_Pg {


    /**
     * Please refer to {@link Naive_RAG_Example_Pg} for a basic context.
     * 基础背景请参见 {@link Naive_RAG_Example_Pg}。
     * <p>
     * Advanced RAG in LangChain4j is described here: https://github.com/langchain4j/langchain4j/pull/538
     * LangChain4j 中的高级 RAG 说明见： https://github.com/langchain4j/langchain4j/pull/538
     * <p>
     * This example demonstrates how to use SQL database content retriever.
     * 本示例演示如何使用 SQL 数据库内容检索器。
     * <p>
     * WARNING! Although fun and exciting, {@link SqlDatabaseContentRetriever} is dangerous to use!
     * 警告！虽然很有趣也很刺激，但 {@link SqlDatabaseContentRetriever} 使用起来很危险！
     * Do not ever use it in production! The database user must have very limited READ-ONLY permissions!
     * 绝不要在生产环境使用！数据库用户必须只有极其有限的只读权限！
     * Although the generated SQL is somewhat validated (to ensure that the SQL is a SELECT statement),
     * 虽然生成的 SQL 会做一定验证（确保是 SELECT 语句），
     * there is no guarantee that it is harmless. Use it at your own risk!
     * 但仍无法保证无害。使用风险自负！
     * <p>
     * In this example we will use an in-memory H2 database with 3 tables: customers, products and orders.
     * 本示例将使用内存 H2 数据库，包含三张表：customers、products 和 orders。
     * See "resources/sql" directory for more details.
     * 更多细节请参见 "resources/sql" 目录。
     * <p>
     * This example requires "langchain4j-experimental-sql" dependency.
     * 本示例需要 "langchain4j-experimental-sql" 依赖。
     */

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {

        Assistant assistant = createAssistant();

        // You can ask questions such as "How many customers do we have?" and "What is our top seller?".
        // 你可以提问，例如“我们有多少客户？”和“我们的畅销产品是什么？”。
        startConversationWith(assistant);
    }

    /**
     * 创建具备 SQL 检索能力的助手。
     *
     * @return AI 助手实例
     */
    private static Assistant createAssistant() {

        DataSource dataSource = createDataSource();

        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(DASHSCOPE_API_KEY)
                .modelName(DASHSCOPE_CHAT_MODEL)
                .build();

        ContentRetriever contentRetriever = SqlDatabaseContentRetriever.builder()
                .dataSource(dataSource)
                .chatModel(chatModel)
                .build();

        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * 创建并初始化内存 H2 数据源。
     *
     * @return 数据源
     */
    private static DataSource createDataSource() {

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        String createTablesScript = read("sql/create_tables.sql");
        execute(createTablesScript, dataSource);

        String prefillTablesScript = read("sql/prefill_tables.sql");
        execute(prefillTablesScript, dataSource);

        return dataSource;
    }

    /**
     * 读取类路径下的 SQL 文件。
     *
     * @param path 资源路径
     * @return SQL 脚本内容
     * @throws RuntimeException 当读取失败时抛出
     */
    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(toPath(path)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行 SQL 脚本。
     *
     * @param sql SQL 脚本内容
     * @param dataSource 数据源
     * @throws RuntimeException 当执行失败时抛出
     */
    private static void execute(String sql, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sqlStatement : sql.split(";")) {
                statement.execute(sqlStatement.trim());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
