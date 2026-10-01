package com.smartwatch.portfolio.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePositionRequest(
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal averageBuyPrice) {
}
