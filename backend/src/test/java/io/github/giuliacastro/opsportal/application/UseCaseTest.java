package io.github.giuliacastro.opsportal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.giuliacastro.opsportal.application.Ports.ReportGateway;
import io.github.giuliacastro.opsportal.application.Ports.ReportRunRepository;
import io.github.giuliacastro.opsportal.application.Ports.ReportTrigger;
import io.github.giuliacastro.opsportal.application.Ports.TriageGateway;
import io.github.giuliacastro.opsportal.application.Ports.TriggerResult;
import io.github.giuliacastro.opsportal.domain.DailyReportSummary;
import io.github.giuliacastro.opsportal.domain.ErrorCode;
import io.github.giuliacastro.opsportal.domain.HealthStatus;
import io.github.giuliacastro.opsportal.domain.ReportKpis;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import io.github.giuliacastro.opsportal.domain.ReportRunPolicy;
import io.github.giuliacastro.opsportal.domain.Result;
import io.github.giuliacastro.opsportal.domain.RunOutcome;
import io.github.giuliacastro.opsportal.domain.TriageKpis;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UseCaseTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final LocalDate YESTERDAY = LocalDate.parse("2026-11-08");

    static final TriageKpis TRIAGE = new TriageKpis(
            48, 40, 8, 0.85, 0.7, 12, 2900.0, new BigDecimal("0.30"), Map.of("BUG", 8));
    static final ReportKpis REPORTS =
            new ReportKpis(9, 9, 0, 9, 1, 0.9, 7.9, BigDecimal.ZERO, 13.5);

    static DailyReportSummary report(LocalDate day, String status) {
        return new DailyReportSummary(
                "id", day, status, HealthStatus.GREEN, "FALLBACK", 1500, 0.002, 350, 0, 7.0);
    }

    static class FakeReports implements ReportGateway {
        boolean down;
        List<DailyReportSummary> reports = new ArrayList<>();

        @Override
        public ReportKpis fetchKpis() {
            if (down) {
                throw new SourceUnavailableException("reports", "down", null);
            }
            return REPORTS;
        }

        @Override
        public List<DailyReportSummary> recentReports(int limit) {
            return reports;
        }

        @Override
        public Optional<DailyReportSummary> findByDate(LocalDate reportDate) {
            return reports.stream().filter(r -> r.reportDate().equals(reportDate)).findFirst();
        }
    }

    static class FakeRuns implements ReportRunRepository {
        final List<ReportRun> saved = new ArrayList<>();

        @Override
        public ReportRun save(ReportRun run) {
            saved.add(run);
            return run;
        }

        @Override
        public List<ReportRun> recent(int limit) {
            return saved.stream().limit(limit).toList();
        }
    }

    final FakeReports reports = new FakeReports();
    final FakeRuns runs = new FakeRuns();

    RunReportUseCase runUseCase(ReportTrigger trigger) {
        return new RunReportUseCase(
                new ReportRunPolicy(60), reports, trigger, runs, CLOCK, SAO_PAULO, () -> "run-1");
    }

    @Test
    void dashboardAggregatesBothSources() {
        reports.reports.add(report(YESTERDAY, "DELIVERED"));
        TriageGateway triage = () -> TRIAGE;
        var dashboard = new GetDashboardUseCase(triage, reports, CLOCK, 2, 30).execute();

        assertThat(dashboard.triage().data()).isEqualTo(TRIAGE);
        assertThat(dashboard.recentReports()).hasSize(1);
        assertThat(dashboard.impact().totalHoursSaved()).isEqualTo(14.8);
        assertThat(dashboard.impact().complete()).isTrue();
    }

    @Test
    void dashboardDegradesWhenASourceIsDown() {
        reports.down = true;
        TriageGateway triage = () -> {
            throw new SourceUnavailableException("triage", "timeout", null);
        };
        var dashboard = new GetDashboardUseCase(triage, reports, CLOCK, 2, 30).execute();

        assertThat(dashboard.triage().available()).isFalse();
        assertThat(dashboard.reports().available()).isFalse();
        assertThat(dashboard.recentReports()).isEmpty();
        assertThat(dashboard.impact().complete()).isFalse();
    }

    @Test
    void runsAndAuditsADeliveredReport() {
        var result = runUseCase(day -> new TriggerResult(true, HealthStatus.RED))
                .execute(YESTERDAY, "operator");

        var run = ((Result.Ok<ReportRun>) result).value();
        assertThat(run.outcome()).isEqualTo(RunOutcome.DELIVERED);
        assertThat(run.health()).isEqualTo(HealthStatus.RED);
        assertThat(run.requestedBy()).isEqualTo("operator");
        assertThat(runs.saved).containsExactly(run);
        assertThat(new ListReportRunsUseCase(runs).execute(500)).containsExactly(run);
    }

    @Test
    void workflowFailureIsAnOutcomeNotAnError() {
        var result = runUseCase(day -> new TriggerResult(false, null)).execute(YESTERDAY, "operator");
        assertThat(((Result.Ok<ReportRun>) result).value().outcome()).isEqualTo(RunOutcome.FAILED);
    }

    @Test
    void unreachableN8nIsAuditedAndRethrown() {
        ReportTrigger down = day -> {
            throw new SourceUnavailableException("n8n", "refused", null);
        };
        assertThatThrownBy(() -> runUseCase(down).execute(YESTERDAY, "operator"))
                .isInstanceOf(SourceUnavailableException.class);
        assertThat(runs.saved).singleElement()
                .extracting(ReportRun::outcome).isEqualTo(RunOutcome.UNAVAILABLE);
    }

    @Test
    void refusesInvalidOrDeliveredDaysWithoutCallingN8n() {
        ReportTrigger mustNotRun = day -> {
            throw new AssertionError("n8n must not be called");
        };
        var useCase = runUseCase(mustNotRun);

        var future = (Result.Err<ReportRun>) useCase.execute(LocalDate.parse("2026-11-09"), "operator");
        assertThat(future.error().code()).isEqualTo(ErrorCode.REPORT_DATE_IN_FUTURE);

        reports.reports.add(report(YESTERDAY, "DELIVERED"));
        var delivered = (Result.Err<ReportRun>) useCase.execute(YESTERDAY, "operator");
        assertThat(delivered.error().code()).isEqualTo(ErrorCode.REPORT_ALREADY_DELIVERED);
        assertThat(runs.saved).isEmpty();
    }

    @Test
    void onlyOneRunAtATime() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var useCase = runUseCase(day -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new TriggerResult(true, HealthStatus.GREEN);
        });
        var first = new AtomicReference<Result<ReportRun>>();
        Thread thread = Thread.ofVirtual().start(() -> first.set(useCase.execute(YESTERDAY, "a")));
        started.await();

        var second = (Result.Err<ReportRun>) useCase.execute(YESTERDAY.minusDays(1), "b");
        assertThat(second.error().code()).isEqualTo(ErrorCode.REPORT_RUN_IN_PROGRESS);

        release.countDown();
        thread.join();
        assertThat(first.get()).isInstanceOf(Result.Ok.class);
        assertThat(useCase.execute(YESTERDAY.minusDays(2), "c")).isInstanceOf(Result.Ok.class);
    }
}
