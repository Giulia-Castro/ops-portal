package io.github.giuliacastro.opsportal.infrastructure.http;

import io.github.giuliacastro.opsportal.application.SourceUnavailableException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Read-only access to an upstream API: timeouts (in the RestClient), retry for idempotent GETs
 * and a circuit breaker. Every failure surfaces as {@link SourceUnavailableException}.
 */
public final class UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);
    static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final String name;
    private final RestClient client;
    private final CircuitBreaker breaker;
    private final int maxAttempts;

    public UpstreamClient(String name, RestClient client, CircuitBreaker breaker, int maxAttempts) {
        this.name = name;
        this.client = client;
        this.breaker = breaker;
        this.maxAttempts = maxAttempts;
    }

    public <T> T get(String path, Class<T> type) {
        return call(path, spec -> spec.body(type));
    }

    public <T> T get(String path, ParameterizedTypeReference<T> type) {
        return call(path, spec -> spec.body(type));
    }

    private <T> T call(String path, Function<RestClient.ResponseSpec, T> reader) {
        if (!breaker.allowsCall()) {
            throw new SourceUnavailableException(name, "circuit open", null);
        }
        RestClientException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T body = reader.apply(client.get()
                        .uri(path)
                        .headers(h -> propagateCorrelationId(h::set))
                        .retrieve());
                breaker.recordSuccess();
                return body;
            } catch (RestClientException e) {
                last = e;
                log.warn("upstream.call.failed source={} path={} attempt={} error={}",
                        name, path, attempt, e.getClass().getSimpleName());
            }
        }
        breaker.recordFailure();
        throw new SourceUnavailableException(name, "GET " + path + " failed", last);
    }

    static void propagateCorrelationId(java.util.function.BiConsumer<String, String> setter) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            setter.accept(CORRELATION_HEADER, correlationId);
        }
    }
}
