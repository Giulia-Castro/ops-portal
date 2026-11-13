package io.github.giuliacastro.opsportal.web;

import io.github.giuliacastro.opsportal.application.GetDashboardUseCase;
import io.github.giuliacastro.opsportal.application.ListReportRunsUseCase;
import io.github.giuliacastro.opsportal.application.RunReportUseCase;
import io.github.giuliacastro.opsportal.domain.Dashboard;
import io.github.giuliacastro.opsportal.domain.HealthStatus;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import io.github.giuliacastro.opsportal.domain.Result;
import io.github.giuliacastro.opsportal.domain.RunOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PortalController {

    public record RunReportRequest(@NotNull LocalDate reportDate) {}

    public record ReportRunResponse(
            String id,
            LocalDate reportDate,
            String requestedBy,
            Instant requestedAt,
            RunOutcome outcome,
            HealthStatus health,
            long durationMs) {

        static ReportRunResponse of(ReportRun run) {
            return new ReportRunResponse(
                    run.id(), run.reportDate(), run.requestedBy(), run.requestedAt(),
                    run.outcome(), run.health(), run.durationMs());
        }
    }

    private final GetDashboardUseCase dashboard;
    private final RunReportUseCase runReport;
    private final ListReportRunsUseCase listRuns;
    private final ProblemFactory problems;

    public PortalController(
            GetDashboardUseCase dashboard, RunReportUseCase runReport,
            ListReportRunsUseCase listRuns, ProblemFactory problems) {
        this.dashboard = dashboard;
        this.runReport = runReport;
        this.listRuns = listRuns;
        this.problems = problems;
    }

    /** Aggregated, non-personal metrics of both systems. */
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return dashboard.execute();
    }

    @GetMapping("/report-runs")
    public List<ReportRunResponse> reportRuns(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return listRuns.execute(limit).stream().map(ReportRunResponse::of).toList();
    }

    /** Runs the n8n workflow for one day. Waits for the workflow (up to the n8n read timeout). */
    @PostMapping("/report-runs")
    public ResponseEntity<?> runReport(
            @Valid @RequestBody RunReportRequest body, Authentication auth) {
        return switch (runReport.execute(body.reportDate(), auth.getName())) {
            case Result.Ok<ReportRun>(ReportRun run) ->
                    ResponseEntity.status(HttpStatus.CREATED).body(ReportRunResponse.of(run));
            case Result.Err<ReportRun>(var error) -> problems.domain(error);
        };
    }
}
