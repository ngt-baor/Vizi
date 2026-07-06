package com.example.vizi.preflight;

import java.util.List;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class PreflightService {

    private final ObjectMapper objectMapper;

    public PreflightService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PreflightReport check(String canvasJson, double widthMm, double heightMm) {
        try {
            var canvas = objectMapper.readTree(canvasJson);
            var layers = canvas.get("layers");
            if (!canvas.isObject() || layers == null || !layers.isArray()) {
                return report(new PreflightIssue(
                        "ERROR",
                        "INVALID_CANVAS",
                        "Canvas JSON must contain a layers array.",
                        null
                ));
            }
            if (layers.isEmpty()) {
                return report(new PreflightIssue(
                        "ERROR",
                        "EMPTY_CANVAS",
                        "Canvas must contain at least one layer.",
                        null
                ));
            }
            return new PreflightReport(true, List.of());
        } catch (Exception exception) {
            return report(new PreflightIssue(
                    "ERROR",
                    "INVALID_CANVAS",
                    "Canvas JSON is invalid.",
                    null
            ));
        }
    }

    private static PreflightReport report(PreflightIssue issue) {
        return new PreflightReport(false, List.of(issue));
    }
}
