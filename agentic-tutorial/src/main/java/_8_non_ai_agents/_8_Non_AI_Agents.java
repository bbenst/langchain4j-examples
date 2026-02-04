package _8_non_ai_agents;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import domain.CvReview;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;

public class _8_Non_AI_Agents {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 100);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * Here we how to use non-AI agents (plain Java operators) within agentic workflows.
     * 这里演示如何在智能体工作流中使用非 AI 智能体（普通 Java 操作）。
     * Non-AI agents are simply methods, but can be used as any other type of agent.
     * 非 AI 智能体只是方法，但可像其他智能体类型一样使用。
     * They are perfect for deterministic operations like calculations, data transformations,
     * 它们非常适合做确定性操作，如计算、数据转换，
     * and aggregations, where you rather have no LLM involvement.
     * 和聚合等，不希望 LLM 介入的场景。
     * The more steps you can outsource to non-AI agents, the faster, correcter and cheaper your workflows will be.
     * 可外包给非 AI 智能体的步骤越多，工作流就越快、越准确、越省成本。
     * Non-AI agents are preferred over tools for workflows where you want to enforce determinism for certain steps.
     * 当你希望某些步骤保持确定性时，更推荐使用非 AI 智能体而不是工具。
     * In this case we want the aggregated score of the reviewers to be calculated deterministically, not by an LLM.
     * 本例中我们希望评审汇总分数以确定性方式计算，而不是由 LLM 计算。
     * We also update the application status in the database deterministically based on the aggregated score.
     * 我们还会基于汇总分数以确定性方式更新数据库中的申请状态。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. Define the ScoreAggregator non-AI agents in this pacckage
        // 1. 在本包中定义 ScoreAggregator 非 AI 智能体

        // 2. Build the AI sub-agents for the parallel review step
        // 2. 为并行评审步骤构建 AI 子智能体
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();

        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        // 3. Build the composed parallel agent
        // 3. 构建组合并行智能体
        var executor = Executors.newFixedThreadPool(3);  // keep a reference for later closing
        // 保留引用以便稍后关闭

        UntypedAgent parallelReviewWorkflow = AgenticServices
                .parallelBuilder()
                .subAgents(hrReviewer, managerReviewer, teamReviewer)
                .executor(executor)
                .build();

        // 4. Build the full workflow incl. non-AI agent
        // 4. 构建包含非 AI 智能体的完整工作流
        UntypedAgent collectFeedback = AgenticServices
                .sequenceBuilder()
                .subAgents(
                        parallelReviewWorkflow,
                        new ScoreAggregator(), // no AgenticServices builder needed for non-AI agents. outputKey 'combinedCvReview' is defined in the class
                        // 非 AI 智能体无需 AgenticServices 构建器，outputKey 'combinedCvReview' 在类中定义
                        new StatusUpdate(), // takes 'combinedCvReview' as input, no output needed
                        // 以 'combinedCvReview' 作为输入，不需要输出
                        AgenticServices.agentAction(agenticScope -> { // another way to add non-AI agents that can operate on the AgenticScope
                            // 另一种添加可操作 AgenticScope 的非 AI 智能体方式
                            CvReview review = (CvReview) agenticScope.readState("combinedCvReview");
                            agenticScope.writeState("scoreAsPercentage", review.score * 100); // when agents from different systems communicate, output conversion is often needed
                            // 不同系统的智能体通信时，常需要做输出转换
                        })
                )
                .outputKey("scoreAsPercentage") // outputKey defined on the non-AI agent annotation in ScoreAggregator.java
                // outputKey 在 ScoreAggregator.java 的非 AI 智能体注解中定义
                .build();

        // 5. Load input data
        // 5. 加载输入数据
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");

        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "hrRequirements", hrRequirements,
                "phoneInterviewNotes", phoneInterviewNotes,
                "jobDescription", jobDescription
        );

        // 6. Invoke the workflow
        // 6. 调用工作流
        double scoreAsPercentage = (double) collectFeedback.invoke(arguments);
        executor.shutdown();

        System.out.println("=== SCORE AS PERCENTAGE ===");
        System.out.println(scoreAsPercentage);
        // as we can see in the logs, the application status has also been updated accordingly
        // 从日志可见，申请状态也已相应更新

    }
}
