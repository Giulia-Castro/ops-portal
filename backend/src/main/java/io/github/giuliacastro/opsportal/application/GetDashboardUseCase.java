package io.github.giuliacastro.opsportal.application;

import io.github.giuliacastro.opsportal.application.Ports.ReportGateway;
import io.github.giuliacastro.opsportal.application.Ports.TriageGateway;
import io.github.giuliacastro.opsportal.domain.Dashboard;
import io.github.giuliacastro.opsportal.domain.DailyReportSummary;
import io.github.giuliacastro.opsportal.domain.ReportKpis;
import io.github.giuliacastro.opsportal.domain.SourceResult;
import io.github.giuliacastro.opsportal.domain.TriageKpis;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the dashboard. One system being down degrades the page instead of breaking it. */
public class GetDashboardUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetDashboardUseCase.class);

    private final TriageGateway triage;
    private final ReportGateway reports;
    private final Clock clock;
    private final int triageManualMinutes;
    private final int recentDays;

    public GetDashboardUseCase(
            TriageGateway triage, ReportGateway reports, Clock clock,
            int triageManualMinutes, int recentDays) {
        this.triage = triage;
        this.reports = reports;
        this.clock = clock;
        this.triageManualMinutes = triageManualMinutes;
        this.recentDays = recentDays;
    }

    public Dashboard execute() {
        SourceResult<TriageKpis> triageKpis = fetch("triage", triage::fetchKpis);
        SourceResult<ReportKpis> reportKpis = fetch("reports", reports::fetchKpis);
        List<DailyReportSummary> recent = reportKpis.available()
                ? fetch("reports", () -> reports.recentReports(recentDays)).data()
                : null;
        return Dashboard.compose(
                clock.instant(), triageKpis, reportKpis,
                recent == null ? List.of() : recent, triageManualMinutes);
    }

    private static <T> SourceResult<T> fetch(String source, Supplier<T> call) {
        try {
            return SourceResult.of(call.get());
        } catch (SourceUnavailableException e) {
            log.warn("dashboard.source.unavailable source={} reason={}", source, e.getMessage());
            return SourceResult.unavailable();
        }
    }
}
