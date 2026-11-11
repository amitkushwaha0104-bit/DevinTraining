package com.capstone.fraud.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Deterministic, offline embedding model for the synthetic policy
 * corpus. Produces stable bag-of-words vectors (token hashing + sign
 * trick, L2-normalised), so retrieval results are fully reproducible
 * with no external embedding provider. Swap for a real embedding model
 * via configuration in a sandboxed environment.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(vectorize(inputs.get(i)), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text != null) {
            for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
                if (token.length() < 3) {
                    continue;
                }
                vector[indexOf(token)] += signOf(token);
                // also hash bigram-style stems to soften morphological mismatch
                if (token.length() > 5) {
                    vector[indexOf(token.substring(0, 5))] += 0.5f * signOf(token.substring(0, 5));
                }
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    private static int indexOf(String token) {
        CRC32 crc = new CRC32();
        crc.update(token.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % DIMENSIONS);
    }

    private static int signOf(String token) {
        return (token.hashCode() & 1) == 0 ? 1 : -1;
    }
}
