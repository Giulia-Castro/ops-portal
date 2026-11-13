package io.github.giuliacastro.opsportal.domain;

import java.math.BigDecimal;
import java.util.Map;

/** Aggregated metrics of the ticket triage agent (project 1). No ticket content. */
public record TriageKpis(
        int total,
        int routed,
        int needsReview,
        Double categoryAccuracy,
        Double severityAccuracy,
        int feedbackCount,
        double p95LatencyMs,
        BigDecimal totalCostUsd,
        Map<String, Integer> byCategory) {

    public TriageKpis {
        byCategory = Map.copyOf(byCategory);
    }

    public double reviewRate() {
        return total == 0 ? 0.0 : Math.round(needsReview * 10_000.0 / total) / 10_000.0;
    }
}
