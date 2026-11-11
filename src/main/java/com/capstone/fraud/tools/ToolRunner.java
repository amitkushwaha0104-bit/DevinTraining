package com.capstone.fraud.tools;

import com.capstone.fraud.domain.ToolStatus;
import com.capstone.fraud.persistence.FraudCaseEntity;
import com.capstone.fraud.persistence.IdGenerator;
import com.capstone.fraud.persistence.ToolTraceEntity;
import com.capstone.fraud.persistence.ToolTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Executes allow-listed tools inside a bounded window and records a
 * redacted trace for every call. Failures become structured UNAVAILABLE
 * or TIMEOUT statuses rather than exceptions leaking to the model or UI.
 */
@Component
public class ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);

    private final ToolTraceRepository traceRepository;
    private final DeterministicFixtures fixtures;
    private final IdGenerator ids;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final long timeoutMs;

    public ToolRunner(ToolTraceRepository traceRepository,
                      DeterministicFixtures fixtures,
                      IdGenerator ids,
                      ObjectMapper objectMapper,
                      @Value("${capstone.tools.timeout-ms:2500}") long timeoutMs) {
        this.traceRepository = traceRepository;
        this.fixtures = fixtures;
        this.ids = ids;
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
    }

    public <T> ToolOutcome<T> run(FraudCaseEntity fraudCase, String toolName, Supplier<T> call, String... hashParts) {
        Instant started = Instant.now();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(call, executor);
        T result = null;
        ToolStatus status;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            status = ToolStatus.SUCCESS;
        } catch (TimeoutException e) {
            future.cancel(true);
            status = ToolStatus.TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = ToolStatus.FAILED;
        } catch (ExecutionException e) {
            log.warn("tool {} failed for case {}: {}", toolName, fraudCase.getCaseId(), e.getCause() == null ? e : e.getCause());
            status = ToolStatus.FAILED;
        }
        long duration = Duration.between(started, Instant.now()).toMillis();

        String summary = status == ToolStatus.SUCCESS ? summarise(result) : "tool " + status.name().toLowerCase();

        ToolTraceEntity trace = new ToolTraceEntity();
        trace.setId(UUID.randomUUID());
        trace.setTraceId(ids.traceId());
        trace.setFraudCase(fraudCase);
        trace.setToolName(toolName);
        trace.setRequestHash(fixtures.requestHash(hashParts));
        trace.setStatus(status);
        trace.setDurationMs(duration);
        trace.setResultSummary(summary);
        trace.setExecutedAt(started);
        traceRepository.save(trace);

        return new ToolOutcome<>(result, status, trace.getTraceId(), duration);
    }

    private String summarise(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            return json.length() > 1800 ? json.substring(0, 1800) + "…" : json;
        } catch (Exception e) {
            return String.valueOf(result);
        }
    }

    public record ToolOutcome<T>(T result, ToolStatus status, String traceId, long durationMs) {
        public boolean success() {
            return status == ToolStatus.SUCCESS && result != null;
        }

        public java.util.Optional<T> resultOptional() {
            return success() ? java.util.Optional.of(result) : java.util.Optional.empty();
        }
    }
}
