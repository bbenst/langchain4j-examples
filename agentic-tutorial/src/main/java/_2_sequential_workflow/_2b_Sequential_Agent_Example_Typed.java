package _2_sequential_workflow;

import _1_basic_agent.CvGenerator;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import util.AgenticScopePrinter;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _2b_Sequential_Agent_Example_Typed {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 150);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * We'll implement the same sequential workflow as in 2a, but this time we'll
     * 我们将实现与 2a 相同的顺序工作流，但这次我们会
     * - use a typed interface for the composed agent (SequenceCvGenerator)
     * - 为组合智能体使用带类型的接口（SequenceCvGenerator）
     * - which will allow us to use its method with arguments instead of .invoke(argsMap)
     * - 这样就可以用带参数的方法调用，而不是 .invoke(argsMap)
     * - collect the output in a custom way
     * - 以自定义方式收集输出
     * - retrieve and inspect the AgenticScope after invocation, for debugging or testing purposes
     * - 在调用后获取并检查 AgenticScope，用于调试或测试
     */

    // 1. Define the model that will power the agents
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the sequential agent interface in this package:
        // 2. 在本包中定义顺序智能体接口：
        //      - SequenceCvGenerator.java
        //      - SequenceCvGenerator.java
        // with method signature:
        // 方法签名：
        // ResultWithAgenticScope<Map<String, String>> generateTailoredCv(@V("lifeStory") String lifeStory, @V("instructions") String instructions);

        // 3. Create both sub-agents using AgenticServices like before
        // 3. 像之前一样使用 AgenticServices 创建两个子智能体
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


        // 4. Load the arguments from text files in resources/documents/
        // 4. 从 resources/documents/ 中加载参数
        // (no need to put them in a Map this time)
        //（这次不需要放入 Map）
        // - user_life_story.txt
        // - user_life_story.txt
        // - job_description_backend.txt
        // - job_description_backend.txt
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String instructions = "Adapt the CV to the job description below." + StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. Build the typed sequence with custom output handling
        // 5. 构建带类型的序列并自定义输出处理
        SequenceCvGenerator sequenceCvGenerator = AgenticServices
                .sequenceBuilder(SequenceCvGenerator.class) // here we specify the typed interface
                // 这里指定带类型的接口
                .subAgents(cvGenerator, cvTailor)
                .outputKey("bothCvsAndLifeStory")
                .output(agenticScope -> { // any method is possible, but we collect some internal variables.
                    // 方法不限，但这里我们收集一些内部变量
                    Map<String, String> bothCvsAndLifeStory = Map.of(
                            "lifeStory", agenticScope.readState("lifeStory", ""),
                            "masterCv", agenticScope.readState("masterCv", ""),
                            "tailoredCv", agenticScope.readState("tailoredCv", "")
                    );
                    return bothCvsAndLifeStory;
                    })
                .build();

        // 6. Call the typed composed agent
        // 6. 调用带类型的组合智能体
        ResultWithAgenticScope<Map<String,String>> bothCvsAndScope = sequenceCvGenerator.generateTailoredCv(lifeStory, instructions);

        // 7. Extract result and agenticScope
        // 7. 提取结果和 AgenticScope
        AgenticScope agenticScope = bothCvsAndScope.agenticScope();
        Map<String,String> bothCvsAndLifeStory = bothCvsAndScope.result();

        System.out.println("=== USER INFO (input) ===");
        String userStory = bothCvsAndLifeStory.get("lifeStory");
        System.out.println(userStory.length() > 100 ? userStory.substring(0, 100) + " [truncated...]" : lifeStory);
        System.out.println("=== MASTER CV TYPED (intermediary variable) ===");
        String masterCv = bothCvsAndLifeStory.get("masterCv");
        System.out.println(masterCv.length() > 100 ? masterCv.substring(0, 100) + " [truncated...]" : masterCv);
        System.out.println("=== TAILORED CV TYPED (output) ===");
        String tailoredCv = bothCvsAndLifeStory.get("tailoredCv");
        System.out.println(tailoredCv.length() > 100 ? tailoredCv.substring(0, 100) + " [truncated...]" : tailoredCv);

        // Both untyped and typed agents give the same tailoredCv result
        // 无类型和有类型智能体得到的 tailoredCv 结果相同
        // (any differences are due to the non-deterministic nature of LLMs),
        //（差异来自 LLM 的非确定性），
        // but the typed agent is more elegant to use and safer because of compile-time type checking
        // 但带类型的智能体更易用且更安全，因为有编译期类型检查

        System.out.println("=== AGENTIC SCOPE ===");
        System.out.println(AgenticScopePrinter.printPretty(agenticScope, 100));
        // this will return this object (filled out):
        // 将返回如下对象（已填充）：
        // AgenticScope {
        //     memoryId = "e705028d-e90e-47df-9709-95953e84878c",
        //             state = {
        //                     bothCvsAndLifeStory = { // output
        //                     bothCvsAndLifeStory = { // 输出
        //                             masterCv = "...",
        //                            lifeStory = "...",
        //                            tailoredCv = "..."
        //                     },
        //                     instructions = "...", // inputs and intermediary variables
        //                     instructions = "...", // 输入与中间变量
        //                     tailoredCv = "...",
        //                     masterCv = "...",
        //                     lifeStory = "..."
        //             }
        // }
        System.out.println("=== CONTEXT AS CONVERSATION (all messages in the conversation) ===");
        System.out.println(AgenticScopePrinter.printConversation(agenticScope.contextAsConversation(), 100));

    }
}
