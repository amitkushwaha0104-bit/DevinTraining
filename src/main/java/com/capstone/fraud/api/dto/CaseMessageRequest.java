package com.capstone.fraud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaseMessageRequest(
        @NotBlank @Size(max = 2000) String message) {
}
