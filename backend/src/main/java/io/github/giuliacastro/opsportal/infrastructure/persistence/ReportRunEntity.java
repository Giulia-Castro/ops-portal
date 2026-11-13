package io.github.giuliacastro.opsportal.infrastructure.persistence;

import io.github.giuliacastro.opsportal.domain.HealthStatus;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import io.github.giuliacastro.opsportal.domain.RunOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model. Portable names: lowercase, unquoted, at most 30 characters. */
@Entity
@Table(name = "report_run")
public class ReportRunEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "reportdate", nullable = false)
    private LocalDate reportDate;

    @Column(name = "requestedby", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requestedat", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RunOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "health", length = 10)
    private HealthStatus health;

    @Column(name = "durationms", nullable = false)
    private long durationMs;

    @Column(name = "completedat")
    private Instant completedAt;

    @Column(name = "createdat", nullable = false)
    private Instant createdAt;

    @Column(name = "createdby", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updatedat", nullable = false)
    private Instant updatedAt;

    @Column(name = "updatedby", nullable = false, length = 100)
    private String updatedBy;

    @Version
    @Column(name = "rowversion", nullable = false)
    private long rowVersion;

    protected ReportRunEntity() {}

    static ReportRunEntity from(ReportRun run) {
        var entity = new ReportRunEntity();
        entity.id = run.id();
        entity.reportDate = run.reportDate();
        entity.requestedBy = run.requestedBy();
        entity.requestedAt = run.requestedAt();
        entity.outcome = run.outcome();
        entity.health = run.health();
        entity.durationMs = run.durationMs();
        entity.completedAt = run.completedAt();
        Instant audit = run.completedAt() != null ? run.completedAt() : run.requestedAt();
        entity.createdAt = run.requestedAt();
        entity.createdBy = run.requestedBy();
        entity.updatedAt = audit;
        entity.updatedBy = run.requestedBy();
        return entity;
    }

    ReportRun toDomain() {
        return ReportRun.restore(
                id, reportDate, requestedBy, requestedAt, outcome, health,
                durationMs, completedAt, rowVersion);
    }
}
