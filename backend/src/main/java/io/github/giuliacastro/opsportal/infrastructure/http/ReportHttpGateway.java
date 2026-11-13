package io.github.giuliacastro.opsportal.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.giuliacastro.opsportal.application.Ports.ReportGateway;
import io.github.giuliacastro.opsportal.domain.DailyReportSummary;
import io.github.giuliacastro.opsportal.domain.HealthStatus;
import io.github.giuliacastro.opsportal.domain.ReportKpis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;

/** Adapter for the report engine of the ops report pipeline (read-only endpoints). */
public class ReportHttpGateway implements ReportGateway {

    private static final int LOOKUP_WINDOW = 100;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MetricsDto(
            int reportsGenerated,
            int reportsDelivered,
            int aiSummaries,
            int fallbackSummaries,
            int pipelineFailures,
            Double successRate,
            Double avgPipelineSeconds,
            BigDecimal totalLlmCostUsd,
            double estimatedHoursSaved) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KpiDto(int totalRequests, double errorRate, int p95LatencyMs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReportDto(
            String id,
            LocalDate reportDate,
            String status,
            HealthStatus health,
            String summarySource,
            KpiDto kpis,
            List<Object> anomalies,
            Double pipelineSeconds) {

        DailyReportSummary toDomain() {
            return new DailyReportSummary(
                    id, reportDate, status, health, summarySource,
                    kpis.totalRequests(), kpis.errorRate(), kpis.p95LatencyMs(),
                    anomalies == null ? 0 : anomalies.size(), pipelineSeconds);
        }
    }

    private static final ParameterizedTypeReference<List<ReportDto>> REPORT_LIST =
            new ParameterizedTypeReference<>() {};

    private final UpstreamClient client;

    public ReportHttpGateway(UpstreamClient client) {
        this.client = client;
    }

    @Override
    public ReportKpis fetchKpis() {
        MetricsDto dto = client.get("/v1/metrics/summary", MetricsDto.class);
        return new ReportKpis(
                dto.reportsGenerated(),
                dto.reportsDelivered(),
                dto.aiSummaries(),
                dto.fallbackSummaries(),
                dto.pipelineFailures(),
                dto.successRate(),
                dto.avgPipelineSeconds(),
                dto.totalLlmCostUsd() == null ? BigDecimal.ZERO : dto.totalLlmCostUsd(),
                dto.estimatedHoursSaved());
    }

    @Override
    public List<DailyReportSummary> recentReports(int limit) {
        return client.get("/v1/reports?limit=" + Math.clamp(limit, 1, 100), REPORT_LIST).stream()
                .map(ReportDto::toDomain)
                .sorted(Comparator.comparing(DailyReportSummary::reportDate))
                .toList();
    }

    @Override
    public Optional<DailyReportSummary> findByDate(LocalDate reportDate) {
        return recentReports(LOOKUP_WINDOW).stream()
                .filter(report -> report.reportDate().equals(reportDate))
                .findFirst();
    }
}
