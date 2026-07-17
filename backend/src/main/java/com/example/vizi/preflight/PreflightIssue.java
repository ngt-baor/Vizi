package com.example.vizi.preflight;

public record PreflightIssue(
        String level,
        String code,
        String message,
        Integer layerIndex,
        String side
) {
    public PreflightIssue(String level, String code, String message, Integer layerIndex) {
        this(level, code, message, layerIndex, null);
    }
}
