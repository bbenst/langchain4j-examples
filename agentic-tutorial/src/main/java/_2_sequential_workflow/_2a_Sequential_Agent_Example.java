package _2_sequential_workflow;

import _1_basic_agent.CvGenerator;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _2a_Sequential_Agent_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * This example demonstrates how to implement two agents:
     * 本示例演示如何实现两个智能体：
     * - CvGenerator (takes in a life story and generates a complete master CV)
     * - CvGenerator（输入人生故事并生成完整的主简历）
     * - CvTailor (takes in the master CV and tailors it to specific instructions (job description, feedback, ...)
     * - CvTailor（输入主简历并根据具体指令进行定制，如职位描述、反馈等）
     * Then we will call them one after in a fixed workflow
     * 然后我们会在固定工作流中按顺序调用它们，
     * using the sequenceBuilder, and demonstrate how to pass a parameter between them.
     * 使用 sequenceBuilder，并演示如何在它们之间传递参数。
     * When combining multiple agents, all input, intermediary, and output parameters and the call chain are
     * 当组合多个智能体时，所有输入、中间与输出参数以及调用链
     * stored in the AgenticScope, which is accessible for advanced use cases.
     * 都存储在 AgenticScope 中，便于高级用例访问。
     */

    // 1. Define the model that will power the agents
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the two sub-agents in this package:
        // 2. 定义本包内的两个子智能体：
        //      - CvGenerator.java
        //      - CvGenerator.java
        //      - CvTailor.java
        //      - CvTailor.java

        // 3. Create both agents using AgenticServices
        // 3. 使用 AgenticServices 创建两个智能体
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("masterCv") // if you want to pass this variable from agent 1 to agent 2,
                // 如果要把该变量从智能体 1 传给智能体 2，
                // then make sure the output key here matches the input variable name
                // 请确保这里的输出 key 与第二个智能体的输入变量名一致
                // specified in the second agent interface agent_interfaces/CvTailor.java
                // （在第二个智能体接口 agent_interfaces/CvTailor.java 中指定）
                .build();
        CvTailor cvTailor = AgenticServices
                .agentBuilder(CvTailor.class)
                .chatModel(CHAT_MODEL) // note that it is also possible to use a different model for a different agent
                // 注意：也可以为不同的智能体使用不同的模型
                .outputKey("tailoredCv") // we need to define the key of the output object
                // 我们需要定义输出对象的 key
                // if we would put "masterCv" here, the original master CV would be overwritten
                // 如果这里写成 "masterCv"，原始主简历会被覆盖
                // by the second agent. In this case we don't want this, but it's a useful feature.
                // 被第二个智能体覆盖。本例不希望这样，但这是一个有用的特性。
                .build();

        ////////////////// UNTYPED EXAMPLE //////////////////////
        ////////////////// 无类型示例 //////////////////////

        // 4. Build the sequence
        // 4. 构建序列
        UntypedAgent tailoredCvGenerator = AgenticServices // use UntypedAgent unless you define the resulting composed agent, see below
                // 除非你定义了组合后的智能体，否则使用 UntypedAgent（见下方）
                .sequenceBuilder()
                .subAgents(cvGenerator, cvTailor) // this can be as many as you want, order matters
                // 子智能体数量不限，但顺序很重要
                .outputKey("tailoredCv") // this is the final output of the composed agent
                // 这是组合智能体的最终输出
                // note that you can use as output any field that is part of the AgenticScope
                // 注意：你可以把 AgenticScope 中的任意字段作为输出
                // for example you could output 'masterCv' instead of tailoredCv (even if in this case that makes no sense)
                // 例如你可以输出 'masterCv' 而不是 tailoredCv（尽管这里没意义）
                .build();

        // 4. Load the arguments from text files in resources/documents/
        // 4. 从 resources/documents/ 中加载参数
        // - user_life_story.txt
        // - user_life_story.txt
        // - job_description_backend.txt
        // - job_description_backend.txt
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String instructions = "Adapt the CV to the job description below." + StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. Because we use an untyped agent, we need to pass a map of arguments
        // 5. 因为使用了无类型智能体，需要传入参数映射
        Map<String, Object> arguments = Map.of(
                "lifeStory", lifeStory, // matches the variable name in agent_interfaces/CvGenerator.java
                // 与 agent_interfaces/CvGenerator.java 中的变量名一致
                "instructions", instructions // matches the variable name in agent_interfaces/CvTailor.java
                // 与 agent_interfaces/CvTailor.java 中的变量名一致
        );

        // 5. Call the composed agent to generate the tailored CV
        // 5. 调用组合智能体生成定制简历
        String tailoredCv = (String) tailoredCvGenerator.invoke(arguments);

        // 6. and print the generated CV
        // 6. 并打印生成的简历
        System.out.println("=== TAILORED CV UNTYPED ===");
        System.out.println((String) tailoredCv); // you can observe that the CV looks very different
        // 你会看到简历差异很大
        // when you'd use job_description_fullstack.txt as input
        // 当输入改为 job_description_fullstack.txt 时

        // In example 2b we'll build the same sequential agent but with typed output,
        // 在示例 2b 中，我们将构建带类型输出的相同顺序智能体
        // and we'll inspect the AgenticScope
        // 并检查 AgenticScope

    }
}
