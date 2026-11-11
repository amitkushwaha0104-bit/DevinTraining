package com.capstone.fraud.agent.springai;

import com.capstone.fraud.agent.common.FraudCaseContext;
import com.capstone.fraud.agent.common.FraudCaseProcessor;
import com.capstone.fraud.agent.common.FraudCaseRecommendation;
import com.capstone.fraud.agent.common.RecommendationValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring AI adapter. Prompts, case-scoped memory and typed output live
 * here; evidence collection and state transitions stay in application
 * code. The model interprets - it never executes actions and its output
 * is always passed through the application validator.
 */
@Component
@ConditionalOnProperty(name = "capstone.processor-mode", havingValue = "spring-ai")
public class SpringAiFraudCaseAgent implements FraudCaseProcessor {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final RecommendationValidator validator;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiFraudCaseAgent(ChatClient fraudChatClient,
                                  ChatMemory chatMemory,
                                  RecommendationValidator validator,
                                  @Value("classpath:prompts/fraud-system.txt") Resource systemPrompt,
                                  @Value("classpath:prompts/fraud-user.txt") Resource userTemplate) throws IOException {
        this.chatClient = fraudChatClient;
        this.chatMemory = chatMemory;
        this.validator = validator;
        this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.userTemplate = userTemplate.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        FraudCaseRecommendation result = chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userTemplate).params(context.promptVariables()))
                .advisors(a -> a
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, context.caseId()))
                .call()
                .entity(FraudCaseRecommendation.class);
        // validated again in the orchestrator; validating here too keeps
        // adapter contract tests exercising the same invariants
        validator.validate(result, context);
        return result;
    }
}
