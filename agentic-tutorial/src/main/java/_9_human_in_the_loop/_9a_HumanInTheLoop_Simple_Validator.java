package _9_human_in_the_loop;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.util.Map;
import java.util.Scanner;

public class _9a_HumanInTheLoop_Simple_Validator {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);
        // 控制从模型调用中看到的日志量
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) {
        // 3. Create involved agents
        // 3. 创建相关智能体
        HiringDecisionProposer decisionProposer = AgenticServices.agentBuilder(HiringDecisionProposer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("modelDecision")
                .build();

        // 2. Define human in the loop for validation
        // 2. 定义用于校验的人在回路
        HumanInTheLoop humanValidator = AgenticServices.humanInTheLoopBuilder()
                .description("validates the model's proposed hiring decision")
                .inputKey("modelDecision")
                .outputKey("finalDecision") // checked by human
                // 由人工检查
                .requestWriter(request -> {
                    System.out.println("AI hiring assistant suggests: " + request);
                    System.out.println("Please confirm the final decision.");
                    System.out.println("Options: Invite on-site (I), Reject (R), Hold (H)");
                    System.out.print("> "); // we  needs input validation and error handling in real life systems
                    // 真实系统中需要输入校验和错误处理
                })
                .responseReader(() -> new Scanner(System.in).nextLine())
                .build();

        // 3. Chain agents into a workflow
        // 3. 将智能体串成工作流
        UntypedAgent hiringDecisionWorkflow = AgenticServices.sequenceBuilder()
                .subAgents(decisionProposer, humanValidator)
                .outputKey("finalDecision")
                .build();

        // 4. Prepare input arguments
        // 4. 准备输入参数
        Map<String, Object> input = Map.of(
                "cvReview", new CvReview(0.85,
                        """
                                Strong technical skills except for required React experience.
                                Seems a fast and independent learner though. Good cultural fit.
                                Potential issue with work permit that seems solvable.
                                Salary expectation slightly over planned budget.
                                Decision to proceed with onsite-interview.
                                """)
        );

        // 5. Run workflow
        // 5. 运行工作流
        String finalDecision = (String) hiringDecisionWorkflow.invoke(input);

        System.out.println("\n=== FINAL DECISION BY HUMAN ===");
        System.out.println("(Invite on-site (I), Reject (R), Hold (H))\n");
        System.out.println(finalDecision);

        // Note: human-in-the-loop and human validation can typically take long for the user to respond.
        // 注意：人在回路/人工校验通常需要较长响应时间。
        // In this case, async agents are recommended so they don't block the rest of the workflow
        // 这种情况下建议使用异步智能体，以免阻塞工作流的其余部分
        // that can potentially be executed before the user answer comes.
        // 这些部分可能在用户回答前就可以执行
    }
}
