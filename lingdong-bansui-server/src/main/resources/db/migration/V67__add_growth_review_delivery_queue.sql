-- 内部待发队列，不包含微信令牌或消息发送成功状态。
CREATE TABLE growth_review_delivery (
    id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    subscription_version BIGINT NOT NULL,
    period_start DATE NOT NULL,
    review_id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_delivery_week UNIQUE (subscription_id, period_start),
    CONSTRAINT fk_growth_review_delivery_subscription FOREIGN KEY (subscription_id) REFERENCES growth_review_subscription(id),
    CONSTRAINT fk_growth_review_delivery_review FOREIGN KEY (review_id) REFERENCES growth_review(id),
    CONSTRAINT fk_growth_review_delivery_snapshot FOREIGN KEY (snapshot_id) REFERENCES growth_review_snapshot(id),
    CONSTRAINT ck_growth_review_delivery_id CHECK (id >= 1000000000000000000),
    CONSTRAINT ck_growth_review_delivery_version CHECK (subscription_version >= 1),
    CONSTRAINT ck_growth_review_delivery_status CHECK (status IN ('PENDING', 'CANCELLED'))
);
CREATE INDEX idx_growth_review_delivery_expiry ON growth_review_delivery(status, expires_at, id);
