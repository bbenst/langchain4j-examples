package dev.langchain4j.example.aiservice;

import dev.langchain4j.example.lowlevel.ChatModelController;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

@Configuration
public class AssistantConfiguration {

    /**
     * This chat memory will be used by {@link Assistant} and {@link StreamingAssistant}
     * 该聊天内存将被 {@link Assistant} 和 {@link StreamingAssistant} 使用
     */
    @Bean
    @Scope(SCOPE_PROTOTYPE)
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }

    /**
     * This listener will be injected into every {@link ChatModel} and {@link StreamingChatModel}
     * 该监听器会被注入到每个 {@link ChatModel} 和 {@link StreamingChatModel}
     * bean   found in the application context.
     * 在应用上下文中找到的 bean。
     * It will listen for {@link ChatModel} in the {@link ChatModelController} as well as
     * 它会监听 {@link ChatModelController} 中的 {@link ChatModel}，以及
     * {@link Assistant} and {@link StreamingAssistant}.
     * {@link Assistant} 和 {@link StreamingAssistant}。
     */
    @Bean
    ChatModelListener chatModelListener() {
        return new MyChatModelListener();
    }
}
