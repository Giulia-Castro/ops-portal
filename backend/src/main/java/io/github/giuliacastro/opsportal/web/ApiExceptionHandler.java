package io.github.giuliacastro.opsportal.web;

import io.github.giuliacastro.opsportal.application.SourceUnavailableException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Technical failures and request errors as RFC 7807. Business errors come as Result values. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemFactory problems;

    public ApiExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> invalid(Exception e) {
        return problems.build(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> loginFailed(AuthenticationException e) {
        log.warn("auth.login.failed reason={}", e.getClass().getSimpleName());
        return problems.build(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", Map.of());
    }

    @ExceptionHandler(SourceUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(SourceUnavailableException e) {
        log.error("upstream.unavailable source={}", e.source(), e);
        ResponseEntity<ProblemDetail> response = problems.build(
                HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_UNAVAILABLE", Map.of("source", e.source()));
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header("Retry-After", "30")
                .body(response.getBody());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException e) {
        return problems.build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("unhandled.exception", e);
        return problems.build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", Map.of());
    }
}
