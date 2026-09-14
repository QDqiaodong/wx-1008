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
import java.util.List;
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
}
