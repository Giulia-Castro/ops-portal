package io.github.giuliacastro.opsportal.domain;

import java.util.Map;

public record DomainError(ErrorCode code, Map<String, Object> params) {

    public DomainError {
        params = Map.copyOf(params);
    }

    public static DomainError of(ErrorCode code, Map<String, Object> params) {
        return new DomainError(code, params);
    }
}
