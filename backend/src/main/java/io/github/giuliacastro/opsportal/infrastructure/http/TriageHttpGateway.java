package io.github.giuliacastro.opsportal.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.giuliacastro.opsportal.application.Ports.TriageGateway;
import io.github.giuliacastro.opsportal.domain.TriageKpis;
import java.math.BigDecimal;
import java.util.Map;

/** Adapter for the ticket triage agent (GET /v1/metrics/summary). */
public class TriageHttpGateway implements TriageGateway {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MetricsDto(
            int total,
            int routed,
            int needsReview,
            Map<String, Integer> byCategory,
            double p95LatencyMs,
            BigDecimal totalCostUsd,
            int feedbackCount,
            Double categoryAccuracy,
            Double severityAccuracy) {}

    private final UpstreamClient client;

    public TriageHttpGateway(UpstreamClient client) {
        this.client = client;
    }

    @Override
    public TriageKpis fetchKpis() {
        MetricsDto dto = client.get("/v1/metrics/summary", MetricsDto.class);
        return new TriageKpis(
                dto.total(),
                dto.routed(),
                dto.needsReview(),
                dto.categoryAccuracy(),
                dto.severityAccuracy(),
                dto.feedbackCount(),
                dto.p95LatencyMs(),
                dto.totalCostUsd() == null ? BigDecimal.ZERO : dto.totalCostUsd(),
                dto.byCategory() == null ? Map.of() : dto.byCategory());
    }
}
