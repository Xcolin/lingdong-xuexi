-- V28：成长复盘逻辑记录、不可变快照、分类、趋势和补录基础。
-- 所有主键由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE growth_review (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    current_snapshot_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_period UNIQUE (student_id, period_type, period_start, period_end),
    CONSTRAINT fk_growth_review_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT ck_growth_review_period_type CHECK (period_type IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT ck_growth_review_period CHECK (period_end >= period_start),
    CONSTRAINT ck_growth_review_status CHECK (status IN ('DRAFT', 'FINAL'))
);

CREATE INDEX idx_growth_review_student_period
    ON growth_review (student_id, period_type, period_start, id);

CREATE TABLE growth_review_snapshot (
    id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    content_version INT NOT NULL,
    task_total_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    in_progress_count INT NOT NULL DEFAULT 0,
    pending_optimization_count INT NOT NULL DEFAULT 0,
    exempted_count INT NOT NULL DEFAULT 0,
    completion_rate DECIMAL(7, 4) NOT NULL DEFAULT 0,
    earned_points BIGINT NOT NULL DEFAULT 0,
    pause_count INT NOT NULL DEFAULT 0,
    generation_source VARCHAR(16) NOT NULL,
    fact_fingerprint VARCHAR(64) NOT NULL,
    data_cutoff_at TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_snapshot_version UNIQUE (review_id, content_version),
    CONSTRAINT fk_growth_review_snapshot_review FOREIGN KEY (review_id) REFERENCES growth_review (id),
    CONSTRAINT ck_growth_review_snapshot_version CHECK (content_version > 0),
    CONSTRAINT ck_growth_review_snapshot_counts CHECK (
        task_total_count >= 0 AND completed_count >= 0 AND in_progress_count >= 0
        AND pending_optimization_count >= 0 AND exempted_count >= 0
        AND pause_count >= 0
    ),
    CONSTRAINT ck_growth_review_snapshot_rate CHECK (completion_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_growth_review_snapshot_source CHECK (generation_source IN ('AUTO', 'BACKFILL'))
);

CREATE INDEX idx_growth_review_snapshot_review_generated
    ON growth_review_snapshot (review_id, generated_at, id);

ALTER TABLE growth_review
    ADD CONSTRAINT fk_growth_review_current_snapshot
        FOREIGN KEY (current_snapshot_id) REFERENCES growth_review_snapshot (id);

CREATE TABLE growth_review_category_stat (
    id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    task_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_category UNIQUE (snapshot_id, category_code),
    CONSTRAINT fk_growth_review_category_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES growth_review_snapshot (id),
    CONSTRAINT ck_growth_review_category_counts CHECK (
        task_count >= 0 AND completed_count >= 0 AND completed_count <= task_count
    )
);

CREATE TABLE growth_review_daily_trend (
    id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    trend_date DATE NOT NULL,
    task_total_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    in_progress_count INT NOT NULL DEFAULT 0,
    pending_optimization_count INT NOT NULL DEFAULT 0,
    completion_rate DECIMAL(7, 4) NOT NULL DEFAULT 0,
    earned_points BIGINT NOT NULL DEFAULT 0,
    pause_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_trend_date UNIQUE (snapshot_id, trend_date),
    CONSTRAINT fk_growth_review_trend_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES growth_review_snapshot (id),
    CONSTRAINT ck_growth_review_trend_counts CHECK (
        task_total_count >= 0 AND completed_count >= 0 AND in_progress_count >= 0
        AND pending_optimization_count >= 0 AND pause_count >= 0
    ),
    CONSTRAINT ck_growth_review_trend_rate CHECK (completion_rate BETWEEN 0 AND 1)
);

CREATE INDEX idx_growth_review_trend_snapshot_date
    ON growth_review_daily_trend (snapshot_id, trend_date);

CREATE TABLE growth_review_supplement (
    id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    editor_user_id BIGINT NOT NULL,
    editor_role VARCHAR(16) NOT NULL,
    supplement_type VARCHAR(32) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    supplemented_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_growth_review_supplement_review FOREIGN KEY (review_id) REFERENCES growth_review (id),
    CONSTRAINT fk_growth_review_supplement_editor FOREIGN KEY (editor_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_growth_review_supplement_role CHECK (editor_role IN ('PARENT', 'STUDENT')),
    CONSTRAINT ck_growth_review_supplement_type CHECK (
        supplement_type IN ('INSIGHT', 'STRENGTH_WEAKNESS', 'NEXT_PLAN')
    ),
    CONSTRAINT ck_growth_review_supplement_content CHECK (
        CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 1000
    )
);

CREATE INDEX idx_growth_review_supplement_review_time
    ON growth_review_supplement (review_id, supplemented_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646390, 'DAILY_GROWTH_REVIEW', '每日成长复盘',
        'GLOBAL', 'GLOBAL', 'ENABLED', 1,
        '控制每日成长复盘自动生成和补录，历史记录查询不受关闭影响。'),
    (1874244142494646391, 'PERIODIC_GROWTH_REPORT', '周期成长报告',
        'GLOBAL', 'GLOBAL', 'ENABLED', 1,
        '控制周报和月报自动生成，历史记录查询不受关闭影响。');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646392, 'GROWTH_REVIEW_READ_SELF', '查询本人成长复盘',
        'OPERATION', 'MINIAPP', NULL, 220, 'ENABLED',
        '学生在小程序查询本人日、周、月成长复盘。'),
    (1874244142494646393, 'GROWTH_REVIEW_READ_CHILD', '查询孩子成长复盘',
        'OPERATION', 'WEB', NULL, 230, 'ENABLED',
        '主家长在 Web 查询活动主关系学生的成长复盘。'),
    (1874244142494646394, 'GROWTH_REVIEW_SUPPLEMENT_SELF', '补录本人成长复盘',
        'OPERATION', 'MINIAPP', NULL, 240, 'ENABLED',
        '学生在允许时限内补录本人成长复盘。'),
    (1874244142494646395, 'GROWTH_REVIEW_SUPPLEMENT_CHILD', '补录孩子成长复盘',
        'OPERATION', 'WEB', NULL, 250, 'ENABLED',
        '主家长在允许时限内补录活动主关系学生的成长复盘。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646396, 1874244142494646278, 1874244142494646392),
    (1874244142494646397, 1874244142494646277, 1874244142494646393),
    (1874244142494646398, 1874244142494646278, 1874244142494646394),
    (1874244142494646399, 1874244142494646277, 1874244142494646395);
