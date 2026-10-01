package com.smartwatch.portfolio.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PositionRequest(
        @NotNull UUID instrumentId,
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal averageBuyPrice) {
}
