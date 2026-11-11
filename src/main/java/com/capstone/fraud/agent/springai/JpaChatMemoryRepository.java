package com.capstone.fraud.agent.springai;

import com.capstone.fraud.persistence.CaseMessageEntity;
import com.capstone.fraud.persistence.CaseMessageRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA-backed chat memory keyed by caseId. Provides restart-safe,
 * case-scoped conversation memory - facts from one case can never be
 * recalled in another because the conversation id IS the case id.
 */
@Component
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private final CaseMessageRepository repository;

    public JpaChatMemoryRepository(CaseMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<String> findConversationIds() {
        return repository.findDistinctCaseIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return repository.findByCaseIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        repository.deleteByCaseId(conversationId);
        Instant now = Instant.now();
        List<CaseMessageEntity> entities = messages.stream().map(m -> {
            CaseMessageEntity e = new CaseMessageEntity();
            e.setId(UUID.randomUUID());
            e.setCaseId(conversationId);
            e.setRole(m.getMessageType().name());
            e.setContent(truncate(m.getText()));
            e.setCreatedAt(now);
            return e;
        }).toList();
        repository.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteByConversationId(String conversationId) {
        repository.deleteByCaseId(conversationId);
    }

    private Message toMessage(CaseMessageEntity e) {
        return switch (e.getRole()) {
            case "USER" -> new UserMessage(e.getContent());
            case "ASSISTANT" -> new AssistantMessage(e.getContent());
            case "SYSTEM" -> new SystemMessage(e.getContent());
            default -> new UserMessage(e.getContent());
        };
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 7900 ? text.substring(0, 7900) : text;
    }
}
