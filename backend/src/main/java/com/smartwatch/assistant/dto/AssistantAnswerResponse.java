package com.smartwatch.assistant.dto;

import java.util.List;

/**
 * A grounded answer. {@code sources} names the existing services that supplied
 * the figures. This record does not carry a prompt or a provider payload.
 */
public record AssistantAnswerResponse(
        String answer,
        boolean refused,
        List<String> sources) {
}
