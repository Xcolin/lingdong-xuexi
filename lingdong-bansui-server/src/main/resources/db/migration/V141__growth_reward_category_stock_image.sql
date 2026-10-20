-- V141：家庭奖励补列分类、库存与商品图，支撑小程序奖励商城网格与家长表单字段完备。

ALTER TABLE growth_reward
    ADD COLUMN category VARCHAR(24) NULL;

ALTER TABLE growth_reward
    ADD COLUMN stock_quantity INT NOT NULL DEFAULT 0;

ALTER TABLE growth_reward
    ADD COLUMN image_url VARCHAR(500) NULL;

ALTER TABLE growth_reward
    ADD CONSTRAINT ck_growth_reward_category CHECK (
        category IS NULL
        OR category IN ('STUDY_SUPPLIES', 'LIFE_GOODS', 'EXPERIENCE')
    );

ALTER TABLE growth_reward
    ADD CONSTRAINT ck_growth_reward_stock CHECK (stock_quantity >= 0);

UPDATE growth_reward
SET category = 'LIFE_GOODS'
WHERE status <> 'DELETED' AND category IS NULL;
