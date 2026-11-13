package io.github.giuliacastro.opsportal.domain;

import java.math.BigDecimal;

/** Aggregated metrics of the daily report pipeline (project 2). */
public record ReportKpis(
        int generated,
        int delivered,
        int aiSummaries,
        int fallbackSummaries,
        int failures,
        Double successRate,
        Double avgPipelineSeconds,
        BigDecimal totalLlmCostUsd,
        double estimatedHoursSaved) {}
