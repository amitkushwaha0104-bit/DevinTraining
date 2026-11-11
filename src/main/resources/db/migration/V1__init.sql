CREATE TABLE fraud_case (
    id                   BINARY(16)   NOT NULL PRIMARY KEY,
    case_id              VARCHAR(32)  NOT NULL UNIQUE,
    masked_customer_ref  VARCHAR(64)  NOT NULL,
    home_region          VARCHAR(64)  NULL,
    channel              VARCHAR(16)  NOT NULL,
    analyst_id           VARCHAR(64)  NOT NULL,
    reported_at          DATETIME(6)  NOT NULL,
    status               VARCHAR(24)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL,
    INDEX idx_fraud_case_status (status)
) ENGINE = InnoDB;

CREATE TABLE transaction_claim (
    id                    BINARY(16)    NOT NULL PRIMARY KEY,
    case_id               BINARY(16)    NOT NULL,
    transaction_ref       VARCHAR(64)   NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    occurred_at           DATETIME(6)   NOT NULL,
    transaction_status    VARCHAR(16)   NOT NULL,
    transaction_channel   VARCHAR(24)   NOT NULL,
    masked_merchant       VARCHAR(96)   NOT NULL,
    issue_type            VARCHAR(32)   NOT NULL,
    customer_statement    VARCHAR(4000) NOT NULL,
    card_possession       BIT           NOT NULL,
    merchant_recognised   BIT           NOT NULL,
    INDEX idx_claim_case (case_id),
    CONSTRAINT fk_claim_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB;

CREATE TABLE evidence_item (
    id             BINARY(16)    NOT NULL PRIMARY KEY,
    evidence_id    VARCHAR(32)   NOT NULL UNIQUE,
    case_id        BINARY(16)    NOT NULL,
    type           VARCHAR(24)   NOT NULL,
    severity       VARCHAR(16)   NOT NULL,
    value_summary  VARCHAR(2000) NOT NULL,
    provenance     VARCHAR(24)   NOT NULL,
    observed_at    DATETIME(6)   NOT NULL,
    detail_json    VARCHAR(4000) NULL,
    INDEX idx_evidence_case (case_id),
    CONSTRAINT fk_evidence_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB;

CREATE TABLE recommendation (
    id                        BINARY(16)    NOT NULL PRIMARY KEY,
    recommendation_id         VARCHAR(32)   NOT NULL UNIQUE,
    case_id                   BINARY(16)    NOT NULL,
    mode                      VARCHAR(16)   NOT NULL,
    classification            VARCHAR(40)   NOT NULL,
    recommended_route         VARCHAR(32)   NOT NULL,
    risk_level                VARCHAR(16)   NOT NULL,
    confidence                DOUBLE        NOT NULL,
    human_review_required     BIT           NOT NULL,
    rationale                 VARCHAR(4000) NOT NULL,
    missing_information_json  VARCHAR(4000) NULL,
    evidence_ids_json         VARCHAR(2000) NULL,
    tool_trace_ids_json       VARCHAR(2000) NULL,
    rule_ids_json             VARCHAR(1000) NULL,
    created_at                DATETIME(6)   NOT NULL,
    INDEX idx_recommendation_case (case_id),
    CONSTRAINT fk_recommendation_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB;

CREATE TABLE policy_citation (
    id                 BINARY(16)    NOT NULL PRIMARY KEY,
    recommendation_id  BINARY(16)    NOT NULL,
    document_id        VARCHAR(64)   NOT NULL,
    version            VARCHAR(16)   NOT NULL,
    section            VARCHAR(64)   NOT NULL,
    chunk_id           VARCHAR(64)   NOT NULL,
    score              DOUBLE        NOT NULL,
    passage            VARCHAR(4000) NOT NULL,
    INDEX idx_citation_recommendation (recommendation_id),
    CONSTRAINT fk_citation_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendation (id)
) ENGINE = InnoDB;

CREATE TABLE tool_trace (
    id              BINARY(16)    NOT NULL PRIMARY KEY,
    trace_id        VARCHAR(32)   NOT NULL UNIQUE,
    case_id         BINARY(16)    NOT NULL,
    tool_name       VARCHAR(64)   NOT NULL,
    request_hash    VARCHAR(64)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    duration_ms     BIGINT        NOT NULL,
    result_summary  VARCHAR(2000) NOT NULL,
    executed_at     DATETIME(6)   NOT NULL,
    INDEX idx_trace_case (case_id),
    CONSTRAINT fk_trace_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB;

CREATE TABLE review_disposition (
    id              BINARY(16)    NOT NULL PRIMARY KEY,
    review_id       VARCHAR(32)   NOT NULL UNIQUE,
    case_id         BINARY(16)    NOT NULL,
    reviewer_id     VARCHAR(64)   NOT NULL,
    action          VARCHAR(32)   NOT NULL,
    selected_route  VARCHAR(32)   NULL,
    reason          VARCHAR(4000) NOT NULL,
    recorded_at     DATETIME(6)   NOT NULL,
    INDEX idx_review_case (case_id),
    CONSTRAINT fk_review_case FOREIGN KEY (case_id) REFERENCES fraud_case (id)
) ENGINE = InnoDB;

CREATE TABLE audit_event (
    id            BINARY(16)    NOT NULL PRIMARY KEY,
    event_id      VARCHAR(32)   NOT NULL UNIQUE,
    case_id       BINARY(16)    NULL,
    event_type    VARCHAR(64)   NOT NULL,
    actor_type    VARCHAR(24)   NOT NULL,
    actor_id      VARCHAR(64)   NULL,
    recorded_at   DATETIME(6)   NOT NULL,
    payload_hash  VARCHAR(64)   NOT NULL,
    summary       VARCHAR(2000) NULL,
    INDEX idx_audit_case (case_id),
    INDEX idx_audit_recorded (recorded_at)
) ENGINE = InnoDB;

CREATE TABLE case_message (
    id          BINARY(16)    NOT NULL PRIMARY KEY,
    case_id     VARCHAR(32)   NOT NULL,
    role        VARCHAR(16)   NOT NULL,
    content     VARCHAR(8000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    INDEX idx_message_case (case_id)
) ENGINE = InnoDB;
