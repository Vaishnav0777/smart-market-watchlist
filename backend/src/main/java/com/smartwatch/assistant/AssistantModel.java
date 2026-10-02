package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;

/**
 * Turns a question and a context into an answer.
 * A later provider can implement this without seeing repositories or HTTP handlers.
 */
public interface AssistantModel {

    AssistantAnswerResponse answer(String question, AssistantContext context);
}
