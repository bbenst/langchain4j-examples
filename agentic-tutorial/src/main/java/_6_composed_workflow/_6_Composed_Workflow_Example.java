package _6_composed_workflow;

import _1_basic_agent.CvGenerator;
import _3_loop_workflow.CvReviewer;
import _3_loop_workflow.ScoredCvTailor;
import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
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

public class _6_Composed_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * Every agent, whether a single-task agent, a sequential workflow,..., is still an Agent object.
     * 无论是单任务智能体、顺序工作流等，本质上仍是 Agent 对象。
     * This makes agents fully composable. You can
     * 这使智能体可以完全组合。你可以：
     * - bundle smaller agents into super-agents
     * - 将更小的智能体组合成超级智能体
     * - decompose tasks with sub-agents
     * - 用子智能体分解任务
     * - mix sequential, parallel, loop, supervisor, ... workflows at any level
     * - 在任意层级混合顺序、并行、循环、监督等工作流
     * In this example, we’ll take the composed agents we built earlier (sequential, parallel, etc.)
     * 在本例中，我们将使用之前构建的组合智能体（顺序、并行等）
     * and combine them into two larger composed agents that orchestrate the entire application process.
     * 将它们组合成两个更大的组合智能体来编排整个招聘流程。
     */

    // 1. Define the model that will power the agents
    // 1. 定义驱动智能体的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        ////////////////// CANDIDATE COMPOSED WORKFLOW //////////////////////
        ////////////////// 候选人组合工作流 //////////////////////
        // We'll go from life story > CV > Review > review loop until we pass
        // 我们将从人生故事 > 简历 > 评审 > 评审循环直到通过
        // then email our CV to the company
        // 然后把简历发给公司

        // 1. Create all necessary agents for candidate workflow
        // 1. 为候选人工作流创建所有必要智能体
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv")
                .build();

        ScoredCvTailor scoredCvTailor = AgenticServices
                .agentBuilder(ScoredCvTailor.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cv")
                .build();

        CvReviewer cvReviewer = AgenticServices
                .agentBuilder(CvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("cvReview")
                .build();

        // 2. Create the loop workflow for CV improvement
        // 2. 创建简历改进的循环工作流
        UntypedAgent cvImprovementLoop = AgenticServices
                .loopBuilder()
                .subAgents(scoredCvTailor, cvReviewer)
                .outputKey("cv")
                .exitCondition(agenticScope -> {
                    CvReview review = (CvReview) agenticScope.readState("cvReview");
                    System.out.println("CV Review Score: " + review.score);
                    if (review.score >= 0.8)
                        System.out.println("CV is good enough, exiting loop.\n");
                        // 简历已足够好，退出循环
                    return review.score >= 0.8;
                })
                .maxIterations(3)
                .build();

        // 3. Create the complete candidate workflow: Generate > Review > Improve Loop
        // 3. 创建完整的候选人工作流：生成 > 评审 > 改进循环
        CandidateWorkflow candidateWorkflow = AgenticServices
                .sequenceBuilder(CandidateWorkflow.class)
                .subAgents(cvGenerator, cvReviewer, cvImprovementLoop)
                // here we use the composed agent cvImprovementLoop inside the sequenceBuilder
                // 这里在 sequenceBuilder 中使用组合智能体 cvImprovementLoop
                // we also need the cvReviewer in order to generate a first review before entering the loop
                // 还需要 cvReviewer 在进入循环前生成首次评审
                .outputKey("cv")
                .build();

        // 4. Load input data
        // 4. 加载输入数据
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. Execute the candidate workflow
        // 5. 执行候选人工作流
        String candidateResult = candidateWorkflow.processCandidate(lifeStory, jobDescription);
        // Note that input parameters and intermediate parameters are all stored in one AgenticScope
        // 注意：输入参数和中间参数都存储在同一个 AgenticScope 中
        // that is available to all agents in the system, no matter how many levels of composition we have
        // 无论组合层级多少，系统内所有智能体都可访问

        System.out.println("=== CANDIDATE WORKFLOW COMPLETED ===");
        System.out.println("Final CV: " + candidateResult);

        System.out.println("\n\n\n\n");

        ////////////////// HIRING TEAM COMPOSED WORKFLOW //////////////////////
        ////////////////// 招聘团队组合工作流 //////////////////////
        // We receive an email with the candidate CV and contacts. We did the phone HR interview.
        // 我们收到包含候选人简历和联系方式的邮件，并已完成 HR 电话面试。
        // We now go through the 3 parallel reviews then send that result into the conditional flow to invite or reject.
        // 现在进行 3 个并行评审，然后把结果交给条件流程决定邀请或拒绝。

        // 1. Create all necessary agents for hiring team workflow
        // 1. 为招聘团队工作流创建所有必要智能体
        HrCvReviewer hrCvReviewer = AgenticServices
                .agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();

        ManagerCvReviewer managerCvReviewer = AgenticServices
                .agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamMemberCvReviewer = AgenticServices
                .agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        EmailAssistant emailAssistant = AgenticServices
                .agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        InterviewOrganizer interviewOrganizer = AgenticServices
                .agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        // 2. Create parallel review workflow
        // 2. 创建并行评审工作流
        UntypedAgent parallelReviewWorkflow = AgenticServices
                .parallelBuilder()
                .subAgents(hrCvReviewer, managerCvReviewer, teamMemberCvReviewer)
                .executor(Executors.newFixedThreadPool(3))
                .outputKey("combinedCvReview")
                .output(agenticScope -> {
                    CvReview hrReview = (CvReview) agenticScope.readState("hrReview");
                    CvReview managerReview = (CvReview) agenticScope.readState("managerReview");
                    CvReview teamMemberReview = (CvReview) agenticScope.readState("teamMemberReview");
                    String feedback = String.join("\n",
                            "HR Review: " + hrReview.feedback,
                            "Manager Review: " + managerReview.feedback,
                            "Team Member Review: " + teamMemberReview.feedback
                    );
                    double avgScore = (hrReview.score + managerReview.score + teamMemberReview.score) / 3.0;
                    System.out.println("Final averaged CV Review Score: " + avgScore + "\n");
                    return new CvReview(avgScore, feedback);
                })
                .build();

        // 3. Create conditional workflow for final decision
        // 3. 创建用于最终决策的条件工作流
        UntypedAgent decisionWorkflow = AgenticServices
                .conditionalBuilder()
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("combinedCvReview")).score >= 0.8, interviewOrganizer)
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("combinedCvReview")).score < 0.8, emailAssistant)
                .build();

        // 4. Create complete hiring team workflow: Parallel Review → Decision
        // 4. 创建完整的招聘团队工作流：并行评审 → 决策
        HiringTeamWorkflow hiringTeamWorkflow = AgenticServices
                .sequenceBuilder(HiringTeamWorkflow.class)
                .subAgents(parallelReviewWorkflow, decisionWorkflow)
                .build();

        // 5. Load input data
        // 5. 加载输入数据
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // Put all data in a Map for easy access
        // 将所有数据放入 Map，便于访问
        Map<String, Object> inputData = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "hrRequirements", hrRequirements,
                "phoneInterviewNotes", phoneInterviewNotes,
                "jobDescription", jobDescription
        );

        // 6. Execute the hiring team workflow
        // 6. 执行招聘团队工作流
        hiringTeamWorkflow.processApplication(candidateCv, jobDescription, hrRequirements, phoneInterviewNotes, candidateContact);

        System.out.println("=== HIRING TEAM WORKFLOW COMPLETED ===");
        System.out.println("Parallel reviews completed and decision made");

        // Note: as workflows become more complex, make sure that names of input, intermediate and output parameters
        // 注意：随着工作流复杂度提升，请确保输入、中间与输出参数名称唯一
        // are unique to avoid inadvertent overwriting of data in the shared AgenticScope
        // 以避免在共享的 AgenticScope 中意外覆盖数据
    }
}
