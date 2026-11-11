package com.capstone.fraud.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

/**
 * Derives synthetic evidence from a stable hash of masked references.
 * Identical input always produces identical output, which keeps tests
 * and demonstrations reproducible. A small set of named scenario refs
 * produce fixed profiles so the demo script is repeatable; every other
 * ref is hash-derived.
 */
@Component
public class DeterministicFixtures {

    /** Canonical demo case from the participant guide. */
    public static final String REF_UNAUTHORISED = "TXN-2026-784102";
    /** Demonstrates duplicate-processing routing. */
    public static final String REF_DUPLICATE = "TXN-2026-DUP01";
    /** Demonstrates a recognised-merchant dispute. */
    public static final String REF_MERCHANT = "TXN-2026-MRC01";
    /** Demonstrates missing transaction evidence. */
    public static final String REF_MISSING = "TXN-2026-MISSING";

    private final String seed;

    public DeterministicFixtures(@Value("${capstone.tools.deterministic-seed:fraud-training-v1}") String seed) {
        this.seed = seed;
    }

    public enum Scenario {UNAUTHORISED, DUPLICATE, MERCHANT_RECOGNISED, MISSING, HASHED}

    public Scenario scenarioFor(String transactionRef) {
        if (transactionRef == null) {
            return Scenario.HASHED;
        }
        if (transactionRef.contains("784102")) {
            return Scenario.UNAUTHORISED;
        }
        if (transactionRef.contains("DUP")) {
            return Scenario.DUPLICATE;
        }
        if (transactionRef.contains("MRC")) {
            return Scenario.MERCHANT_RECOGNISED;
        }
        if (transactionRef.contains("MISSING")) {
            return Scenario.MISSING;
        }
        return Scenario.HASHED;
    }

    /**
     * Stable per-input Random. Same (scope, ref) pair always yields the
     * same sequence, so hash-derived fixtures are reproducible.
     */
    public Random randomFor(String scope, String ref) {
        long value = hashToLong(seed + "|" + scope + "|" + (ref == null ? "" : ref));
        return new Random(value);
    }

    public String requestHash(String... parts) {
        String joined = String.join("|", parts);
        return HexFormat.of().formatHex(sha256(joined)).substring(0, 16);
    }

    private long hashToLong(String input) {
        byte[] digest = sha256(input);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (digest[i] & 0xffL);
        }
        return result;
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
