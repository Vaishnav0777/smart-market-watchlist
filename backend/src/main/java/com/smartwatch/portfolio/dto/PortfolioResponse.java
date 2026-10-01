package com.smartwatch.portfolio.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        List<PositionResponse> positions) {
}
