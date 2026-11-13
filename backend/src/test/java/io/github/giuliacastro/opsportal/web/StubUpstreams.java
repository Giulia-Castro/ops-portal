package io.github.giuliacastro.opsportal.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake triage API, report engine and n8n webhook, served by the JDK HTTP server. */
final class StubUpstreams {

    static final String TRIAGE_KEY = "triage-key-0123456789";
    static final String REPORT_KEY = "report-key-0123456789";

    final HttpServer server;
    volatile boolean triageDown;
    volatile int n8nStatus = 200;
    final List<String> triggered = new CopyOnWriteArrayList<>();
    final List<String> correlationIds = new CopyOnWriteArrayList<>();
    final LocalDate deliveredDay;

    StubUpstreams(LocalDate deliveredDay) throws IOException {
        this.deliveredDay = deliveredDay;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/triage/v1/metrics/summary", ex -> {
            if (triageDown) {
                send(ex, 503, "{}");
            } else if (authorized(ex, TRIAGE_KEY)) {
                send(ex, 200, """
                        {"total":48,"routed":40,"needsReview":8,"byCategory":{"BUG":8,"ACCESS":8},
                         "avgLatencyMs":2100.5,"p95LatencyMs":3900.0,"totalCostUsd":"0.384000",
                         "avgCostUsd":"0.008000","feedbackCount":12,"categoryAccuracy":0.9167,
                         "severityAccuracy":0.75}""");
            }
        });
        server.createContext("/reports/v1/metrics/summary", ex -> {
            if (authorized(ex, REPORT_KEY)) {
                send(ex, 200, """
                        {"reportsGenerated":9,"reportsDelivered":9,"aiSummaries":0,
                         "fallbackSummaries":9,"pipelineFailures":1,"successRate":0.9,
                         "avgPipelineSeconds":7.9,"totalLlmCostUsd":"0.000000",
                         "manualMinutesPerReport":90,"estimatedHoursSaved":13.5}""");
            }
        });
        server.createContext("/reports/v1/reports", ex -> {
            if (authorized(ex, REPORT_KEY)) {
                send(ex, 200, """
                        [{"id":"r2","reportDate":"%s","status":"DELIVERED","health":"RED",
                          "summarySource":"FALLBACK","kpis":{"totalRequests":1482,"serverErrors":15,
                          "errorRate":0.0101,"p95LatencyMs":419,"affectedUsers":15,"rejectedLines":1},
                          "anomalies":[{"kind":"ERROR_RATE_SLO"},{"kind":"LATENCY_SLO"}],
                          "pipelineSeconds":6.4,"version":3}]""".formatted(deliveredDay));
            }
        });
        server.createContext("/n8n/webhook/daily-report", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            triggered.add(body);
            String correlation = ex.getRequestHeaders().getFirst("X-Correlation-ID");
            if (correlation != null) {
                correlationIds.add(correlation);
            }
            if (!authorized(ex, REPORT_KEY)) {
                return;
            }
            if (n8nStatus == 200) {
                send(ex, 200, "{\"status\":\"DELIVERED\",\"health\":\"YELLOW\",\"version\":3}");
            } else {
                send(ex, n8nStatus, "{\"message\":\"Error in workflow\"}");
            }
        });
        server.start();
    }

    String url(String prefix) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + prefix;
    }

    void stop() {
        server.stop(0);
    }

    private static boolean authorized(HttpExchange ex, String key) throws IOException {
        if (key.equals(ex.getRequestHeaders().getFirst("X-API-Key"))) {
            return true;
        }
        send(ex, 401, "{}");
        return false;
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
