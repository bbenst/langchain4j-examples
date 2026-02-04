package _7_supervisor_orchestration;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Advanced Supervisor Example with explicit AgenticScope to inspect evolving context
 * 高级 Supervisor 示例，显式使用 AgenticScope 来检查上下文演进
 */
public class _7b_Supervisor_Orchestration_Advanced {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);
        // 控制从模型调用中看到的日志量
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    /**
     * In this example we build a similar supervisor as in _7a_Supervisor_Orchestration,
     * 在本例中，我们构建与 _7a_Supervisor_Orchestration 类似的 supervisor，
     * but we explore a number of extra features of the Supervisor:
     * 但会探索 Supervisor 的多个额外特性：
     * - typed supervisor,
     * - 带类型的 supervisor，
     * - context engineering,
     * - 上下文工程，
     * - output strategies,
     * - 输出策略，
     * - call chain observation,
     * - 调用链观察，
     * - context evolution inspection
     * - 上下文演进检查
     */
    public static void main(String[] args) throws IOException {

        // 1. Define subagents
        // 1. 定义子智能体
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .build();
        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .outputKey("response")
                .build();
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .outputKey("response")
                .build();

        // 2. Build supervisor
        // 2. 构建 supervisor

        HiringSupervisor hiringSupervisor = AgenticServices
                .supervisorBuilder(HiringSupervisor.class)
                .chatModel(CHAT_MODEL)
                .subAgents(hrReviewer, managerReviewer, teamReviewer, interviewOrganizer, emailAssistant)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                // depending on what your supervisor needs to know about what the sub-agents have been doing,
                // 取决于 supervisor 需要了解子智能体做了什么，
                // you can choose contextGenerationStrategy CHAT_MEMORY, SUMMARIZATION, or CHAT_MEMORY_AND_SUMMARIZATION
                // 可选择 CHAT_MEMORY、SUMMARIZATION 或 CHAT_MEMORY_AND_SUMMARIZATION
                .responseStrategy(SupervisorResponseStrategy.SCORED) // this strategy uses a scorer model to decide weather the LAST response or the SUMMARY solves the user request best
                // 该策略使用评分模型来决定“最后响应”还是“总结”更能满足用户请求
                // an output function here would override the response strategy
                // 若在此定义输出函数，会覆盖响应策略
                .supervisorContext("Policy: Always check HR first, escalate if needed, reject low-fit.")
                .build();

        // 3. Load input data
        // 3. 加载输入数据
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        String request = "Evaluate this candidate and either schedule an interview or send a rejection email.\n"
                + "Candidate CV:\n" + candidateCv + "\n"
                + "Candidate Contacts:\n" + candidateContact + "\n"
                + "Job Description:\n" + jobDescription + "\n"
                + "HR Requirements:\n" + hrRequirements + "\n"
                + "Phone Interview Notes:\n" + phoneInterviewNotes;

        // 4. Invoke supervisor
        // 4. 调用 supervisor
        long start = System.nanoTime();
        ResultWithAgenticScope<String> decision = hiringSupervisor.invoke(request, "Manager technical review is most important.");
        long end = System.nanoTime();

        System.out.println("=== Hiring Supervisor finished in " + ((end - start) / 1_000_000_000.0) + "s ===");
        System.out.println(decision.result());

        // Print collected contexts
        // 打印收集到的上下文
        System.out.println("\n=== Context as Conversation ===");
        System.out.println(decision.agenticScope().contextAsConversation()); // will work in next release
        // 将在下一版本生效

    }
}
