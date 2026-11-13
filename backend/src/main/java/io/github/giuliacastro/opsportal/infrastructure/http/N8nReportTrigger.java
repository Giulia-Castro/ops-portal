package io.github.giuliacastro.opsportal.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.giuliacastro.opsportal.application.Ports.ReportTrigger;
import io.github.giuliacastro.opsportal.application.Ports.TriggerResult;
import io.github.giuliacastro.opsportal.application.SourceUnavailableException;
import io.github.giuliacastro.opsportal.domain.HealthStatus;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the n8n "Daily ops report" webhook, which answers when the workflow finishes.
 * Not retried: running a report twice is not idempotent (two e-mails).
 */
public class N8nReportTrigger implements ReportTrigger {

    static final String SOURCE = "n8n";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WorkflowResponse(String status, HealthStatus health) {}

    private final RestClient client;
    private final CircuitBreaker breaker;

    public N8nReportTrigger(RestClient client, CircuitBreaker breaker) {
        this.client = client;
        this.breaker = breaker;
    }

    @Override
    public TriggerResult trigger(LocalDate reportDate) {
        if (!breaker.allowsCall()) {
            throw new SourceUnavailableException(SOURCE, "circuit open", null);
        }
        try {
            TriggerResult result = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> UpstreamClient.propagateCorrelationId(h::set))
                    .body(Map.of("reportDate", reportDate.toString()))
                    .exchange((request, response) -> toResult(response.getStatusCode(), response));
            breaker.recordSuccess();
            return result;
        } catch (RestClientException e) {
            breaker.recordFailure();
            throw new SourceUnavailableException(SOURCE, "webhook call failed", e);
        }
    }

    private static TriggerResult toResult(
            HttpStatusCode status, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        if (status.is2xxSuccessful()) {
            WorkflowResponse body = response.bodyTo(WorkflowResponse.class);
            boolean delivered = body != null && "DELIVERED".equals(body.status());
            return new TriggerResult(delivered, body == null ? null : body.health());
        }
        if (status.value() == 500) {
            // n8n answers 500 "Error in workflow" when a node failed; the error workflow alerted.
            return new TriggerResult(false, null);
        }
        throw new SourceUnavailableException(SOURCE, "unexpected HTTP " + status.value(), null);
    }
}
