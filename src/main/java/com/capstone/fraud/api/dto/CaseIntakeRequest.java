package com.capstone.fraud.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CaseIntakeRequest(
        @NotBlank @Size(max = 64) String maskedCustomerRef,
        @Size(max = 64) String homeRegion,
        @NotBlank @Size(max = 64) String transactionRef,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Instant occurredAt,
        @NotBlank String status,
        @NotBlank String channel,
        @NotBlank @Size(max = 96) String maskedMerchant,
        @NotBlank String issueType,
        @NotBlank @Size(max = 4000) String customerStatement,
        @NotNull Boolean cardPossession,
        @NotNull Boolean merchantRecognised,
        String intakeChannel,
        Instant reportedAt,
        @AssertTrue(message = "syntheticDataConfirmed must be true") Boolean syntheticDataConfirmed,
        @AssertTrue(message = "reviewRequired must be true") Boolean reviewRequired) {
}
