package com.capstone.fraud;

import com.capstone.fraud.agent.springai.JpaChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T10: conversation memory is keyed by caseId - a fact introduced in
 * Case A can never be recalled in Case B.
 */
@SpringBootTest
@ActiveProfiles("test")
class MemoryIsolationTests {

    @Autowired
    JpaChatMemoryRepository memory;

    @Test
    void caseAMemoryNotVisibleToCaseB() {
        memory.saveAll("CASE-A", List.of(
                new UserMessage("the customer mentioned a secret codeword ZEBRA-9"),
                new AssistantMessage("noted")));

        var caseB = memory.findByConversationId("CASE-B");
        assertTrue(caseB.isEmpty());

        var caseA = memory.findByConversationId("CASE-A");
        assertEquals(2, caseA.size());
        assertTrue(caseA.getFirst().getText().contains("ZEBRA-9"));

        assertTrue(memory.findConversationIds().contains("CASE-A"));

        memory.deleteByConversationId("CASE-A");
        assertTrue(memory.findByConversationId("CASE-A").isEmpty());
    }
}
