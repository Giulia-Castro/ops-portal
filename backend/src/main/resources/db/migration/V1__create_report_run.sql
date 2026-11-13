-- Audit of on-demand report runs requested through the portal.
-- Portable naming: lowercase, unquoted, at most 30 characters, no reserved words.
CREATE TABLE report_run (
    id          VARCHAR(36)              NOT NULL,
    reportdate  DATE                     NOT NULL,
    requestedby VARCHAR(100)             NOT NULL,
    requestedat TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome     VARCHAR(20)              NOT NULL,
    health      VARCHAR(10),
    durationms  BIGINT                   NOT NULL,
    completedat TIMESTAMP WITH TIME ZONE,
    createdat   TIMESTAMP WITH TIME ZONE NOT NULL,
    createdby   VARCHAR(100)             NOT NULL,
    updatedat   TIMESTAMP WITH TIME ZONE NOT NULL,
    updatedby   VARCHAR(100)             NOT NULL,
    rowversion  BIGINT                   NOT NULL,
    CONSTRAINT pk_report_run PRIMARY KEY (id)
);

CREATE INDEX ix_report_run_requestedat ON report_run (requestedat);
