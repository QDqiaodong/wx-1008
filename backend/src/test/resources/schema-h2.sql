-- H2 (MySQL 兼容模式) 测试库结构，与生产 schema.sql 等价，含单锚点唯一占用的生成列唯一索引。
CREATE TABLE IF NOT EXISTS anchor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anchor_code VARCHAR(50) UNIQUE NOT NULL,
    max_weight DECIMAL(10,2) NOT NULL,
    min_wind_speed DECIMAL(5,2) NOT NULL,
    max_wind_speed DECIMAL(5,2) NOT NULL,
    location_desc VARCHAR(200),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS flight_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_code VARCHAR(50) UNIQUE NOT NULL,
    route_name VARCHAR(100) NOT NULL,
    route_group VARCHAR(50) NOT NULL,
    wind_speed DECIMAL(5,2) NOT NULL,
    wind_level VARCHAR(20),
    description VARCHAR(500),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS route_anchor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    anchor_id BIGINT NOT NULL,
    bind_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    unbind_time DATETIME,
    status TINYINT DEFAULT 1,
    active_anchor_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 1 THEN anchor_id ELSE NULL END),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_route_anchor UNIQUE (route_id, anchor_id),
    CONSTRAINT uk_active_anchor UNIQUE (active_anchor_id)
);

CREATE TABLE IF NOT EXISTS adapt_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    route_code VARCHAR(50) NOT NULL,
    anchor_id BIGINT NOT NULL,
    anchor_code VARCHAR(50) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    before_wind_speed DECIMAL(5,2),
    after_wind_speed DECIMAL(5,2),
    before_weight DECIMAL(10,2),
    after_weight DECIMAL(10,2),
    reason VARCHAR(500),
    operator VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
