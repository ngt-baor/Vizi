package com.example.vizi.ai;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiController {

    private final AiConfigService aiConfigService;
    private final AiTextRewriteService aiTextRewriteService;

    AiController(AiConfigService aiConfigService, AiTextRewriteService aiTextRewriteService) {
        this.aiConfigService = aiConfigService;
        this.aiTextRewriteService = aiTextRewriteService;
    }

    @GetMapping("/api/ai/status")
    AiConfigStatus status() {
        return aiConfigService.requireConfigured();
    }

    @PostMapping("/api/ai/text/rewrite")
    AiTextRewriteResponse rewriteText(
            @Valid @RequestBody AiTextRewriteRequest request,
            Authentication authentication
    ) {
        return aiTextRewriteService.rewrite(request, authentication.getName());
    }
}