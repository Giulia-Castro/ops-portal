package io.github.giuliacastro.opsportal.application;

/** Technical failure of an upstream system (timeout, connection refused, 5xx, open circuit). */
public class SourceUnavailableException extends RuntimeException {

    private final String source;

    public SourceUnavailableException(String source, String message, Throwable cause) {
        super(source + " unavailable: " + message, cause);
        this.source = source;
    }

    public String source() {
        return source;
    }
}
