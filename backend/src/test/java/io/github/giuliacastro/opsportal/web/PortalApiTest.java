package io.github.giuliacastro.opsportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PortalApiTest {

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    static final LocalDate DELIVERED_DAY = TODAY.minusDays(3);
    static final StubUpstreams STUBS = startStubs();

    static StubUpstreams startStubs() {
        try {
            return new StubUpstreams(DELIVERED_DAY);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("portal.triage.url", () -> STUBS.url("/triage"));
        registry.add("portal.triage.api-key", () -> StubUpstreams.TRIAGE_KEY);
        registry.add("portal.reports.url", () -> STUBS.url("/reports"));
        registry.add("portal.reports.api-key", () -> StubUpstreams.REPORT_KEY);
        registry.add("portal.n8n.url", () -> STUBS.url("/n8n/webhook/daily-report"));
        registry.add("portal.n8n.api-key", () -> StubUpstreams.REPORT_KEY);
    }

    @AfterAll
    static void stop() {
        STUBS.stop();
    }

    @Autowired
    MockMvc mvc;

    static final RequestPostProcessor VIEWER = user("viewer").roles("VIEWER");
    static final RequestPostProcessor OPERATOR = user("operator").roles("VIEWER", "OPERATOR");

    @BeforeEach
    void reset() {
        STUBS.triageDown = false;
        STUBS.n8nStatus = 200;
    }

    @Test
    void dashboardRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void dashboardAggregatesBothSystems() throws Exception {
        mvc.perform(get("/api/v1/dashboard").with(VIEWER).header("X-Correlation-ID", "test-corr-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "test-corr-1234"))
                .andExpect(jsonPath("$.triage.available").value(true))
                .andExpect(jsonPath("$.triage.data.categoryAccuracy").value(0.9167))
                .andExpect(jsonPath("$.reports.data.delivered").value(9))
                .andExpect(jsonPath("$.recentReports", hasSize(1)))
                .andExpect(jsonPath("$.recentReports[0].anomalyCount").value(2))
                .andExpect(jsonPath("$.health.red").value(1))
                .andExpect(jsonPath("$.impact.triageHoursSaved").value(1.3))
                .andExpect(jsonPath("$.impact.totalHoursSaved").value(14.8))
                .andExpect(jsonPath("$.impact.complete").value(true));
    }

    @Test
    void wrongPasswordIsALocalizedProblem() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.title").value("Falha no login"));
    }

    @Test
    void dashboardDegradesWhenTriageIsDown() throws Exception {
        STUBS.triageDown = true;
        mvc.perform(get("/api/v1/dashboard").with(VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage.available").value(false))
                .andExpect(jsonPath("$.reports.available").value(true))
                .andExpect(jsonPath("$.impact.complete").value(false));
    }

    @Test
    void onlyOperatorsRunReports() throws Exception {
        String body = "{\"reportDate\":\"" + TODAY.minusDays(1) + "\"}";
        mvc.perform(post("/api/v1/report-runs").with(VIEWER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorRunsAReportAndItIsAudited() throws Exception {
        LocalDate day = TODAY.minusDays(1);
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .header("X-Correlation-ID", "run-corr-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"" + day + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("DELIVERED"))
                .andExpect(jsonPath("$.health").value("YELLOW"))
                .andExpect(jsonPath("$.requestedBy").value("operator"));
        assertThat(STUBS.triggered).anyMatch(b -> b.contains(day.toString()));
        assertThat(STUBS.correlationIds).contains("run-corr-12345");

        mvc.perform(get("/api/v1/report-runs").with(VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportDate").value(day.toString()));
    }

    @Test
    void workflowFailureIsRecordedAsFailed() throws Exception {
        STUBS.n8nStatus = 500;
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"" + TODAY.minusDays(5) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("FAILED"));
    }

    @Test
    void unreachableN8nIs503() throws Exception {
        STUBS.n8nStatus = 404;
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"" + TODAY.minusDays(6) + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.params.source").value("n8n"));
    }

    @Test
    void businessRulesAreProblems() throws Exception {
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"" + DELIVERED_DAY + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_DELIVERED"))
                .andExpect(jsonPath("$.title").value("Relatório já entregue"));
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"" + TODAY + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REPORT_DATE_IN_FUTURE"))
                .andExpect(jsonPath("$.detail").value(
                        "The report for " + TODAY + " can only run after the day ends."));
        mvc.perform(post("/api/v1/report-runs").with(OPERATOR).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportDate\":\"nope\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/report-runs?limit=0").with(VIEWER))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownRoutesAreDenied() throws Exception {
        mvc.perform(get("/api/v2/anything").with(VIEWER)).andExpect(status().isForbidden());
    }
}
