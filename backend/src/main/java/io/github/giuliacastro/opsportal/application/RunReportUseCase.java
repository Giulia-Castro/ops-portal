package io.github.giuliacastro.opsportal.application;

import io.github.giuliacastro.opsportal.application.Ports.ReportGateway;
import io.github.giuliacastro.opsportal.application.Ports.ReportRunRepository;
import io.github.giuliacastro.opsportal.application.Ports.ReportTrigger;
import io.github.giuliacastro.opsportal.application.Ports.TriggerResult;
import io.github.giuliacastro.opsportal.domain.DomainError;
import io.github.giuliacastro.opsportal.domain.ErrorCode;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import io.github.giuliacastro.opsportal.domain.ReportRunPolicy;
import io.github.giuliacastro.opsportal.domain.Result;
import io.github.giuliacastro.opsportal.domain.RunOutcome;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the n8n report workflow for one day on behalf of an operator and audits the attempt.
 * A day already delivered is refused before calling n8n, and only one run happens at a time.
 */
public class RunReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunReportUseCase.class);

    private final ReportRunPolicy policy;
    private final ReportGateway reports;
    private final ReportTrigger trigger;
    private final ReportRunRepository runs;
    private final Clock clock;
    private final ZoneId zone;
    private final Supplier<String> ids;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RunReportUseCase(
            ReportRunPolicy policy, ReportGateway reports, ReportTrigger trigger,
            ReportRunRepository runs, Clock clock, ZoneId zone, Supplier<String> ids) {
        this.policy = policy;
        this.reports = reports;
        this.trigger = trigger;
        this.runs = runs;
        this.clock = clock;
        this.zone = zone;
        this.ids = ids;
    }

    public Result<ReportRun> execute(LocalDate reportDate, String actor) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        if (policy.validate(reportDate, today) instanceof Result.Err<LocalDate>(DomainError error)) {
            return Result.err(error);
        }
        if (reports.findByDate(reportDate).filter(r -> r.delivered()).isPresent()) {
            return Result.err(DomainError.of(
                    ErrorCode.REPORT_ALREADY_DELIVERED, Map.of("reportDate", reportDate.toString())));
        }
        if (!running.compareAndSet(false, true)) {
            return Result.err(DomainError.of(ErrorCode.REPORT_RUN_IN_PROGRESS, Map.of()));
        }
        try {
            return Result.ok(run(reportDate, actor));
        } finally {
            running.set(false);
        }
    }

    private ReportRun run(LocalDate reportDate, String actor) {
        ReportRun run = ReportRun.request(ids.get(), reportDate, actor, clock.instant());
        try {
            TriggerResult result = trigger.trigger(reportDate);
            run.complete(
                    result.delivered() ? RunOutcome.DELIVERED : RunOutcome.FAILED,
                    result.health(), clock.instant());
        } catch (SourceUnavailableException e) {
            run.complete(RunOutcome.UNAVAILABLE, null, clock.instant());
            runs.save(run);
            throw e;
        }
        ReportRun saved = runs.save(run);
        log.info("report.run.completed reportDate={} outcome={} durationMs={} actor={}",
                reportDate, saved.outcome(), saved.durationMs(), actor);
        return saved;
    }
}
