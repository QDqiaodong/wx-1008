package com.px.base.service;

import com.px.base.dto.AnchorDTO;
import com.px.base.entity.Anchor;
import com.px.base.repository.AnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnchorService {
    private final AnchorRepository anchorRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_WEIGHT = "anchor:weight";
    private static final String REDIS_KEY_WIND_MIN = "anchor:wind:min";
    private static final String REDIS_KEY_WIND_MAX = "anchor:wind:max";

    @Transactional
    public Anchor create(AnchorDTO dto) {
        if (anchorRepository.existsByAnchorCode(dto.getAnchorCode())) {
            throw new IllegalArgumentException("锚点编号已存在: " + dto.getAnchorCode());
        }
        
        Anchor anchor = Anchor.builder()
                .anchorCode(dto.getAnchorCode())
                .maxWeight(dto.getMaxWeight())
                .minWindSpeed(dto.getMinWindSpeed())
                .maxWindSpeed(dto.getMaxWindSpeed())
                .locationDesc(dto.getLocationDesc())
                .status(1)
                .build();
        
        Anchor saved = anchorRepository.save(anchor);
        updateRedisCache(saved);
        log.info("创建锚点: {}", saved.getAnchorCode());
        return saved;
    }

    @Transactional
    public Anchor update(Long id, AnchorDTO dto) {
        Anchor anchor = anchorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("锚点不存在: " + id));
        
        if (!anchor.getAnchorCode().equals(dto.getAnchorCode()) && 
            anchorRepository.existsByAnchorCode(dto.getAnchorCode())) {
            throw new IllegalArgumentException("锚点编号已存在: " + dto.getAnchorCode());
        }
        
        removeRedisCache(anchor);
        
        anchor.setAnchorCode(dto.getAnchorCode());
        anchor.setMaxWeight(dto.getMaxWeight());
        anchor.setMinWindSpeed(dto.getMinWindSpeed());
        anchor.setMaxWindSpeed(dto.getMaxWindSpeed());
        anchor.setLocationDesc(dto.getLocationDesc());
        
        Anchor saved = anchorRepository.save(anchor);
        updateRedisCache(saved);
        log.info("更新锚点: {}", saved.getAnchorCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Anchor anchor = anchorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("锚点不存在: " + id));
        
        removeRedisCache(anchor);
        anchor.setStatus(0);
        anchorRepository.save(anchor);
        log.info("删除锚点: {}", anchor.getAnchorCode());
    }

    public Optional<Anchor> findById(Long id) {
        return anchorRepository.findById(id);
    }

    public Optional<Anchor> findByCode(String code) {
        return anchorRepository.findByAnchorCode(code);
    }

    public List<Anchor> findAll() {
        return anchorRepository.findAll();
    }

    public List<Anchor> findByStatus(Integer status) {
        return anchorRepository.findByStatus(status);
    }

    public List<Anchor> filterByWindRange(BigDecimal minWind, BigDecimal maxWind) {
        return anchorRepository.findByWindRange(minWind, maxWind);
    }

    public List<Anchor> findAdaptableByWindSpeed(BigDecimal windSpeed) {
        return anchorRepository.findByWindSpeedAdaptable(windSpeed);
    }

    public List<Anchor> findAdaptableByWindSpeedFromRedis(BigDecimal windSpeed) {
        Set<Object> anchorCodes = redisTemplate.opsForZSet().rangeByScore(REDIS_KEY_WIND_MAX, windSpeed.doubleValue(), Double.MAX_VALUE);
        if (anchorCodes == null || anchorCodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Anchor> anchors = new ArrayList<>();
        for (Object code : anchorCodes) {
            anchorRepository.findByAnchorCode((String) code).ifPresent(anchors::add);
        }
        return anchors;
    }

    private void updateRedisCache(Anchor anchor) {
        redisTemplate.opsForZSet().add(REDIS_KEY_WEIGHT, anchor.getAnchorCode(), anchor.getMaxWeight().doubleValue());
        redisTemplate.opsForZSet().add(REDIS_KEY_WIND_MIN, anchor.getAnchorCode(), anchor.getMinWindSpeed().doubleValue());
        redisTemplate.opsForZSet().add(REDIS_KEY_WIND_MAX, anchor.getAnchorCode(), anchor.getMaxWindSpeed().doubleValue());
    }

    private void removeRedisCache(Anchor anchor) {
        redisTemplate.opsForZSet().remove(REDIS_KEY_WEIGHT, anchor.getAnchorCode());
        redisTemplate.opsForZSet().remove(REDIS_KEY_WIND_MIN, anchor.getAnchorCode());
        redisTemplate.opsForZSet().remove(REDIS_KEY_WIND_MAX, anchor.getAnchorCode());
    }

    public void initRedisCache() {
        List<Anchor> anchors = anchorRepository.findByStatus(1);
        anchors.forEach(this::updateRedisCache);
        log.info("初始化Redis缓存, 共{}个锚点", anchors.size());
    }

    /**
     * 对给定锚点的三个排序 ZSET 成员拍快照（含是否存在、旧 score），供提交失败时精确回滚。
     */
    public CacheSnapshot snapshot(List<Anchor> anchors) {
        Map<String, Double> weight = new HashMap<>();
        Map<String, Double> windMin = new HashMap<>();
        Map<String, Double> windMax = new HashMap<>();
        for (Anchor anchor : anchors) {
            String code = anchor.getAnchorCode();
            putScore(weight, code, REDIS_KEY_WEIGHT);
            putScore(windMin, code, REDIS_KEY_WIND_MIN);
            putScore(windMax, code, REDIS_KEY_WIND_MAX);
        }
        return new CacheSnapshot(weight, windMin, windMax);
    }

    private void putScore(Map<String, Double> target, String code, String key) {
        Double score = redisTemplate.opsForZSet().score(key, code);
        if (score != null) {
            target.put(code, score);
        }
    }

    /** 提交成功后把锚点承重与气流排序缓存与数据库写齐（与建档走同一份 ZADD 逻辑）。 */
    public void syncCacheForBind(List<Anchor> anchors) {
        anchors.forEach(this::updateRedisCache);
    }

    /**
     * 提交中途落库失败时调用：仅把“本次方案碰过的锚点”在三个 ZSET 里的成员恢复到快照状态——
     * 原本不存在的成员删掉，原本存在的成员还原旧 score。绝不扫描/删除别的方案或历史已存在的成员，
     * 从而在回退本次脏写的同时，不影响其他锚点，杜绝“缓存有、库里没有”。
     */
    public void restoreCache(CacheSnapshot snapshot, List<Anchor> touched) {
        restore(REDIS_KEY_WEIGHT, snapshot.weight(), touched);
        restore(REDIS_KEY_WIND_MIN, snapshot.windMin(), touched);
        restore(REDIS_KEY_WIND_MAX, snapshot.windMax(), touched);
    }

    private void restore(String key, Map<String, Double> before, List<Anchor> touched) {
        for (Anchor anchor : touched) {
            String code = anchor.getAnchorCode();
            Double oldScore = before.get(code);
            if (oldScore == null) {
                // 提交前不存在该成员：本次是新写入，失败即删除
                redisTemplate.opsForZSet().remove(key, code);
            } else {
                // 提交前已存在：还原旧分数
                redisTemplate.opsForZSet().add(key, code, oldScore);
            }
        }
    }

    /** 三个排序 ZSET 的提交前快照。 */
    public record CacheSnapshot(Map<String, Double> weight,
                                Map<String, Double> windMin,
                                Map<String, Double> windMax) {
    }
}
