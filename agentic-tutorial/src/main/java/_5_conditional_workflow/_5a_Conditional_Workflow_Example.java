package _5_conditional_workflow;

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

public class _5a_Conditional_Workflow_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 200);  // control how much you see from the model calls
        // 控制从模型调用中看到的日志量
    }

    /**
     * This example demonstrates the conditional agent workflow.
     * 本示例演示条件式智能体工作流。
     * Based on a score and a candidate profile, we will either
     * 基于分数和候选人档案，我们将：
     * - invoke an agent that prepares everything for an on-site interview with the candidate
     * - 调用智能体准备候选人的现场面试事宜
     * - invoke an agent that sends a kind email that we will not move forward*
     * - 调用智能体发送一封婉拒邮件*
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. Define the two sub-agents in this package:
        // 2. 定义本包内的两个子智能体：
        //      - EmailAssistant.java
        //      - EmailAssistant.java
        //      - InterviewOrganizer.java
        //      - InterviewOrganizer.java

        // 3. Create all agents using AgenticServices
        // 3. 使用 AgenticServices 创建所有智能体
        EmailAssistant emailAssistant = AgenticServices.agentBuilder(EmailAssistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools()) // the agent can use all tools defined there
                // 智能体可以使用其中定义的所有工具
                .build();
        InterviewOrganizer interviewOrganizer = AgenticServices.agentBuilder(InterviewOrganizer.class)
                .chatModel(CHAT_MODEL)
                .tools(new OrganizingTools())
                .contentRetriever(RagProvider.loadHouseRulesRetriever()) // this is how we can add RAG to an agent
                // 这就是为智能体添加 RAG 的方式
                .build();

        // 4. Build the conditional workflow
        // 4. 构建条件工作流
        UntypedAgent candidateResponder = AgenticServices // use UntypedAgent unless you define the resulting composed agent, see _2_Sequential_Agent_Example
                // 除非你定义了组合后的智能体，否则使用 UntypedAgent（见 _2_Sequential_Agent_Example）
                .conditionalBuilder()
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("cvReview")).score >= 0.8, interviewOrganizer)
                .subAgents(agenticScope -> ((CvReview) agenticScope.readState("cvReview")).score < 0.8, emailAssistant)
                .build();
        // Good to know: when multiple conditions are defined, they are all executed in sequence.
        // 小提示：当定义多个条件时，会按顺序依次执行。
        // If you want parallel execution here, use async agents, as demonstrated in _5b_Conditional_Workflow_Example_Async
        // 如果想并行执行，请使用异步智能体，参见 _5b_Conditional_Workflow_Example_Async

        // 5. Load the arguments from text files in resources/documents/
        // 5. 从 resources/documents/ 中加载参数
        String candidateCv = StringLoader.loadFromResource("/documents/tailored_cv.txt");
        String candidateContact = StringLoader.loadFromResource("/documents/candidate_contact.txt");
        String jobDescription = StringLoader.loadFromResource("/documents/job_description_backend.txt");
        CvReview cvReviewFail = new CvReview(0.6, "The CV is good but lacks some technical details relevant for the backend position.");
        CvReview cvReviewPass = new CvReview(0.9, "The CV is excellent and matches all requirements for the backend position.");

        // 5. Because we use an untyped agent, we need to pass a map of all input arguments
        // 5. 因为使用了无类型智能体，需要传入所有输入参数的映射
        Map<String, Object> arguments = Map.of(
                "candidateCv", candidateCv,
                "candidateContact", candidateContact,
                "jobDescription", jobDescription,
                "cvReview", cvReviewPass // change to cvReviewFail to see the other branch
                // 改为 cvReviewFail 可看到另一分支
        );

        // 5. Call the conditional agent to respond to the candidate in line with the review
        // 5. 调用条件智能体，根据评审结果回复候选人
        candidateResponder.invoke(arguments);
        // in this example, we didn't make meaningful changes to the AgenticScope
        // 在本例中，我们没有对 AgenticScope 做有意义的更改
        // and we don't have a meaningful output to print, since the tools executed the final action.
        // 且没有可打印的有意义输出，因为工具执行了最终动作。
        // we print to the console which actions were taken by the tools (emails sent, application status updated)
        // 我们在控制台打印工具采取的动作（发送邮件、更新申请状态）

        // when you observe the logs in debug mode, the tool call result 'success' is still sent to the model
        // 当你在 debug 模式观察日志时，工具调用结果 'success' 仍会发送给模型
        // and the model still answers something like "The email has been sent to John Doe informing him ..."
        // 模型仍会回复类似“邮件已发送给 John Doe…”的内容

        // For info: if tools are your last actions and you don't want to call the model back afterwards,
        // 提示：如果工具是最后动作且不想再回调模型，
        // you will typically add @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)`
        // 通常会添加 @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)`
        // https://docs.langchain4j.dev/tutorials/tools#returning-immediately-the-result-of-a-tool-execution-request
        // 参考链接
        // !!! BUT in agentic workflows IMMEDIATE RETURN BEHAVIOR for tools is NOT RECOMMENDED,
        // !!! 但在智能体工作流中，不推荐工具使用“立即返回”行为，
        // since immediate return behavior will store the tool result in the AgenticScope and things can go wrong
        // 因为立即返回会把工具结果存入 AgenticScope，可能导致问题

        // For info: this was an example of routing behavior with a code check on the conditions.
        // 提示：这是一个通过代码检查条件实现的路由行为示例。
        // Routing behavior can also be obtained by letting an LLM determine the best tool(s)/agent(s)
        // 路由行为也可由 LLM 决定最合适的工具/智能体
        // to continue with, either by using
        // 继续执行，可通过以下方式：
        // - Supervisor agent: will operate on agents, see _7_supervisor_orchestration
        // - Supervisor 智能体：用于调度智能体，见 _7_supervisor_orchestration
        // - AiServices as tools, like this
        // - 将 AiServices 作为工具，例如：
        // RouterService routerService = AiServices.builder(RouterAgent.class)
        //        .chatModel(model)
        //        .tools(medicalExpert, legalExpert, technicalExpert)
        //        .build();
        //
        // The best option depends on your use case:
        // 最佳方案取决于你的使用场景：
        //
        // - With conditional agents, you hardcode call criteria
        // - 使用条件智能体时，你硬编码调用标准
        // - Vs. with AiServices or Supervisor, the LLM decide which expert(s) to call
        // - 使用 AiServices 或 Supervisor 时，LLM 决定调用哪些专家
        //
        // - With agentic solutions (conditional, supervisor) all intermediary states and the call chain are stored in AgenticScope
        // - 使用智能体方案（条件、监督）时，所有中间状态和调用链都存储在 AgenticScope 中
        // - Vs. with AiServices it is much harder to track the call chain or intermediary states
        // - 而使用 AiServices 时更难跟踪调用链或中间状态

    }
}
