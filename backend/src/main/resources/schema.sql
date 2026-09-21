SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

CREATE TABLE IF NOT EXISTS anchor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anchor_code VARCHAR(50) UNIQUE NOT NULL COMMENT '锚点编号',
    max_weight DECIMAL(10,2) NOT NULL COMMENT '最大承重(kg)',
    min_wind_speed DECIMAL(5,2) NOT NULL COMMENT '适配气流下限(m/s)',
    max_wind_speed DECIMAL(5,2) NOT NULL COMMENT '适配气流上限(m/s)',
    location_desc VARCHAR(200) COMMENT '位置描述',
    status TINYINT DEFAULT 1 COMMENT '状态:0-停用,1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_anchor_code (anchor_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地面固定锚点表';

CREATE TABLE IF NOT EXISTS flight_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_code VARCHAR(50) UNIQUE NOT NULL COMMENT '航线编号',
    route_name VARCHAR(100) NOT NULL COMMENT '航线名称',
    route_group VARCHAR(50) NOT NULL COMMENT '航线分组',
    wind_speed DECIMAL(5,2) NOT NULL COMMENT '当前气流强度(m/s)',
    wind_level VARCHAR(20) COMMENT '气流等级',
    description VARCHAR(500) COMMENT '航线描述',
    status TINYINT DEFAULT 1 COMMENT '状态:0-停用,1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_route_code (route_code),
    INDEX idx_route_group (route_group),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动力伞航线表';

CREATE TABLE IF NOT EXISTS route_anchor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL COMMENT '航线ID',
    anchor_id BIGINT NOT NULL COMMENT '锚点ID',
    bind_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    unbind_time DATETIME COMMENT '解绑时间',
    status TINYINT DEFAULT 1 COMMENT '状态:0-解绑,1-绑定',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_route_id (route_id),
    INDEX idx_anchor_id (anchor_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_route_anchor (route_id, anchor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航线锚点绑定表';

-- 锚点唯一主占表：anchor_id 作主键，从数据库层强制"一个锚点同一时间只服役一条启用航线"
CREATE TABLE IF NOT EXISTS anchor_occupancy (
    anchor_id BIGINT PRIMARY KEY COMMENT '锚点ID(主键即唯一占用约束)',
    route_id BIGINT NOT NULL COMMENT '当前服役航线ID',
    route_code VARCHAR(50) NOT NULL COMMENT '当前服役航线编号',
    bind_id BIGINT COMMENT '对应绑定关系ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '占用时间',
    INDEX idx_occ_route_id (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='锚点唯一主占表';

CREATE TABLE IF NOT EXISTS adapt_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL COMMENT '航线ID',
    route_code VARCHAR(50) NOT NULL COMMENT '航线编号',
    anchor_id BIGINT NOT NULL COMMENT '锚点ID',
    anchor_code VARCHAR(50) NOT NULL COMMENT '锚点编号',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型:BIND/UNBIND/REBIND',
    before_wind_speed DECIMAL(5,2) COMMENT '操作前气流强度',
    after_wind_speed DECIMAL(5,2) COMMENT '操作后气流强度',
    before_weight DECIMAL(10,2) COMMENT '操作前锚点承重',
    after_weight DECIMAL(10,2) COMMENT '操作后锚点承重',
    reason VARCHAR(500) COMMENT '操作原因',
    operator VARCHAR(50) COMMENT '操作人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_route_id (route_id),
    INDEX idx_anchor_id (anchor_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='适配调整流水表';
