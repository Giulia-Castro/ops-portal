package io.github.giuliacastro.opsportal.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Audit record of an on-demand report run: who asked, for which day and what happened. */
public final class ReportRun {

    private final String id;
    private final LocalDate reportDate;
    private final String requestedBy;
    private final Instant requestedAt;
    private RunOutcome outcome;
    private HealthStatus health;
    private long durationMs;
    private Instant completedAt;
    private long version;

    private ReportRun(String id, LocalDate reportDate, String requestedBy, Instant requestedAt) {
        this.id = Objects.requireNonNull(id);
        this.reportDate = Objects.requireNonNull(reportDate);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.requestedAt = Objects.requireNonNull(requestedAt);
    }

    public static ReportRun request(String id, LocalDate reportDate, String requestedBy, Instant now) {
        return new ReportRun(id, reportDate, requestedBy, now);
    }

    public static ReportRun restore(
            String id, LocalDate reportDate, String requestedBy, Instant requestedAt,
            RunOutcome outcome, HealthStatus health, long durationMs, Instant completedAt,
            long version) {
        var run = new ReportRun(id, reportDate, requestedBy, requestedAt);
        run.outcome = outcome;
        run.health = health;
        run.durationMs = durationMs;
        run.completedAt = completedAt;
        run.version = version;
        return run;
    }

    public void complete(RunOutcome result, HealthStatus reportHealth, Instant now) {
        if (outcome != null) {
            throw new IllegalStateException("run " + id + " is already completed");
        }
        this.outcome = Objects.requireNonNull(result);
        this.health = result == RunOutcome.DELIVERED ? reportHealth : null;
        this.completedAt = now;
        this.durationMs = Math.max(0, Duration.between(requestedAt, now).toMillis());
    }

    public String id() {
        return id;
    }

    public LocalDate reportDate() {
        return reportDate;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public RunOutcome outcome() {
        return outcome;
    }

    public HealthStatus health() {
        return health;
    }

    public long durationMs() {
        return durationMs;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public long version() {
        return version;
    }
}
