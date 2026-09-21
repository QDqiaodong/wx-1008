package com.px.base.service;

import com.px.base.entity.Anchor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 锚点承重 / 气流 Redis 排序缓存服务（SortedSet）。
 *
 * 与既有 AnchorService 使用同一套 key 与 member（锚点编号）：
 *   anchor:weight / anchor:wind:min / anchor:wind:max
 *
 * 关键：成组提交时先对将要写入的 member 做快照（写前分值集合），
 * 一旦后续落库失败，用 {@link #compensate} 按快照逐条恢复，
 * 已写进缓存的那几条一起回退，杜绝"缓存有、库里没有"的脏数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnchorRankCacheService {

    public static final String KEY_WEIGHT = "anchor:weight";
    public static final String KEY_WIND_MIN = "anchor:wind:min";
    public static final String KEY_WIND_MAX = "anchor:wind:max";

    private static final String[] KEYS = {KEY_WEIGHT, KEY_WIND_MIN, KEY_WIND_MAX};

    private final RedisTemplate<String, Object> redisTemplate;

    /** 单个 member 在三个 ZSet 上的写前状态 */
    public record MemberSnapshot(String code, Double weight, Double windMin, Double windMax, boolean existed) {
    }

    /** 写入前抓取快照（用于失败补偿） */
    public List<MemberSnapshot> snapshot(List<Anchor> anchors) {
        List<MemberSnapshot> snaps = new ArrayList<>();
        for (Anchor a : anchors) {
            String code = a.getAnchorCode();
            Double w = score(KEY_WEIGHT, code);
            Double min = score(KEY_WIND_MIN, code);
            Double max = score(KEY_WIND_MAX, code);
            boolean existed = w != null || min != null || max != null;
            snaps.add(new MemberSnapshot(code, w, min, max, existed));
        }
        return snaps;
    }

    private Double score(String key, String member) {
        try {
            return redisTemplate.opsForZSet().score(key, member);
        } catch (Exception e) {
            log.warn("读取Redis快照失败 key={} member={}: {}", key, member, e.getMessage());
            return null;
        }
    }

    /** 将锚点承重/气流写入三个排序缓存（幂等：同 member 覆盖分值） */
    public void addAnchorRanks(Anchor anchor) {
        redisTemplate.opsForZSet().add(KEY_WEIGHT, anchor.getAnchorCode(), anchor.getMaxWeight().doubleValue());
        redisTemplate.opsForZSet().add(KEY_WIND_MIN, anchor.getAnchorCode(), anchor.getMinWindSpeed().doubleValue());
        redisTemplate.opsForZSet().add(KEY_WIND_MAX, anchor.getAnchorCode(), anchor.getMaxWindSpeed().doubleValue());
    }

    /**
     * 按写前快照补偿回退：
     *  - 写前不存在的 member：从三个 ZSet 移除（撤销本次新增）
     *  - 写前已存在的 member：恢复写前分值
     */
    public void compensate(List<MemberSnapshot> snapshots) {
        for (MemberSnapshot s : snapshots) {
            try {
                if (!s.existed()) {
                    for (String key : KEYS) {
                        redisTemplate.opsForZSet().remove(key, s.code());
                    }
                } else {
                    if (s.weight() != null) redisTemplate.opsForZSet().add(KEY_WEIGHT, s.code(), s.weight());
                    else redisTemplate.opsForZSet().remove(KEY_WEIGHT, s.code());
                    if (s.windMin() != null) redisTemplate.opsForZSet().add(KEY_WIND_MIN, s.code(), s.windMin());
                    else redisTemplate.opsForZSet().remove(KEY_WIND_MIN, s.code());
                    if (s.windMax() != null) redisTemplate.opsForZSet().add(KEY_WIND_MAX, s.code(), s.windMax());
                    else redisTemplate.opsForZSet().remove(KEY_WIND_MAX, s.code());
                }
            } catch (Exception e) {
                log.error("缓存补偿失败 member={}，需人工核对: {}", s.code(), e.getMessage(), e);
            }
        }
        log.warn("已按快照回退 Redis 排序缓存，共{}条", snapshots.size());
    }

    /** 校验用：返回某 member 在三个 ZSet 是否存在（与数据库一致性核对） */
    public boolean existsInCache(String code) {
        for (String key : KEYS) {
            Double sc = score(key, code);
            if (sc != null) return true;
        }
        return false;
    }

    public Set<Object> rangeByMaxWind(double minScore) {
        return redisTemplate.opsForZSet().rangeByScore(KEY_WIND_MAX, minScore, Double.MAX_VALUE);
    }
}
