package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;
import com.smartwatch.assistant.dto.AssistantQuestionRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Builds the signed-in user's context and asks the configured model.
 * The model in V1 is local and does not change stored data.
 */
@Service
public class AssistantService {

    private final AssistantContextBuilder contextBuilder;
    private final AssistantModel assistantModel;

    public AssistantService(AssistantContextBuilder contextBuilder, AssistantModel assistantModel) {
        this.contextBuilder = contextBuilder;
        this.assistantModel = assistantModel;
    }

    public AssistantAnswerResponse ask(UUID userId, AssistantQuestionRequest request) {
        AssistantContext context = contextBuilder.build(userId, request.portfolioId());
        return assistantModel.answer(request.question().trim(), context);
    }
}
