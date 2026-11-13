package io.github.giuliacastro.opsportal.application;

import io.github.giuliacastro.opsportal.domain.DailyReportSummary;
import io.github.giuliacastro.opsportal.domain.HealthStatus;
import io.github.giuliacastro.opsportal.domain.ReportKpis;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import io.github.giuliacastro.opsportal.domain.TriageKpis;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Outbound ports. Implementations throw {@link SourceUnavailableException} on technical failure. */
public final class Ports {

    private Ports() {}

    /** Project 1: ticket triage agent. */
    public interface TriageGateway {
        TriageKpis fetchKpis();
    }

    /** Project 2: report engine (read side). */
    public interface ReportGateway {
        ReportKpis fetchKpis();

        List<DailyReportSummary> recentReports(int limit);

        Optional<DailyReportSummary> findByDate(LocalDate reportDate);
    }

    /** Project 2: n8n workflow (write side). Not idempotent, so never retried. */
    public interface ReportTrigger {
        TriggerResult trigger(LocalDate reportDate);
    }

    /**
     * {@code delivered} false means the workflow ran and failed (its error workflow alerted
     * on-call). Unreachable n8n is a {@link SourceUnavailableException} instead.
     */
    public record TriggerResult(boolean delivered, HealthStatus health) {}

    public interface ReportRunRepository {
        ReportRun save(ReportRun run);

        List<ReportRun> recent(int limit);
    }
}
