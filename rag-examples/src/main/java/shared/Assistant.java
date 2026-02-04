package shared;

/**
 * This is an "AI Service". It is a Java service with AI capabilities/features.
 * 这是一个“AI Service”。它是具备 AI 能力/功能的 Java 服务。
 * It can be integrated into your code like any other service, acting as a bean, and can be mocked for testing.
 * 它可以像其他服务一样集成进你的代码中，作为 bean 使用，也可用于测试时的 mock。
 * The goal is to seamlessly integrate AI functionality into your (existing) codebase with minimal friction.
 * 目标是在尽量少的摩擦下，将 AI 功能无缝集成到你（现有）的代码库中。
 * It's conceptually similar to Spring Data JPA or Retrofit.
 * 从概念上讲，它类似于 Spring Data JPA 或 Retrofit。
 * You define an interface and optionally customize it with annotations.
 * 你定义一个接口，并可选地用注解进行定制。
 * LangChain4j then provides an implementation for this interface using proxy and reflection.
 * LangChain4j 随后通过代理与反射为该接口提供实现。
 * This approach abstracts away all the complexity and boilerplate.
 * 这种方式抽象掉了所有复杂性和样板代码。
 * So you won't need to juggle the model, messages, memory, RAG components, tools, output parsers, etc.
 * 因此你不必在模型、消息、记忆、RAG 组件、工具、输出解析器等之间来回折腾。
 * However, don't worry. It's quite flexible and configurable, so you'll be able to tailor it
 * 不过别担心，它非常灵活且可配置，你可以根据自己的用例进行定制。
 * to your specific use case.
 * 以满足你的具体需求。
 * <br>
 * More info here: https://docs.langchain4j.dev/tutorials/ai-services
 * 更多信息： https://docs.langchain4j.dev/tutorials/ai-services
 */
public interface Assistant {

    String answer(String query);
}
