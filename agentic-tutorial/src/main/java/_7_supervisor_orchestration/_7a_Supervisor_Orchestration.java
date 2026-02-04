package _7_supervisor_orchestration;

import _4_parallel_workflow.HrCvReviewer;
import _4_parallel_workflow.ManagerCvReviewer;
import _4_parallel_workflow.TeamMemberCvReviewer;
import _5_conditional_workflow.EmailAssistant;
import _5_conditional_workflow.InterviewOrganizer;
import _5_conditional_workflow.OrganizingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;

/**
 * Up until now we built deterministic workflows:
 * 到目前为止我们构建的是确定性工作流：
 * - sequential, parallel, conditional, loop, and compositions of those.
 * - 顺序、并行、条件、循环及其组合。
 * You can also build a Supervisor agentic system, in which an agent will
 * 你也可以构建 Supervisor 智能体系统，在其中一个智能体会
 * decide dynamically which of his sub-agents to call in which order.
 * 动态决定调用哪些子智能体及其顺序。
 * In this example, the Supervisor coordinates the hiring workflow:
 * 在本例中，Supervisor 负责协调招聘流程：
 * He is supposed to runs HR/Manager/Team reviews and either schedule
 * 他将运行 HR/经理/团队评审并决定
 * an interview or send a rejection email.
 * 安排面试或发送拒绝邮件。
 * Just like part 2 of the Composed Workflow example, but now 'self-organised'
 * 类似组合工作流示例的第二部分，但现在是“自组织”的。
 * Note that supervisor super-agents can be used in composed workflows just like the other super-agent types.
 * 注意：Supervisor 超级智能体也可像其他超级智能体类型一样用于组合工作流。
 * IMPORTANT: this example takes about 50s to run with GPT-4o-mini. You can see what is happening continuously in the PRETTY logs.
 * 重要：使用 GPT-4o-mini 运行本例约需 50 秒。你可以在 PRETTY 日志中持续观察进度。
 * There are ways to speed up execution, see comments at the end of this file.
 * 有方法可以加速执行，见文件末尾的注释。
 */
public class _7a_Supervisor_Orchestration {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 1. Define all sub-agents
        // 1. 定义所有子智能体
        HrCvReviewer hrReviewer = AgenticServices.agentBuilder(HrCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("hrReview")
                .build();
        // importantly, if we use the same method names for multiple agents
        // 重要：如果多个智能体使用相同的方法名
        // (in this case: 'reviewCv' for all reviewers) we best name our agents, like this:
        //（本例中所有评审都是 'reviewCv'），最好给智能体命名，例如：
        // @Agent(name = "managerReviewer", description = "Reviews a CV based on a job description, gives feedback and a score")
        // @Agent(name = "managerReviewer", description = "根据职位描述评审简历，给出反馈和分数")

        ManagerCvReviewer managerReviewer = AgenticServices.agentBuilder(ManagerCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("managerReview")
                .build();

        TeamMemberCvReviewer teamReviewer = AgenticServices.agentBuilder(TeamMemberCvReviewer.class)
                .chatModel(CHAT_MODEL)
                .outputKey("teamMemberReview")
                .build();

        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .build();

        // 2. Build the Supervisor agent
        // 2. 构建 Supervisor 智能体
        SupervisorAgent hiringSupervisor = AgenticServices.supervisorBuilder()
                .chatModel(CHAT_MODEL)
                .subAgents(hrReviewer, managerReviewer, teamReviewer, interviewOrganizer, emailAssistant)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY) // we want a summary of what happened, rather than retrieving a response
                // 我们希望得到过程总结，而不是直接返回响应
                .supervisorContext("Always use the full panel of available reviewers. Always answer in English. When invoking agent, use pure JSON (no backticks, and new lines as backslash+n).") // optional context for the supervisor on how to behave
                // 给 supervisor 的可选行为上下文
                .build();
        // Important to know: the supervisor will invoke 1 agent at a time and then review his plan to choose which agent to invoke next
        // 重要：supervisor 一次只调用一个智能体，然后评估计划再选择下一位
        // It is not possible to have agents executed in parallel by the supervisor
        // supervisor 无法并行执行多个智能体
        // If agents are marked as async, the supervisor will override that (no async execution) and issue a warning
        // 如果智能体标记为 async，supervisor 会覆盖该设置（不异步）并给出警告

        // 3. Load candidate CV & job description
        // 3. 加载候选人简历和职位描述
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String hrRequirements = StringLoader.loadFromResource("/documents/hr_requirements.txt");
        String phoneInterviewNotes = StringLoader.loadFromResource("/documents/phone_interview_notes.txt");

        // start a timer
        // 开始计时
        long start = System.nanoTime();
        // 4. Invoke Supervisor with a natural request
        // 4. 用自然语言请求调用 Supervisor
        String result = (String) hiringSupervisor.invoke(
                "Evaluate the following candidate:\n" +
                        "Candidate CV:\n" + candidateCv + "\n\n" +
                        "Candidate Contacts:\n" + candidateContact + "\n\n" +
                        "Job Description:\n" + jobDescription + "\n\n" +
                        "HR Requirements:\n" + hrRequirements + "\n\n" +
                        "Phone Interview Notes:\n" + phoneInterviewNotes
        );
        long end = System.nanoTime();
        double elapsedSeconds = (end - start) / 1_000_000_000.0;
        // in the logs you'll notice a final invocation of agent 'done', this is how the supervisor finishes the invocation series
        // 在日志中会看到最后调用了 'done' 智能体，这是 supervisor 结束调用序列的方式

        System.out.println("=== SUPERVISOR RUN COMPLETED in " + elapsedSeconds + " seconds ===");
        System.out.println(result);
    }

    // ADVANCED USE CASES:
    // 高级用例：
    // See _7b_Supervisor_Orchestration_Advanced.java for
    // 参见 _7b_Supervisor_Orchestration_Advanced.java
    // - typed supervisor,
    // - 带类型的 supervisor，
    // - context engineering,
    // - 上下文工程，
    // - output strategies,
    // - 输出策略，
    // - call chain observation,
    // - 调用链观察，

    // ON LATENCY:
    // 关于延迟：
    // The whole run of this flow typically takes over 60s.
    // 该流程完整运行通常超过 60 秒。
    // A solution for this is to use a fast inference provider like CEREBRAS,
    // 解决方案之一是使用更快的推理服务商，如 CEREBRAS，
    // which will run the whole flow in 10s but makes more mistakes.
    // 可将全流程运行时间降到 10 秒，但错误更多。
    // To try this example with CEREBRAS, get a key (click get started with free API key)
    // 如要用 CEREBRAS 试运行，请获取 API Key（点击获取免费 API Key）
    // https://inference-docs.cerebras.ai/quickstart
    // 参考链接
    // and save in env variables as "CEREBRAS_API_KEY"
    // 并保存到环境变量 "CEREBRAS_API_KEY"
    // Then change line 38 to:
    // 然后把第 38 行改为：
    // private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel("CEREBRAS");
    // 示例代码

}
