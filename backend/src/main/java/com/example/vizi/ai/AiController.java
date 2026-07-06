package com.example.vizi.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiController {

    private final AiConfigService aiConfigService;

    AiController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping("/api/ai/status")
    AiConfigStatus status() {
        return aiConfigService.requireConfigured();
    }
}
