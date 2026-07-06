package com.example.vizi.preflight;

import java.util.List;

public record PreflightReport(
        boolean valid,
        List<PreflightIssue> issues
) {
}
