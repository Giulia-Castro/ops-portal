package io.github.giuliacastro.opsportal.config;

import io.github.giuliacastro.opsportal.application.GetDashboardUseCase;
import io.github.giuliacastro.opsportal.application.ListReportRunsUseCase;
import io.github.giuliacastro.opsportal.application.Ports.ReportGateway;
import io.github.giuliacastro.opsportal.application.Ports.ReportRunRepository;
import io.github.giuliacastro.opsportal.application.Ports.ReportTrigger;
import io.github.giuliacastro.opsportal.application.Ports.TriageGateway;
import io.github.giuliacastro.opsportal.application.RunReportUseCase;
import io.github.giuliacastro.opsportal.domain.ReportRunPolicy;
import io.github.giuliacastro.opsportal.infrastructure.http.CircuitBreaker;
import io.github.giuliacastro.opsportal.infrastructure.http.N8nReportTrigger;
import io.github.giuliacastro.opsportal.infrastructure.http.ReportHttpGateway;
import io.github.giuliacastro.opsportal.infrastructure.http.TriageHttpGateway;
import io.github.giuliacastro.opsportal.infrastructure.http.UpstreamClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Composition root: the domain and application layers have no Spring annotations. */
@Configuration
public class AppConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TriageGateway triageGateway(PortalProperties props, RestClient.Builder builder, Clock clock) {
        return new TriageHttpGateway(upstream("triage", props.triage(), props, builder, clock));
    }

    @Bean
    ReportGateway reportGateway(PortalProperties props, RestClient.Builder builder, Clock clock) {
        return new ReportHttpGateway(upstream("reports", props.reports(), props, builder, clock));
    }

    @Bean
    ReportTrigger reportTrigger(PortalProperties props, RestClient.Builder builder, Clock clock) {
        var n8n = props.n8n();
        RestClient client = restClient(builder, n8n).mutate().baseUrl(n8n.url()).build();
        return new N8nReportTrigger(client, breaker(props, clock));
    }

    @Bean
    GetDashboardUseCase getDashboardUseCase(
            TriageGateway triage, ReportGateway reports, Clock clock, PortalProperties props) {
        return new GetDashboardUseCase(
                triage, reports, clock, props.triageManualMinutes(), props.recentDays());
    }

    @Bean
    RunReportUseCase runReportUseCase(
            ReportGateway reports, ReportTrigger trigger, ReportRunRepository runs,
            Clock clock, PortalProperties props) {
        return new RunReportUseCase(
                new ReportRunPolicy(props.maxRunAgeDays()), reports, trigger, runs, clock,
                ZoneId.of(props.timezone()), () -> UUID.randomUUID().toString());
    }

    @Bean
    ListReportRunsUseCase listReportRunsUseCase(ReportRunRepository runs) {
        return new ListReportRunsUseCase(runs);
    }

    private static UpstreamClient upstream(
            String name, PortalProperties.Upstream upstream, PortalProperties props,
            RestClient.Builder builder, Clock clock) {
        RestClient client = restClient(builder, upstream).mutate().baseUrl(upstream.url()).build();
        return new UpstreamClient(
                name, client, breaker(props, clock), props.resilience().maxAttempts());
    }

    private static RestClient restClient(RestClient.Builder builder, PortalProperties.Upstream upstream) {
        // HTTP/1.1 on purpose: the JDK client's default h2c upgrade ("Upgrade: h2c") makes
        // Node servers such as n8n drop the connection.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(orDefault(upstream.connectTimeout(), Duration.ofSeconds(3)))
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(orDefault(upstream.readTimeout(), Duration.ofSeconds(10)));
        RestClient.Builder configured = builder.clone().requestFactory(factory);
        if (upstream.apiKey() != null && !upstream.apiKey().isBlank()) {
            configured.defaultHeader(API_KEY_HEADER, upstream.apiKey());
        }
        return configured.build();
    }

    private static CircuitBreaker breaker(PortalProperties props, Clock clock) {
        var resilience = props.resilience();
        return new CircuitBreaker(
                resilience.failureThreshold(),
                orDefault(resilience.openDuration(), Duration.ofSeconds(30)),
                clock);
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
