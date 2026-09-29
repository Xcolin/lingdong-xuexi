-- Provides the shared dictionary source required by FSD-SYS-03.
-- Initial dictionary contents remain configuration data and are not guessed in this migration.
CREATE TABLE sys_dictionary_type (
    id BIGINT NOT NULL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_dictionary_type_code UNIQUE (type_code)
);

CREATE TABLE sys_dictionary_item (
    id BIGINT NOT NULL PRIMARY KEY,
    type_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_dictionary_item_code UNIQUE (type_id, item_code),
    CONSTRAINT fk_sys_dictionary_item_type FOREIGN KEY (type_id) REFERENCES sys_dictionary_type (id)
);

CREATE INDEX idx_sys_dictionary_item_type_status_sort
    ON sys_dictionary_item (type_id, status, sort_order);
