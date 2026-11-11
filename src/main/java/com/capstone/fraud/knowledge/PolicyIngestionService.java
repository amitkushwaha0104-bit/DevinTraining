package com.capstone.fraud.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads synthetic policy documents from classpath:policies, validates
 * metadata, chunks by section headings, embeds and indexes them in an
 * in-memory vector store. Withdrawn or unparseable documents are
 * rejected so stale policy can never be cited.
 */
@Service
public class PolicyIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);

    private final ResourcePatternResolver resolver;
    private final VectorStore vectorStore;
    private final Map<String, PolicyPassage> chunkIndex = new ConcurrentHashMap<>();
    private final List<PolicyDocumentMetadata> activeDocuments = new ArrayList<>();

    public PolicyIngestionService(ResourcePatternResolver resolver) {
        this.resolver = resolver;
        this.vectorStore = SimpleVectorStore.builder(new HashingEmbeddingModel()).build();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = resolver.getResources("classpath:policies/*.md");
        for (Resource resource : resources) {
            PolicyDocument doc = parse(resource);
            if (doc == null) {
                log.warn("policy file {} rejected: missing or invalid metadata", resource.getFilename());
                continue;
            }
            if ("WITHDRAWN".equalsIgnoreCase(doc.metadata().status())) {
                log.info("policy {} v{} skipped: withdrawn", doc.metadata().documentId(), doc.metadata().version());
                continue;
            }
            List<Document> chunks = chunk(doc);
            vectorStore.add(chunks);
            activeDocuments.add(doc.metadata());
            doc.chunks().forEach(c -> chunkIndex.put(c.metadata.chunkId(), toPassage(c, 1.0)));
            log.info("policy {} v{} ingested: {} chunks", doc.metadata().documentId(), doc.metadata().version(), chunks.size());
        }
    }

    public VectorStore vectorStore() {
        return vectorStore;
    }

    public List<PolicyDocumentMetadata> activeDocuments() {
        return List.copyOf(activeDocuments);
    }

    /** Exact passage lookup for citation drill-down, independent of similarity. */
    public PolicyPassage passageByChunkId(String chunkId) {
        return chunkIndex.get(chunkId);
    }

    private static PolicyPassage toPassage(Chunk c, double score) {
        var m = c.metadata();
        return new PolicyPassage(m.documentId(), m.title(), m.version(), m.section(), m.chunkId(), m.effectiveDate(), score, c.text());
    }

    static PolicyDocument parse(Resource resource) throws IOException {
        String text = resource.getContentAsString(StandardCharsets.UTF_8);
        String[] parts = text.split("(?m)^---\\s*$", -1);
        if (parts.length < 3 || !parts[0].isBlank()) {
            return null;
        }
        Map<String, String> header = parseHeader(parts[1]);
        String documentId = header.get("documentId");
        String title = header.get("title");
        String version = header.get("version");
        String effectiveDate = header.get("effectiveDate");
        if (documentId == null || title == null || version == null || effectiveDate == null) {
            return null;
        }
        var metadata = new PolicyDocumentMetadata(documentId, title, version, effectiveDate,
                header.getOrDefault("owner", "unknown"), header.getOrDefault("status", "ACTIVE"));

        String body = parts[2];
        List<Chunk> chunks = new ArrayList<>();
        String[] sections = body.split("(?m)^(?=##\\s)");
        int seq = 0;
        for (String section : sections) {
            String trimmed = section.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") && !trimmed.startsWith("##")) {
                continue;
            }
            String firstLine = trimmed.lines().findFirst().orElse("general");
            String sectionName = firstLine.replaceAll("^#+\\s*", "").strip();
            String chunkId = documentId.toLowerCase().replace("pol-", "") + "-" + String.format("%03d", ++seq);
            chunks.add(new Chunk(new ChunkMetadata(documentId, title, version, effectiveDate, sectionName, chunkId), trimmed));
        }
        if (chunks.isEmpty()) {
            return null;
        }
        return new PolicyDocument(metadata, chunks);
    }

    private static Map<String, String> parseHeader(String yaml) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : yaml.split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }
        return map;
    }

    private List<Document> chunk(PolicyDocument doc) {
        return doc.chunks().stream()
                .map(c -> {
                    var m = c.metadata();
                    return Document.builder()
                            .id(m.chunkId())
                            .text(c.text())
                            .metadata("documentId", m.documentId())
                            .metadata("title", m.title())
                            .metadata("version", m.version())
                            .metadata("effectiveDate", m.effectiveDate())
                            .metadata("section", m.section())
                            .metadata("chunkId", m.chunkId())
                            .build();
                })
                .toList();
    }

    public record PolicyDocumentMetadata(
            String documentId, String title, String version,
            String effectiveDate, String owner, String status) {
    }

    record ChunkMetadata(String documentId, String title, String version,
                         String effectiveDate, String section, String chunkId) {
    }

    record Chunk(ChunkMetadata metadata, String text) {
    }

    record PolicyDocument(PolicyDocumentMetadata metadata, List<Chunk> chunks) {
    }
}
