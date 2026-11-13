package io.github.giuliacastro.opsportal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** What the portal shows: both systems side by side plus the combined automation impact. */
public record Dashboard(
        Instant generatedAt,
        SourceResult<TriageKpis> triage,
        SourceResult<ReportKpis> reports,
        List<DailyReportSummary> recentReports,
        HealthBreakdown health,
        AutomationImpact impact) {

    public record HealthBreakdown(int green, int yellow, int red) {

        static HealthBreakdown of(List<DailyReportSummary> reports) {
            int green = 0;
            int yellow = 0;
            int red = 0;
            for (DailyReportSummary report : reports) {
                switch (report.health()) {
                    case GREEN -> green++;
                    case YELLOW -> yellow++;
                    case RED -> red++;
                }
            }
            return new HealthBreakdown(green, yellow, red);
        }
    }

    /**
     * Hours saved are estimates: each system multiplies its automated volume by a configured
     * manual time. {@code complete} is false when a source was down, so the total is partial.
     */
    public record AutomationImpact(
            double reportHoursSaved,
            double triageHoursSaved,
            double totalHoursSaved,
            BigDecimal llmCostUsd,
            boolean complete) {}

    public static Dashboard compose(
            Instant now,
            SourceResult<TriageKpis> triage,
            SourceResult<ReportKpis> reports,
            List<DailyReportSummary> recentReports,
            int triageManualMinutes) {
        double reportHours = reports.available() ? reports.data().estimatedHoursSaved() : 0.0;
        double triageHours = triage.available()
                ? round1(triage.data().routed() * triageManualMinutes / 60.0)
                : 0.0;
        BigDecimal cost = BigDecimal.ZERO;
        if (triage.available()) {
            cost = cost.add(triage.data().totalCostUsd());
        }
        if (reports.available()) {
            cost = cost.add(reports.data().totalLlmCostUsd());
        }
        var impact = new AutomationImpact(
                reportHours,
                triageHours,
                round1(reportHours + triageHours),
                cost,
                triage.available() && reports.available());
        return new Dashboard(
                now, triage, reports, List.copyOf(recentReports),
                HealthBreakdown.of(recentReports), impact);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
