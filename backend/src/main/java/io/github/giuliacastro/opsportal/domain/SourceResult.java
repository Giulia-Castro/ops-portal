package io.github.giuliacastro.opsportal.domain;

/** Data from one upstream system, or a marker that it was unavailable (partial dashboard). */
public record SourceResult<T>(T data, boolean available) {

    public static <T> SourceResult<T> of(T data) {
        return new SourceResult<>(data, true);
    }

    public static <T> SourceResult<T> unavailable() {
        return new SourceResult<>(null, false);
    }
}
