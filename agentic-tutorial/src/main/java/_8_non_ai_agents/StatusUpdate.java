package _8_non_ai_agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import domain.CvReview;

/**
 * Non-AI agent that aggregates multiple CV reviews into a combined review.
 * 非 AI 智能体，用于将多份简历评审聚合成综合评审。
 * This demonstrates how plain Java operators can be used as first-class agents
 * 这展示了普通 Java 运算也可作为一等公民智能体
 * in agentic workflows, making them interchangeable with AI-powered agents.
 * 融入智能体工作流，并与 AI 智能体互换使用。
 */
public class StatusUpdate {

    @Agent(description = "Update application status based on score")
    public void update(@V("combinedCvReview") CvReview aggregateCvReview) {
        double score = aggregateCvReview.score;
        System.out.println("StatusUpdate called with score: " + score);

        if (score >= 8.0) {
            // dummy database update for demo
            // 示例用的虚拟数据库更新
            System.out.println("Application status updated to: INVITED");
        } else {
            // dummy database update for demo
            // 示例用的虚拟数据库更新
            System.out.println("Application status updated to: REJECTED");
        }
    }
}

