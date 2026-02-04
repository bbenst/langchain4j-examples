package _9_human_in_the_loop;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import util.ChatModelProvider;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.util.Map;
import java.util.Scanner;

public class _9b_HumanInTheLoop_Chatbot_With_Memory {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * This example demonstrates a back-and-forth loop with human-in-the-loop interaction,
     * 本示例演示带有人在回路交互的往复循环，
     * until an end-goal is reached (exit condition), after which the rest of the workflow
     * 直到达到目标（退出条件），然后其余流程
     * can continue.
     * 才能继续执行。
     * The loop continues until the human confirms availability, which is verified by an AiService.
     * 循环会持续到人类确认可用时间，由 AiService 进行验证。
     * When no slot is found, the loop ends after 5 iterations.
     * 若未找到合适时间，循环在 5 次迭代后结束。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) {

        // 1. Define sub-agent
        // 1. 定义子智能体
        MeetingProposer proposer = AgenticServices
                .agentBuilder(MeetingProposer.class)
                .chatModel(CHAT_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(15)) // so the agent remembers what he proposed already
                // 让智能体记住已提议的内容
                .outputKey("proposal")
                .build();

        // 2. Add an AiService to judge if a decision has been reached (this can be a tiny local model because the assignment is so simple)
        // 2. 添加 AiService 判断是否已达成决定（任务简单时可用小型本地模型）
        DecisionsReachedService decisionService = AiServices.create(DecisionsReachedService.class, CHAT_MODEL);

        // 2. Define Human-in-the-loop agent
        // 2. 定义人在回路智能体
        HumanInTheLoop humanInTheLoop = AgenticServices
                .humanInTheLoopBuilder()
                .description("agent that asks input from the user")
                .outputKey("candidateAnswer") // matches one of the proposer's input variable names
                // 与提议者输入变量名之一对应
                .inputKey("proposal") // must match the output of the proposer agent
                // 必须与提议者智能体的输出匹配
                .requestWriter(request -> {
                    System.out.println(request);
                    System.out.print("> ");
                })
                .responseReader(() -> new Scanner(System.in).nextLine())
                .async(true) // no need to block the entire program while waiting for user input
                // 等待用户输入时无需阻塞整个程序
                .build();

        // 3. construct the loop
        // 3. 构建循环
        // Here we only want the exit condition to be checked once per loop, not after every agent invocation,
        // 这里我们只希望每个循环检查一次退出条件，而不是每次智能体调用后检查
        // so we bundle both agents in a sequence and give it as one agent to the loop
        // 因此把两个智能体打包成序列，作为一个智能体交给循环
        UntypedAgent agentSequence = AgenticServices
                .sequenceBuilder()
                .subAgents(proposer, humanInTheLoop)
                .output(agenticScope -> Map.of(
                        "proposal", agenticScope.readState("proposal"),
                        "candidateAnswer", agenticScope.readState("candidateAnswer")
                ))
                .outputKey("proposalAndAnswer")
                // this output contains the last date proposal + candidate's answer, which should be sufficient info for a followup agent to schedule the meeting (or abort trying)
                // 该输出包含最后一次日期提议与候选人回答，足以让后续智能体安排会议（或放弃尝试）
                .build();

        UntypedAgent schedulingLoop = AgenticServices
                .loopBuilder()
                .subAgents(agentSequence)
                .exitCondition(scope -> {
                    System.out.println("--- checking exit condition ---");
                    String response = (String) scope.readState("candidateAnswer");
                    String proposal = (String) scope.readState("proposal");
                    return response != null && decisionService.isDecisionReached(proposal, response);
                })
                .outputKey("proposalAndAnswer")
                .maxIterations(5)
                .build();

        // 4. Run the scheduling loop
        // 4. 运行排期循环
        Map<String, Object> input = Map.of("meetingTopic", "on-site visit",
                "candidateAnswer", "hi", // this variable needs to be present in the AgenticScope in advance because the MeetingProposer takes it as input
                // 该变量需要预先存在于 AgenticScope 中，因为 MeetingProposer 将其作为输入
                "memoryId", "user-1234"); // if we don't put a memoryId, the proposer agent will not remember what he proposed already
                // 若不提供 memoryId，提议者智能体不会记住已提议内容

        var lastProposalAndAnswer = schedulingLoop.invoke(input);

        System.out.println("=== Result: last proposalAndAnswer ===");
        System.out.println(lastProposalAndAnswer);
    }
}
