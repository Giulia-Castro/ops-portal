package io.github.giuliacastro.opsportal.domain;

import java.time.LocalDate;
import java.util.Map;

/** Which days an operator may (re)run. Logs older than the retention window are gone. */
public record ReportRunPolicy(int maxAgeDays) {

    public ReportRunPolicy {
        if (maxAgeDays < 1) {
            throw new IllegalArgumentException("maxAgeDays must be positive");
        }
    }

    public Result<LocalDate> validate(LocalDate reportDate, LocalDate today) {
        if (!reportDate.isBefore(today)) {
            return Result.err(DomainError.of(
                    ErrorCode.REPORT_DATE_IN_FUTURE, Map.of("reportDate", reportDate.toString())));
        }
        if (reportDate.isBefore(today.minusDays(maxAgeDays))) {
            return Result.err(DomainError.of(
                    ErrorCode.REPORT_DATE_TOO_OLD,
                    Map.of("reportDate", reportDate.toString(), "maxAgeDays", maxAgeDays)));
        }
        return Result.ok(reportDate);
    }
}
