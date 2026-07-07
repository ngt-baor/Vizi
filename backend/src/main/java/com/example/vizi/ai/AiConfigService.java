package com.example.vizi.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AiConfigService {

    private final String geminiApiKey;
    private final String textModel;
    private final String imageModel;

    AiConfigService(
            @Value("${app.ai.gemini.api-key:}") String geminiApiKey,
            @Value("${app.ai.gemini.text-model:gemini-3.1-flash-lite}") String textModel,
            @Value("${app.ai.gemini.image-model:gemini-3.1-flash-lite-image}") String imageModel
    ) {
        this.geminiApiKey = geminiApiKey;
        this.textModel = textModel;
        this.imageModel = imageModel;
    }

    AiConfigStatus requireConfigured() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY is not configured");
        }
        return new AiConfigStatus(true, textModel, imageModel);
    }

    String requireGeminiApiKey() {
        requireConfigured();
        return geminiApiKey;
    }

    String textModel() {
        return textModel;
    }
}
