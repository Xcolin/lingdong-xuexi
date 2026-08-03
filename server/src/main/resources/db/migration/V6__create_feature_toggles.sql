CREATE TABLE sys_feature_toggle (
    id BIGINT NOT NULL PRIMARY KEY,
    feature_code VARCHAR(64) NOT NULL,
    feature_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    organization_id BIGINT,
    scope_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_feature_toggle_scope UNIQUE (feature_code, scope_key),
    CONSTRAINT fk_sys_feature_toggle_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

INSERT INTO sys_feature_toggle (id, feature_code, feature_name, scope_type, scope_key, status, built_in, description) VALUES
    (1874244142494646284, 'GEO_ATTENDANCE', '地理位置考勤', 'GLOBAL', 'GLOBAL', 'DISABLED', 1, '未成年人定位能力默认关闭。'),
    (1874244142494646285, 'STUDENT_LOCATION_TRACK', '学生轨迹记录', 'GLOBAL', 'GLOBAL', 'DISABLED', 1, '未成年人轨迹记录默认关闭。');
