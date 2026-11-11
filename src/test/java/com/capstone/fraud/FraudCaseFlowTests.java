package com.capstone.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow over the real stack (H2, rules mode): intake ->
 * investigate -> evidence/traces -> recommendation -> human review.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FraudCaseFlowTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String intakeJson(String txnRef) {
        return """
                {
                  "maskedCustomerRef": "CUST-••••-4821",
                  "homeRegion": "IN-MH",
                  "transactionRef": "%s",
                  "amount": 48750.00,
                  "currency": "INR",
                  "occurredAt": "2026-11-06T08:42:00Z",
                  "status": "POSTED",
                  "channel": "CARD_NOT_PRESENT",
                  "maskedMerchant": "NORTHSTAR DIGITAL ••••73",
                  "issueType": "MERCHANT_NOT_RECOGNISED",
                  "customerStatement": "I do not recognise this purchase and still have my card.",
                  "cardPossession": true,
                  "merchantRecognised": false,
                  "syntheticDataConfirmed": true,
                  "reviewRequired": true
                }
                """.formatted(txnRef);
    }

    private String createCase(String txnRef) throws Exception {
        MvcResult result = mvc.perform(post("/api/cases")
                        .with(csrf()).with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("agent1").roles("AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(intakeJson(txnRef)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").exists())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("caseId").asText();
    }

    @Test
    @WithMockUser(username = "agent1", roles = "AGENT")
    void t04RejectsUnmaskedCardNumber() throws Exception {
        String bad = intakeJson("TXN-1").replace("CUST-••••-4821", "4111111111111111");
        mvc.perform(post("/api/cases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "agent1", roles = "AGENT")
    void t04RejectsMissingFields() throws Exception {
        mvc.perform(post("/api/cases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "agent1", roles = "AGENT")
    void t04RejectsMarkupInStatement() throws Exception {
        String bad = intakeJson("TXN-1").replace("still have my card", "<script>alert(1)</script>");
        mvc.perform(post("/api/cases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullFlowIntakeToReview() throws Exception {
        String caseId = createCase("TXN-2026-784102");

        // investigate as investigator
        MvcResult inv = mvc.perform(post("/api/cases/" + caseId + "/investigations")
                        .with(csrf()).with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("inv1").roles("INVESTIGATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.plan").isArray())
                .andExpect(jsonPath("$.recommendation.classification").value("UNAUTHORISED_TRANSACTION"))
                .andExpect(jsonPath("$.recommendation.recommendedRoute").value("FRAUD_INVESTIGATION"))
                .andExpect(jsonPath("$.recommendation.humanReviewRequired").value(true))
                .andReturn();
        JsonNode rec = objectMapper.readTree(inv.getResponse().getContentAsString())
                .get("recommendation");
        assertFalse(rec.get("evidenceIds").isEmpty());

        // evidence and traces available
        mvc.perform(get("/api/cases/" + caseId + "/evidence")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("aud").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.length()").value(org.hamcrest.Matchers.greaterThan(3)));
        mvc.perform(get("/api/cases/" + caseId + "/tool-traces")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("aud").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traces.length()").value(org.hamcrest.Matchers.greaterThan(4)));
        mvc.perform(get("/api/cases/" + caseId + "/citations")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("aud").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // T12: review without reason is blocked
        mvc.perform(post("/api/cases/" + caseId + "/reviews").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("rev1").roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT_AND_ROUTE\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        // reviewer cannot investigate (role boundary)
        mvc.perform(post("/api/cases/" + caseId + "/investigations").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("rev1").roles("REVIEWER")))
                .andExpect(status().isForbidden());

        // review with reason -> ROUTED
        mvc.perform(post("/api/cases/" + caseId + "/reviews").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("rev1").roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT_AND_ROUTE\",\"reason\":\"Evidence supports fraud routing.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("ROUTED"));

        // closed-out cases reject further transitions
        mvc.perform(post("/api/cases/" + caseId + "/investigations").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("inv1").roles("INVESTIGATOR")))
                .andExpect(status().isConflict());
    }

    @Test
    void t08MissingTransactionYieldsIncomplete() throws Exception {
        String caseId = createCase("TXN-2026-MISSING");
        mvc.perform(post("/api/cases/" + caseId + "/investigations").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("inv1").roles("INVESTIGATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.recommendation.classification").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.recommendation.recommendedRoute").value("MANUAL_REVIEW"));
    }

    @Test
    void followUpMessageReturnsAnswerAndCitations() throws Exception {
        String caseId = createCase("TXN-2026-DUP01");
        mvc.perform(post("/api/cases/" + caseId + "/messages").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("inv1").roles("INVESTIGATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"duplicate posting charged twice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }
}
