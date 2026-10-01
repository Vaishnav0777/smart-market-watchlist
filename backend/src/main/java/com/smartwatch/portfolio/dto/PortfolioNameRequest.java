package com.smartwatch.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortfolioNameRequest(
        @NotBlank @Size(max = 120) String name) {
}
