package _3_loop_workflow;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _3a_Loop_Agent_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * This example demonstrates how to implement a CvReviewer agent that we can add to a loop
     * 本示例演示如何实现一个 CvReviewer 智能体，并将其加入循环
     * with our CvTailor agent. We will implement two agents:
     * 与 CvTailor 智能体配合使用。我们将实现两个智能体：
     * - ScoredCvTailor (takes in a CV and tailors it to a CvReview (feedback/instruction + score))
     * - ScoredCvTailor（输入简历并根据 CvReview（反馈/指令 + 分数）进行定制）
     * - CvReviewer (takes in the tailored CV and job description, and returns a CvReview object (feedback + score)
     * - CvReviewer（输入定制简历和职位描述，返回 CvReview 对象（反馈 + 分数））
     * Additionally, the loop ends when the score is above a certain threshold (e.g. 0.7) (exit condition)
     * 此外，当分数高于某个阈值（例如 0.7）时循环结束（退出条件）
     */

    // 1. Define the model that will power the agents
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the two sub-agents in this package:
        // 2. 定义本包内的两个子智能体：
        //      - CvReviewer.java
        //      - CvReviewer.java
        //      - CvTailor.java
        //      - CvTailor.java

        // 3. Create all agents using AgenticServices
        // 3. 使用 AgenticServices 创建所有智能体
        CvReviewer cvReviewer = AgenticServices.agentBuilder(CvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cvReview") // this gets updated in every iteration with new feedback for the next tailoring
                // 每次迭代都会更新，用于下一次定制的反馈
                .build();
        ScoredCvTailor scoredCvTailor = AgenticServices.agentBuilder(ScoredCvTailor.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv") // this will be updated in every iteration, continuously improving the CV
                // 每次迭代都会更新，持续改进简历
                .build();

        // 4. Build the sequence
        // 4. 构建序列
        UntypedAgent reviewedCvGenerator = AgenticServices // use UntypedAgent unless you define the resulting composed agent, see _2_Sequential_Agent_Example
                // 除非你定义了组合后的智能体，否则使用 UntypedAgent（见 _2_Sequential_Agent_Example）
                .loopBuilder().subAgents(cvReviewer, scoredCvTailor) // this can be as many as you want, order matters
                // 子智能体数量不限，但顺序很重要
                .outputKey("cv") // this is the final output we want to observe (the improved CV)
                // 这是我们要观察的最终输出（改进后的简历）
                .exitCondition(agenticScope -> {
                            CvReview review = (CvReview) agenticScope.readState("cvReview");
                            System.out.println("Checking exit condition with score=" + review.score); // we log intermediary scores
                            // 记录中间分数
                            return review.score > 0.8;
                        }) // exit condition based on the score given by the CvReviewer agent, when > 0.8 we are satisfied
                // 退出条件基于 CvReviewer 智能体给出的分数，> 0.8 则满足
                // note that the exit condition is checked after each agent invocation, not just after the entire loop
                // 注意：退出条件在每次智能体调用后检查，而不是仅在整个循环结束后检查
                .maxIterations(3) // safety to avoid infinite loops, in case exit condition is never met
                // 安全措施，避免退出条件未满足时出现无限循环
                .build();

        // 5. Load the original arguments from text files in resources/documents/
        // 5. 从 resources/documents/ 中加载原始参数
        // - master_cv.txt
        // - master_cv.txt
        // - job_description_backend.txt
        // - job_description_backend.txt
        String masterCv = StringLoader.loadFromResource("/documents/master_cv.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. Because we use an untyped agent, we need to pass a map of arguments
        // 5. 因为使用了无类型智能体，需要传入参数映射
        Map<String, Object> arguments = Map.of(
                "cv", masterCv, // start with the master CV, it will be continuously improved
                // 从主简历开始，会持续改进
                "jobDescription", jobDescription
        );

        // 5. Call the composed agent to generate the tailored CV
        // 5. 调用组合智能体生成定制简历
        String tailoredCv = (String) reviewedCvGenerator.invoke(arguments);

        // 6. and print the generated CV
        // 6. 并打印生成的简历
        System.out.println("=== REVIEWED CV UNTYPED ===");
        System.out.println((String) tailoredCv);

        // this CV probably passes after the first tailoring + review round
        // 这份简历可能在第一次定制 + 评审后就通过
        // if you want to see it fail, try with the flute teacher jobDescription
        // 如果想看到失败的情况，可以使用长笛教师的职位描述
        // as in example 3b, where we also inspect intermediary states of the CV
        // 如示例 3b 所示，我们还会检查简历的中间状态
        // and retrieve the final review and score as well.
        // 并获取最终评审和分数。

    }
}
