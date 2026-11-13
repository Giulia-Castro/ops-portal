package io.github.giuliacastro.opsportal.domain;

public enum RunOutcome {
    /** The n8n workflow ran and the report e-mail was delivered. */
    DELIVERED,
    /** The workflow failed; its error workflow already alerted on-call. */
    FAILED,
    /** n8n could not be reached. */
    UNAVAILABLE
}
