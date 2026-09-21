package com.px.base.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时确保单锚点唯一占用的数据库级约束存在（对历史库幂等补建）。
 * <p>
 * Redis 锚点锁 + 应用层判定是第一道防线；该生成列唯一索引是最后兜底，
 * 即便绕过应用层，也不可能让同一个锚点同时存在两条 status=1 的绑定。
 */
@Component
@ConditionalOnProperty(name = "app.schema.guard", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SchemaGuard implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureGeneratedColumn();
        ensureUniqueIndex();
    }

    private void ensureGeneratedColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'route_anchor' " +
                            "AND COLUMN_NAME = 'active_anchor_id'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE route_anchor " +
                        "ADD COLUMN active_anchor_id BIGINT GENERATED ALWAYS AS " +
                        "(CASE WHEN status = 1 THEN anchor_id ELSE NULL END) VIRTUAL");
                log.info("已为 route_anchor 补建生成列 active_anchor_id");
            }
        } catch (Exception e) {
            log.warn("补建 active_anchor_id 列失败（可能已存在）: {}", e.getMessage());
        }
    }

    private void ensureUniqueIndex() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'route_anchor' " +
                            "AND INDEX_NAME = 'uk_active_anchor'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE route_anchor ADD UNIQUE KEY uk_active_anchor (active_anchor_id)");
                log.info("已为 route_anchor 补建唯一索引 uk_active_anchor（单锚点唯一占用兜底）");
            }
        } catch (Exception e) {
            // 若历史数据存在一个锚点多条生效绑定，建索引会失败，需人工先清理脏数据。
            log.error("补建 uk_active_anchor 唯一索引失败，请先清理重复占用数据: {}", e.getMessage());
        }
    }
}
