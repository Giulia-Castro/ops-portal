package io.github.giuliacastro.opsportal.domain;

/** Business outcome as a value. Exceptions are reserved for technical failures. */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    record Ok<T>(T value) implements Result<T> {}

    record Err<T>(DomainError error) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(DomainError error) {
        return new Err<>(error);
    }
}
