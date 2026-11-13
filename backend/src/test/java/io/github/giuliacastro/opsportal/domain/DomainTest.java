package io.github.giuliacastro.opsportal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainTest {

    static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-11-09");

    static TriageKpis triage(int routed) {
        return new TriageKpis(
                routed + 10, routed, 10, 0.9, 0.8, 20, 3100.0,
                new BigDecimal("0.40"), Map.of("BUG", 5));
    }

    static ReportKpis reports() {
        return new ReportKpis(9, 9, 2, 7, 1, 0.9, 7.9, new BigDecimal("0.10"), 13.5);
    }

    static DailyReportSummary report(String day, HealthStatus health, String status) {
        return new DailyReportSummary(
                "id-" + day, LocalDate.parse(day), status, health, "AI", 1500, 0.01, 400, 1, 7.5);
    }

    @Nested
    class DashboardCompose {

        @Test
        void combinesBothSystems() {
            var dashboard = Dashboard.compose(
                    NOW, SourceResult.of(triage(60)), SourceResult.of(reports()),
                    List.of(
                            report("2026-11-04", HealthStatus.RED, "DELIVERED"),
                            report("2026-11-05", HealthStatus.GREEN, "DELIVERED"),
                            report("2026-11-06", HealthStatus.YELLOW, "DELIVERED"),
                            report("2026-11-07", HealthStatus.GREEN, "DELIVERED")),
                    2);

            assertThat(dashboard.impact().reportHoursSaved()).isEqualTo(13.5);
            assertThat(dashboard.impact().triageHoursSaved()).isEqualTo(2.0);
            assertThat(dashboard.impact().totalHoursSaved()).isEqualTo(15.5);
            assertThat(dashboard.impact().llmCostUsd()).isEqualByComparingTo("0.50");
            assertThat(dashboard.impact().complete()).isTrue();
            assertThat(dashboard.health()).isEqualTo(new Dashboard.HealthBreakdown(2, 1, 1));
            assertThat(dashboard.generatedAt()).isEqualTo(NOW);
        }

        @Test
        void partialWhenASourceIsDown() {
            var dashboard = Dashboard.compose(
                    NOW, SourceResult.unavailable(), SourceResult.of(reports()), List.of(), 2);
            assertThat(dashboard.impact().complete()).isFalse();
            assertThat(dashboard.impact().triageHoursSaved()).isZero();
            assertThat(dashboard.impact().totalHoursSaved()).isEqualTo(13.5);

            var noReports = Dashboard.compose(
                    NOW, SourceResult.of(triage(30)), SourceResult.unavailable(), List.of(), 2);
            assertThat(noReports.impact().reportHoursSaved()).isZero();
            assertThat(noReports.impact().llmCostUsd()).isEqualByComparingTo("0.40");
            assertThat(noReports.triage().available()).isTrue();
            assertThat(noReports.reports().available()).isFalse();
        }
    }

    @Test
    void reviewRate() {
        assertThat(triage(30).reviewRate()).isEqualTo(0.25);
        assertThat(new TriageKpis(0, 0, 0, null, null, 0, 0, BigDecimal.ZERO, Map.of()).reviewRate())
                .isZero();
    }

    @Test
    void deliveredFlag() {
        assertThat(report("2026-11-04", HealthStatus.RED, "DELIVERED").delivered()).isTrue();
        assertThat(report("2026-11-04", HealthStatus.RED, "SUMMARIZED").delivered()).isFalse();
    }

    @Nested
    class RunPolicy {

        final ReportRunPolicy policy = new ReportRunPolicy(60);

        @Test
        void acceptsPastDaysInsideTheWindow() {
            assertThat(policy.validate(TODAY.minusDays(1), TODAY))
                    .isEqualTo(Result.ok(TODAY.minusDays(1)));
            assertThat(policy.validate(TODAY.minusDays(60), TODAY)).isInstanceOf(Result.Ok.class);
        }

        @Test
        void rejectsTodayFutureAndOldDays() {
            assertThat(policy.validate(TODAY, TODAY))
                    .isEqualTo(Result.err(DomainError.of(
                            ErrorCode.REPORT_DATE_IN_FUTURE, Map.of("reportDate", "2026-11-09"))));
            var old = (Result.Err<LocalDate>) policy.validate(TODAY.minusDays(61), TODAY);
            assertThat(old.error().code()).isEqualTo(ErrorCode.REPORT_DATE_TOO_OLD);
            assertThat(old.error().params()).containsEntry("maxAgeDays", 60);
        }

        @Test
        void windowMustBePositive() {
            assertThatThrownBy(() -> new ReportRunPolicy(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Run {

        @Test
        void completesOnceAndMeasuresDuration() {
            var run = ReportRun.request("r1", TODAY.minusDays(1), "operator", NOW);
            run.complete(RunOutcome.DELIVERED, HealthStatus.RED, NOW.plusSeconds(8));

            assertThat(run.outcome()).isEqualTo(RunOutcome.DELIVERED);
            assertThat(run.health()).isEqualTo(HealthStatus.RED);
            assertThat(run.durationMs()).isEqualTo(8000);
            assertThat(run.completedAt()).isEqualTo(NOW.plusSeconds(8));
            assertThatThrownBy(() -> run.complete(RunOutcome.FAILED, null, NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void failedRunHasNoHealth() {
            var run = ReportRun.request("r2", TODAY.minusDays(1), "operator", NOW);
            run.complete(RunOutcome.FAILED, HealthStatus.GREEN, NOW);
            assertThat(run.health()).isNull();
        }

        @Test
        void restoreKeepsEveryField() {
            var run = ReportRun.restore(
                    "r3", TODAY, "operator", NOW, RunOutcome.UNAVAILABLE, null, 12,
                    NOW.plusMillis(12), 3);
            assertThat(run.id()).isEqualTo("r3");
            assertThat(run.reportDate()).isEqualTo(TODAY);
            assertThat(run.requestedBy()).isEqualTo("operator");
            assertThat(run.requestedAt()).isEqualTo(NOW);
            assertThat(run.version()).isEqualTo(3);
        }
    }

    @Test
    void sourceResult() {
        assertThat(SourceResult.of("x").available()).isTrue();
        assertThat(SourceResult.unavailable().data()).isNull();
    }
}
