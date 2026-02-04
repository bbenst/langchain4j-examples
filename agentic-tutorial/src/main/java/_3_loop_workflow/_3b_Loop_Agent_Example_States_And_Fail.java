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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class _3b_Loop_Agent_Example_States_And_Fail {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * Here we build the same loop-agent as in 3a, but this time we should see it fail
     * 这里我们构建与 3a 相同的循环智能体，但这次应该会失败
     * by trying to tailor the CV to a job description that doesn't fit.
     * 因为尝试把简历定制到不匹配的职位描述上。
     * We will also return the latest score and feedback, on top of the final CV,
     * 我们还会在最终简历之外返回最新分数和反馈，
     * which will allow us to check if we obtained a good score and if it's worth handing in this CV.
     * 以便判断分数是否足够好以及是否值得投递这份简历。
     * We also show a trick to inspect the intermediary states of the review (it gets overwritten in every loop)
     * 我们还展示一种查看评审中间状态的技巧（每次循环都会被覆盖）
     * by storing them in a list each time the exit condition is checked (ie. after every agent invocation).
     * 即在每次检查退出条件时（也就是每次智能体调用后）保存到列表。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. Create all sub-agents (same as before)
        // 1. 创建所有子智能体（与之前相同）
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

        // 2. Build the sequence and store the reviews on each exit condition check
        // 2. 构建序列，并在每次检查退出条件时存储评审结果
        // It can be important to know whether the exit condition was met or just the max iterations
        // 了解是满足了退出条件还是仅仅达到最大迭代次数可能很重要
        // (eg. John may not even want to bother applying for this job).
        //（例如 John 可能根本不想申请这份工作）
        // You can change the output variable to also contain the last score and feedback, and check yourself after the loop finished.
        // 你可以把输出变量改为同时包含最后的分数和反馈，并在循环结束后自行检查。
        // You can also store the intermediary values in a mutable list to inspect later.
        // 也可以把中间值存到可变列表里以便稍后检查。
        // The code below does both things at the same time.
        // 下面的代码同时做了这两件事。
        List<CvReview> reviewHistory = new ArrayList<>();

        UntypedAgent reviewedCvGenerator = AgenticServices // use UntypedAgent unless you define the resulting composed agent, see below
                // 除非你定义了组合后的智能体，否则使用 UntypedAgent（见下方）
                .loopBuilder().subAgents(cvReviewer, scoredCvTailor) // this can be as many as you want, order matters
                // 子智能体数量不限，但顺序很重要
                .outputKey("cvAndReview") // this is the final output we want to observe
                // 这是我们要观察的最终输出
                .output(agenticScope -> {
                    Map<String, Object> cvAndReview = Map.of(
                            "cv", agenticScope.readState("cv"),
                            "finalReview", agenticScope.readState("cvReview")
                    );
                    return cvAndReview;
                })
                .exitCondition(scope -> {
                    CvReview review = (CvReview) scope.readState("cvReview");
                    reviewHistory.add(review); // capture the score+feedback at every agent invocation
                    // 在每次智能体调用时记录分数与反馈
                    System.out.println("Exit check with score=" + review.score);
                    return review.score >= 0.8;
                })
                .maxIterations(3) // safety to avoid infinite loops, in case exit condition is never met
                // 安全措施，避免退出条件未满足时出现无限循环
                .build();

        // 3. Load the original arguments from text files in resources/documents/
        // 3. 从 resources/documents/ 中加载原始参数
        // - master_cv.txt
        // - master_cv.txt
        // - job_description_backend.txt
        // - job_description_backend.txt
        String masterCv = StringLoader.loadFromResource("/documents/master_cv.txt");
        String fluteJobDescription = "We are looking for a passionate flute teacher to join our music academy.";

        // 4. Because we use an untyped agent, we need to pass a map of arguments
        // 4. 因为使用了无类型智能体，需要传入参数映射
        Map<String, Object> arguments = Map.of(
                "cv", masterCv, // start with the master CV, it will be continuously improved
                // 从主简历开始，会持续改进
                "jobDescription", fluteJobDescription
        );

        // 5. Call the composed agent to generate the tailored CV
        // 5. 调用组合智能体生成定制简历
        Map<String, Object> cvAndReview = (Map<String, Object>) reviewedCvGenerator.invoke(arguments);

        // You can observe the steps in the logs, for example:
        // 你可以在日志中观察步骤，例如：
        // Round 1 output: "content": "{\n  \"score\": 0.0,\n  \"feedback\": \"This CV is not suitable for the flute teacher position at our music academy...
        // 第 1 轮输出："content": "{\n  \"score\": 0.0,\n  \"feedback\": \"这份简历不适合我们音乐学院的长笛教师岗位...
        // Round 2 output: "content": "{\n  \"score\": 0.3,\n  \"feedback\": \"John's CV demonstrates strong soft skills such as communication, patience, and adaptability, which are important in a teaching role. However, the absence of formal music training or ...
        // 第 2 轮输出："content": "{\n  \"score\": 0.3,\n  \"feedback\": \"John 的简历展示了沟通、耐心、适应力等重要软技能，但缺乏正式音乐训练或...
        // Round 3 output: "content": "{\n  \"score\": 0.4,\n  \"feedback\": \"John Doe demonstrates strong soft skills and mentoring experience,...
        // 第 3 轮输出："content": "{\n  \"score\": 0.4,\n  \"feedback\": \"John Doe 展示了良好的软技能和辅导经验，...

        System.out.println("=== REVIEWED CV FOR FLUTE TEACHER ===");
        System.out.println(cvAndReview.get("cv")); // the final CV after the loop
        // 循环结束后的最终简历

        // now you get the finalReview in the output map so you can check
        // 现在输出 map 中包含 finalReview，可用于检查
        // if the final score and feedback meet your requirements
        // 最终分数和反馈是否满足需求
        CvReview review = (CvReview) cvAndReview.get("finalReview");
        System.out.println("=== FINAL REVIEW FOR FLUTE TEACHER ===");
        System.out.println("CV" + (review.score >= 0.8 ? " passes" : " does not pass") + " with score=" + review.score);
        System.out.println("Final feedback: " + review.feedback);

        // in reviewHistory you find the full history of reviews
        // 在 reviewHistory 中可以看到完整的评审历史
        System.out.println("=== FULL REVIEW HISTORY FOR FLUTE TEACHER ===");
        System.out.println(reviewHistory);

    }
}
