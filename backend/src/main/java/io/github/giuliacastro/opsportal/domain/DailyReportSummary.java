package io.github.giuliacastro.opsportal.domain;

import java.time.LocalDate;

public record DailyReportSummary(
        String id,
        LocalDate reportDate,
        String status,
        HealthStatus health,
        String summarySource,
        int totalRequests,
        double errorRate,
        int p95LatencyMs,
        int anomalyCount,
        Double pipelineSeconds) {

    public boolean delivered() {
        return "DELIVERED".equals(status);
    }
}
