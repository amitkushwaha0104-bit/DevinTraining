package com.capstone.fraud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank String action,
        @Size(max = 32) String selectedRoute,
        @NotBlank(message = "Reviewer rationale is mandatory") @Size(max = 4000) String reason) {
}
