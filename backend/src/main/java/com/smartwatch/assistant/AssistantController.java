package com.smartwatch.assistant;

import com.smartwatch.assistant.dto.AssistantAnswerResponse;
import com.smartwatch.assistant.dto.AssistantQuestionRequest;
import com.smartwatch.user.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final AuthenticatedUser authenticatedUser;

    public AssistantController(AssistantService assistantService, AuthenticatedUser authenticatedUser) {
        this.assistantService = assistantService;
        this.authenticatedUser = authenticatedUser;
    }

    @PostMapping("/questions")
    public ResponseEntity<AssistantAnswerResponse> ask(@Valid @RequestBody AssistantQuestionRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(assistantService.ask(authenticatedUser.requireId(), request));
    }
}
