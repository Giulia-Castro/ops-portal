package io.github.giuliacastro.opsportal.infrastructure.persistence;

import io.github.giuliacastro.opsportal.application.Ports.ReportRunRepository;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaReportRunRepository implements ReportRunRepository {

    private final SpringDataReportRuns runs;

    JpaReportRunRepository(SpringDataReportRuns runs) {
        this.runs = runs;
    }

    @Override
    @Transactional
    public ReportRun save(ReportRun run) {
        return runs.save(ReportRunEntity.from(run)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportRun> recent(int limit) {
        return runs.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "requestedAt")))
                .map(ReportRunEntity::toDomain)
                .toList();
    }
}
