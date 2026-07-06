package com.example.vizi.preflight;

public record PreflightIssue(
        String level,
        String code,
        String message,
        Integer layerIndex
) {
}
