package io.github.giuliacastro.opsportal.web;

import io.github.giuliacastro.opsportal.domain.DomainError;
import io.github.giuliacastro.opsportal.domain.ErrorCode;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** RFC 7807 problems with title and detail translated from messages_{locale}.properties. */
@Component
public class ProblemFactory {

    private static final Map<ErrorCode, HttpStatus> DOMAIN_STATUS = Map.of(
            ErrorCode.REPORT_DATE_IN_FUTURE, HttpStatus.UNPROCESSABLE_CONTENT,
            ErrorCode.REPORT_DATE_TOO_OLD, HttpStatus.UNPROCESSABLE_CONTENT,
            ErrorCode.REPORT_ALREADY_DELIVERED, HttpStatus.CONFLICT,
            ErrorCode.REPORT_RUN_IN_PROGRESS, HttpStatus.CONFLICT);

    private final MessageSource messages;

    public ProblemFactory(MessageSource messages) {
        this.messages = messages;
    }

    public ResponseEntity<ProblemDetail> domain(DomainError error) {
        return build(DOMAIN_STATUS.get(error.code()), error.code().name(), error.params());
    }

    public ResponseEntity<ProblemDetail> build(HttpStatus status, String code, Map<String, Object> params) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("urn:ops-portal:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(text("problem." + code + ".title", params, locale, code));
        problem.setDetail(text("problem." + code + ".detail", params, locale, code));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (!params.isEmpty()) {
            problem.setProperty("params", params);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Content-Language", locale.toLanguageTag())
                .body(problem);
    }

    private String text(String key, Map<String, Object> params, Locale locale, String fallback) {
        String template = messages.getMessage(key, null, fallback, locale);
        for (var entry : params.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return template;
    }
}
