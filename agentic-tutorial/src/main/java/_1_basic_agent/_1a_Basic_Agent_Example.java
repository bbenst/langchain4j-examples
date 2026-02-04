package _1_basic_agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;

public class _1a_Basic_Agent_Example {

    /**
     * This example demonstrates how to implement a basic agent to demonstrate the syntax
     * 本示例演示如何实现一个基础智能体来展示语法
     * Note that agents are only useful when combined with other agents, which we will show in the next steps.
     * 注意：智能体只有与其他智能体结合时才更有用，后续会展示
     * For just one agent, you can better use an AiService.
     * 对于单个智能体，更推荐使用 AiService。
     *
     * This basic agent turns a user's life story into a clean and complete CV.
     * 这个基础智能体会把用户的人生故事转换成干净完整的简历。
     * Note that running this can take a while because the outputted CV
     * 注意运行可能需要一段时间，因为生成的简历
     * will be quite lengthy and the model needs a while.
     * 会比较长，模型处理也需要时间。
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

        // 2. Define the agent behavior in agent_interfaces/CvGenerator.java
        // 2. 在 agent_interfaces/CvGenerator.java 中定义智能体行为

        // 3. Create the agent using AgenticServices
        // 3. 使用 AgenticServices 创建智能体
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("masterCv") // we can optionally define the key of the output object
                // 我们可以选择性地定义输出对象的 key
                .build();

        // 4. Load text file from resources/documents/user_life_story.txt
        // 4. 从 resources/documents/user_life_story.txt 加载文本
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");

        // 5. We call the agent to generate the CV
        // 5. 调用智能体生成简历
        String cv = cvGenerator.generateCv(lifeStory);

        // 6. and print the generated CV
        // 6. 并打印生成的简历
        System.out.println("=== CV ===");
        System.out.println(cv);

        // In example 1b we'll build the same agent but with structured output
        // 在示例 1b 中，我们将构建带结构化输出的同一智能体

    }
}
