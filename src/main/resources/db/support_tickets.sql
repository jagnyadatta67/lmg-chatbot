-- ============================================================
-- DDL: support_tickets
-- Purpose : Stores every Write-to-Us / support ticket raised
--           through the chatbot widget.
-- Created : 2026-03-14
-- Note    : Hibernate (ddl-auto=update) creates this table
--           automatically; this script is for reference,
--           manual migrations, or fresh DB setup.
-- ============================================================

CREATE TABLE IF NOT EXISTS support_tickets
(
    -- Primary key
    id          BIGINT         NOT NULL AUTO_INCREMENT,

    -- Human-readable ticket reference  e.g. TKT-1715000000000
    ticket_id   VARCHAR(40)    NOT NULL,

    -- Customer-provided fields
    name        VARCHAR(100)   NULL,
    email       VARCHAR(150)   NULL,
    phone       VARCHAR(20)    NULL,
    category    VARCHAR(50)    NULL       COMMENT 'Query|Return|Delivery|Late Delivery|Cancellation|Refund|Other',
    message     VARCHAR(2000)  NULL,

    -- Context fields (auto-populated by widget)
    concept     VARCHAR(30)    NULL       COMMENT 'LIFESTYLE|MAX|HOMECENTRE|BABYSHOP',
    user_id     VARCHAR(100)   NULL       COMMENT 'NULL for guest users',
    appid       VARCHAR(20)    NULL       COMMENT 'ANDROID|IPHONE|Desktop|Mobile',
    env         VARCHAR(10)    NULL       COMMENT 'uat1|uat5|prod',

    -- Internal tracking
    status      VARCHAR(20)    NOT NULL   DEFAULT 'OPEN'  COMMENT 'OPEN|IN_PROGRESS|CLOSED',
    email_sent  TINYINT(1)     NOT NULL   DEFAULT 0,

    -- Timestamps
    created_at  DATETIME       NOT NULL,
    closed_at   DATETIME       NULL,

    -- Constraints
    PRIMARY KEY (id),
    UNIQUE KEY  uq_st_ticket_id  (ticket_id),

    -- Indexes for common query patterns
    INDEX idx_st_user_id    (user_id),
    INDEX idx_st_concept    (concept),
    INDEX idx_st_status     (status),
    INDEX idx_st_created_at (created_at)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = 'Support tickets raised via the Write-to-Us chatbot widget';

-- ── Optional seed row for smoke-testing ────────────────────────────────────
-- INSERT INTO support_tickets
--     (ticket_id, name, email, phone, category, message,
--      concept, user_id, appid, env, status, email_sent, created_at)
-- VALUES
--     ('TKT-TEST-001', 'Test User', 'test@example.com', '9999999999',
--      'Query', 'This is a test ticket.',
--      'LIFESTYLE', NULL, 'Desktop', 'uat1', 'OPEN', 0, NOW());
