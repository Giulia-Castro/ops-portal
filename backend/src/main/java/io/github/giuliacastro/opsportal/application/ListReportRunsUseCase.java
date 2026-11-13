package io.github.giuliacastro.opsportal.application;

import io.github.giuliacastro.opsportal.application.Ports.ReportRunRepository;
import io.github.giuliacastro.opsportal.domain.ReportRun;
import java.util.List;

public class ListReportRunsUseCase {

    private final ReportRunRepository runs;

    public ListReportRunsUseCase(ReportRunRepository runs) {
        this.runs = runs;
    }

    public List<ReportRun> execute(int limit) {
        return runs.recent(Math.clamp(limit, 1, 100));
    }
}
