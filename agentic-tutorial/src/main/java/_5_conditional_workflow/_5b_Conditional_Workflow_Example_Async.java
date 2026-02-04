package _5_conditional_workflow;

import _4_parallel_workflow.ManagerCvReviewer;
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

public class _5b_Conditional_Workflow_Example_Async {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 150);
        // 控制从模型调用中看到的日志量
    }

    /**
     * This example demonstrates multiple fulfilled conditions and async agents that will
     * 本示例演示多个条件同时满足，以及使用异步智能体
     * allow consecutive agents to be called in parallel for faster execution.
     * 以便后续智能体并行调用、加快执行速度。
     * In this example:
     * 本例中：
     * - condition 1: if the HrReview is good, the CV is passed to the manager for review,
     * - 条件 1：如果 HR 评审通过，简历将转给经理评审
     * - condition 2: if the HrReview indicates missing information, the candidate is contacted for more info.
     * - 条件 2：如果 HR 评审提示信息缺失，将联系候选人补充信息
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. Create all async agents
        // 1. 创建所有异步智能体
        ManagerCvReviewer managerCvReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .async(true) // async agent
                // 异步智能体
                .outputKey("managerReview")
                .build();
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .async(true)
                .tools(new OrganizingTools())
                .outputKey("sentEmailId")
                .build();
        InfoRequester infoRequester = AgenticServices.agentBuilder(InfoRequester.class)
                .chatModel(CHAT_MODEL)
                .async(true)
                .tools(new OrganizingTools())
                .outputKey("sentEmailId")
                .build();

        // 2. Build async conditional workflow
        // 2. 构建异步条件工作流
        UntypedAgent candidateResponder = AgenticServices
                .conditionalBuilder()
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.score >= 0.8; // if HR passes, send to manager for review
                    // 若 HR 通过，转给经理评审
                }, managerCvReviewer)
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.score < 0.8; // if HR does not pass, send rejection email
                    // 若 HR 未通过，发送拒绝邮件
                }, emailAssistant)
                .subAgents(scope -> {
                    CvReview hrReview = (CvReview) scope.readState("cvReview");
                    return hrReview.feedback.toLowerCase().contains("missing information:");
                }, infoRequester) // if needed, request more info from candidate
                // 如有需要，向候选人索取更多信息
                .output(agenticScope ->
                        (agenticScope.readState("managerReview", new CvReview(0, "no manager review needed"))).toString() +
                                "\n" + agenticScope.readState("sentEmailId", 0)
                ) // final output is the manager review (if any)
                // 最终输出为经理评审（如有）
                .build();

        // 3. Input arguments
        // 3. 输入参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        CvReview hrReview = new CvReview(
                0.85,
                """
                        Solid candidate, salary expectations in scope and able to start within desired timeframe.
                        Missing information: details about work authorization status in Belgium.
                        """
        );

        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "jobDescription", jobDescription,
                "cvReview", hrReview
        );


        // 4. Run the conditional async workflow
        // 4. 运行异步条件工作流
        candidateResponder.invoke(arguments);

        System.out.println("=== Finished execution of async conditional workflow ===");
    }
}
