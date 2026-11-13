package io.github.giuliacastro.opsportal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Portal configuration. Secrets come only from environment variables, never from defaults. */
@Validated
@ConfigurationProperties(prefix = "portal")
public record PortalProperties(
        @Valid Upstream triage,
        @Valid Upstream reports,
        @Valid Upstream n8n,
        @Valid Users users,
        @NotBlank String timezone,
        @Min(0) @Max(120) int triageManualMinutes,
        @Min(1) @Max(90) int recentDays,
        @Min(1) @Max(365) int maxRunAgeDays,
        @Valid Resilience resilience) {

    /** An upstream system: base URL (or webhook URL for n8n), its API key and timeouts. */
    public record Upstream(
            @NotBlank String url,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout) {}

    /** Passwords for the two built-in accounts. A blank password disables that account. */
    public record Users(String viewerPassword, String operatorPassword) {}

    public record Resilience(
            @Min(1) @Max(5) int maxAttempts,
            @Min(1) int failureThreshold,
            Duration openDuration) {}
}
