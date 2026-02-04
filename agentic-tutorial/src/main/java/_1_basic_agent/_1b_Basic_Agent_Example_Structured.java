package _1_basic_agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import domain.Cv;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;

public class _1b_Basic_Agent_Example_Structured {
    /**
     * This example implements the same CvGenerator agent as in 1a,
     * 本示例实现了与 1a 相同的 CvGenerator 智能体，
     * but this version will return a custom Java objects, Cv, as defined in model/Cv.java
     * 但此版本将返回一个自定义的 Java 对象 Cv，定义在 model/Cv.java 中
     */

    // Set logging level
    // 设置日志级别
    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    // 1. Define the model that will power the agent
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the agent behavior in agent_interfaces/CvGeneratorStructuredOutput.java
        // 2. 在 agent_interfaces/CvGeneratorStructuredOutput.java 中定义智能体行为

        // 3. Create the agent using AgenticServices
        // 3. 使用 AgenticServices 创建智能体
        CvGeneratorStructuredOutput cvGeneratorStructuredOutput = AgenticServices
                .agentBuilder(CvGeneratorStructuredOutput.class)
                .chatModel(CHAT_MODEL)
                .build();

        // 4. Load text file from resources/documents/user_life_story.txt
        // 4. 从 resources/documents/user_life_story.txt 加载文本
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");

        // 5. Retrieve a Cv object from the agent
        // 5. 从智能体获取 Cv 对象
        Cv cvStructured = cvGeneratorStructuredOutput.generateCv(lifeStory);

        System.out.println("\n\n=== CV OBJECT ===");
        System.out.println(cvStructured);
    }
}
