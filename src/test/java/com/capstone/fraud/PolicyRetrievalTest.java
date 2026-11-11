package com.capstone.fraud;

import com.capstone.fraud.knowledge.PolicyIngestionService;
import com.capstone.fraud.knowledge.PolicyRetriever;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PolicyRetrievalTest {

    private PolicyIngestionService ingestion;
    private PolicyRetriever retriever;

    @BeforeAll
    void ingest() throws Exception {
        ingestion = new PolicyIngestionService(new PathMatchingResourcePatternResolver());
        ingestion.run(new DefaultApplicationArguments());
        retriever = new PolicyRetriever(ingestion, 4, 0.10);
    }

    @Test
    void relevantQueryReturnsCitationsWithMetadata() {
        var passages = retriever.retrieve("card not present unauthorised merchant not recognised fraud routing");
        assertFalse(passages.isEmpty());
        var p = passages.getFirst();
        assertFalse(p.documentId().isBlank());
        assertFalse(p.version().isBlank());
        assertFalse(p.section().isBlank());
        assertFalse(p.chunkId().isBlank());
        assertFalse(p.passage().isBlank());
        assertTrue(p.score() >= 0.10);
    }

    @Test
    void unrelatedQueryAbstains() {
        var passages = retriever.retrieve("chocolate cake recipe ingredients flour sugar");
        assertTrue(passages.isEmpty(), "expected abstention for unrelated query");
    }

    @Test
    void blankQueryAbstains() {
        assertTrue(retriever.retrieve("   ").isEmpty());
        assertTrue(retriever.retrieve(null).isEmpty());
    }

    @Test
    void withdrawnPolicyIsNotIndexed() {
        assertTrue(ingestion.activeDocuments().stream()
                .noneMatch(d -> d.documentId().equals("POL-ESC-021")));
        assertEquals(3, ingestion.activeDocuments().size());
    }

    @Test
    void duplicateQueryRetrievesPaymentOpsPolicy() {
        var passages = retriever.retrieve("duplicate posting charged twice payment fingerprint");
        assertFalse(passages.isEmpty());
        assertTrue(passages.stream().anyMatch(p -> p.documentId().equals("POL-DUP-009")));
    }
}
