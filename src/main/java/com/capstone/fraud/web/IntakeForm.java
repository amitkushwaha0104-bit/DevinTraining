package com.capstone.fraud.web;

import com.capstone.fraud.api.dto.CaseIntakeRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Form-backing bean for the intake screen. Enums stay as strings and
 * are validated by the shared intake rules in FraudCaseService.
 */
@Data
public class IntakeForm {

    @NotBlank
    @Size(max = 64)
    private String maskedCustomerRef;

    @Size(max = 64)
    private String homeRegion;

    @NotBlank
    @Size(max = 64)
    private String transactionRef;

    @NotNull
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency = "INR";

    private String occurredAt;

    @NotBlank
    private String status = "POSTED";

    @NotBlank
    private String channel = "CARD_NOT_PRESENT";

    @NotBlank
    @Size(max = 96)
    private String maskedMerchant;

    @NotBlank
    private String issueType = "MERCHANT_NOT_RECOGNISED";

    @NotBlank
    @Size(max = 4000)
    private String customerStatement;

    private boolean cardPossession = true;
    private boolean merchantRecognised;
    private boolean syntheticDataConfirmed;
    private boolean reviewRequired;

    public CaseIntakeRequest toRequest() {
        Instant occurred = (occurredAt == null || occurredAt.isBlank())
                ? Instant.now()
                : java.time.LocalDateTime.parse(occurredAt).toInstant(java.time.ZoneOffset.UTC);
        return new CaseIntakeRequest(
                maskedCustomerRef, homeRegion, transactionRef, amount, currency,
                occurred, status, channel, maskedMerchant, issueType, customerStatement,
                cardPossession, merchantRecognised, "WEB", Instant.now(),
                syntheticDataConfirmed, reviewRequired);
    }
}
