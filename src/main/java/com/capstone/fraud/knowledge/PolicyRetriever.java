package com.capstone.fraud.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thresholded retrieval over the approved policy corpus. Returns exact
 * citation metadata; returns an empty list (abstention) when nothing
 * meets the configured similarity threshold - never fabricates policy.
 */
@Service
public class PolicyRetriever {

    private static final Logger log = LoggerFactory.getLogger(PolicyRetriever.class);

    private final PolicyIngestionService ingestion;
    private final int topK;
    private final double threshold;

    public PolicyRetriever(PolicyIngestionService ingestion,
                           @Value("${capstone.retrieval.top-k:4}") int topK,
                           @Value("${capstone.retrieval.similarity-threshold:0.10}") double threshold) {
        this.ingestion = ingestion;
        this.topK = topK;
        this.threshold = threshold;
    }

    public List<PolicyPassage> retrieve(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Document> docs = ingestion.vectorStore().similaritySearch(
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(threshold).build());
        List<PolicyPassage> passages = docs == null ? List.of() : docs.stream()
                .map(this::toPassage)
                .toList();
        if (passages.isEmpty()) {
            log.info("policy retrieval abstained: no passage met threshold {} for query", threshold);
        }
        return passages;
    }

    private PolicyPassage toPassage(Document doc) {
        var m = doc.getMetadata();
        double score = doc.getScore() == null ? 0.0 : doc.getScore();
        return new PolicyPassage(
                str(m, "documentId"), str(m, "title"), str(m, "version"),
                str(m, "section"), str(m, "chunkId"), str(m, "effectiveDate"),
                score, doc.getText() == null ? "" : doc.getText());
    }

    private static String str(java.util.Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    public int topK() {
        return topK;
    }

    public double threshold() {
        return threshold;
    }
}
