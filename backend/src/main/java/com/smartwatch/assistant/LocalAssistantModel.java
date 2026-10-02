package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;
import org.springframework.stereotype.Component;

/**
 * V1 answers locally from the supplied context. It does not open a connection.
 */
@Component
public class LocalAssistantModel implements AssistantModel {

    @Override
    public AssistantAnswerResponse answer(String question, AssistantContext context) {
        return AssistantReplyBuilder.reply(question, context);
    }
}
