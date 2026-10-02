package com.smartwatch.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * One question. {@code portfolioId} limits the answer to that book when present.
 * The owner always comes from the access token.
 */
public record AssistantQuestionRequest(
        @NotBlank @Size(max = 500) String question,
        UUID portfolioId) {
}
