package _4_parallel_workflow;

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
import java.util.concurrent.Executors;

public class _4_Parallel_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

     /**
     * This example demonstrates how to implement 3 parallel CvReviewer agents that will
     * 本示例演示如何实现 3 个并行的 CvReviewer 智能体
     * evaluate the CV simultaneously. We will implement three agents:
     * 同时评估简历。我们将实现三个智能体：
     * - ManagerCvReviewer (judges how well the candidate will likely do the job)
     * - ManagerCvReviewer（评估候选人胜任工作的可能性）
     *      input: CV and job description
     *      输入：简历和职位描述
     * - TeamMemberCvReviewer (judges how well the candidate will fit in the team)
     * - TeamMemberCvReviewer（评估候选人与团队的契合度）
     *      input: CV
     *      输入：简历
     * - HrCvReviewer (checks if the candidate qualifies from HR point of view)
     * - HrCvReviewer（从 HR 角度判断候选人是否合格）
     *      input: CV, HR requirements
     *      输入：简历、HR 要求
     */

    // 1. Define the model that will power the agents
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the three sub-agents in this package:
        // 2. 定义本包内的三个子智能体：
        //      - HrCvReviewer.java
        //      - HrCvReviewer.java
        //      - ManagerCvReviewer.java
        //      - ManagerCvReviewer.java
        //      - TeamMemberCvReviewer.java
        //      - TeamMemberCvReviewer.java

        // 3. Create all agents using AgenticServices
        // 3. 使用 AgenticServices 创建所有智能体
        HrCvReviewer hrCvReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview") // this will be overwritten in every iteration, and also be used as the final output we want to observe
                // 每次迭代都会覆盖，也会作为我们要观察的最终输出
                .build();

        ManagerCvReviewer managerCvReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview") // this overwrites the original input instructions, and is overwritten in every iteration and used as new instructions for the CvTailor
                // 覆盖原始输入指令，并在每次迭代中覆盖，用作 CvTailor 的新指令
                .build();

        TeamMemberCvReviewer teamMemberCvReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview") // this overwrites the original input instructions, and is overwritten in every iteration and used as new instructions for the CvTailor
                // 覆盖原始输入指令，并在每次迭代中覆盖，用作 CvTailor 的新指令
                .build();

        // 4. Build the sequence
        // 4. 构建序列
        var executor = Executors.newFixedThreadPool(3);  // keep a reference for later closing
        // 保留引用以便稍后关闭

        UntypedAgent cvReviewGenerator = AgenticServices // use UntypedAgent unless you define the resulting composed agent, see _2_Sequential_Agent_Example
                // 除非你定义了组合后的智能体，否则使用 UntypedAgent（见 _2_Sequential_Agent_Example）
                .parallelBuilder()
                .subAgents(hrCvReviewer, managerCvReviewer, teamMemberCvReviewer) // this can be as many as you want
                // 子智能体数量不限
                .executor(executor) // optional, by default an internal cached thread pool is used which will automatically shut down after execution is completed
                // 可选；默认使用内部缓存线程池，执行完成后会自动关闭
                .outputKey("fullCvReview") // this is the final output we want to observe
                // 这是我们要观察的最终输出
                .output(agenticScope -> {
                    // read the outputs of each reviewer from the agentic scope
                    // 从 agenticScope 中读取各评审的输出
                    CvReview hrReview = (CvReview) agenticScope.readState("hrReview");
                    CvReview managerReview = (CvReview) agenticScope.readState("managerReview");
                    CvReview teamMemberReview = (CvReview) agenticScope.readState("teamMemberReview");
                    // return a bundled review with averaged score (or any other aggregation you want here)
                    // 返回合并后的评审结果，分数取平均值（也可按需聚合）
                    String feedback = String.join("\n",
                            "HR Review: " + hrReview.feedback,
                            "Manager Review: " + managerReview.feedback,
                            "Team Member Review: " + teamMemberReview.feedback
                    );
                    double avgScore = (hrReview.score + managerReview.score + teamMemberReview.score) / 3.0;

                    return new CvReview(avgScore, feedback);
                        })
                .build();

        // 5. Load the original arguments from text files in resources/documents/
        // 5. 从 resources/documents/ 中加载原始参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // 6. Because we use an untyped agent, we need to pass a map of arguments
        // 6. 因为使用了无类型智能体，需要传入参数映射
        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "jobDescription", jobDescription
                ,"hrRequirements", hrRequirements
                ,"phoneInterviewNotes", phoneInterviewNotes
        );

        // 7. Call the composed agent to generate the tailored CV
        // 7. 调用组合智能体生成评审结果
        var review = cvReviewGenerator.invoke(arguments);

        // 8. and print the generated CV
        // 8. 并打印生成的评审结果
        System.out.println("=== REVIEWED CV ===");
        System.out.println(review);

        // 9. Shutdown executor
        // 9. 关闭线程池
        executor.shutdown();
   }
}
