package com.capstone.fraud.agent.springai;

import com.capstone.fraud.config.CapstoneProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository, CapstoneProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.memory().maxMessages())
                .build();
    }

    @Bean
    public ChatClient fraudChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
