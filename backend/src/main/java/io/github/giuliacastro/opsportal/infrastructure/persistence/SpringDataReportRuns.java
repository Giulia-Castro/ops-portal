package io.github.giuliacastro.opsportal.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReportRuns extends JpaRepository<ReportRunEntity, String> {}
