package com.px.base.service;

import com.px.base.dto.AdaptResultDTO;
import com.px.base.entity.AdaptLog;
import com.px.base.entity.Anchor;
import com.px.base.entity.AnchorOccupancy;
import com.px.base.entity.FlightRoute;
import com.px.base.entity.RouteAnchor;
import com.px.base.repository.AdaptLogRepository;
import com.px.base.repository.AnchorOccupancyRepository;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.repository.RouteAnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptService {
    private final RouteAnchorRepository routeAnchorRepository;
    private final AnchorRepository anchorRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final AdaptLogRepository adaptLogRepository;
    private final AnchorOccupancyRepository anchorOccupancyRepository;

    @Transactional
    public AdaptResultDTO bindAnchor(Long routeId, Long anchorId) {
        FlightRoute route = flightRouteRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + routeId));
        
        Anchor anchor = anchorRepository.findById(anchorId)
                .orElseThrow(() -> new IllegalArgumentException("锚点不存在: " + anchorId));
        
        if (route.getStatus() != 1) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason("航线已停用")
                    .build();
        }
        
        if (anchor.getStatus() != 1) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason("锚点已停用")
                    .build();
        }
        
        if (routeAnchorRepository.existsByRouteIdAndAnchorIdAndStatus(routeId, anchorId, 1)) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason("锚点已绑定该航线")
                    .build();
        }

        // 单锚点唯一占用：已被别的启用航线主占则拒绝（与成组配桩同一约束，旧入口也不能绕过）
        Optional<AnchorOccupancy> occupied = anchorOccupancyRepository.findByAnchorIdForUpdate(anchorId);
        if (occupied.isPresent() && !occupied.get().getRouteId().equals(routeId)) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason(String.format("唯一占用冲突：锚点%s已被启用航线%s主占，同一时间不能再配给本航线",
                            anchor.getAnchorCode(), occupied.get().getRouteCode()))
                    .build();
        }
        
        boolean valid = validateAdapt(route, anchor);
        
        if (!valid) {
            String reason = String.format("锚点%s适配气流上限%.2fm/s小于航线气流强度%.2fm/s，禁止绑定",
                    anchor.getAnchorCode(), anchor.getMaxWindSpeed(), route.getWindSpeed());
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason(reason)
                    .build();
        }
        
        RouteAnchor routeAnchor;
        Optional<RouteAnchor> existing = routeAnchorRepository.findByRouteIdAndAnchorId(routeId, anchorId);
        if (existing.isPresent()) {
            routeAnchor = existing.get();
            routeAnchor.setStatus(1);
            routeAnchor.setUnbindTime(null);
            routeAnchor.setBindTime(LocalDateTime.now());
        } else {
            routeAnchor = RouteAnchor.builder()
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .status(1)
                    .build();
        }
        
        RouteAnchor saved = routeAnchorRepository.save(routeAnchor);

        // 同步主占表（主键=锚点ID），保证唯一占用在两个入口都成立
        AnchorOccupancy occupancy = anchorOccupancyRepository.findById(anchorId)
                .orElseGet(() -> AnchorOccupancy.builder().anchorId(anchorId).build());
        occupancy.setRouteId(routeId);
        occupancy.setRouteCode(route.getRouteCode());
        occupancy.setBindId(saved.getId());
        anchorOccupancyRepository.save(occupancy);
        
        String reason = String.format("锚点%s适配气流区间[%.2f-%.2f]覆盖航线气流强度%.2fm/s",
                anchor.getAnchorCode(), anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(), route.getWindSpeed());
        
        AdaptLog logEntry = AdaptLog.builder()
                .routeId(routeId)
                .routeCode(route.getRouteCode())
                .anchorId(anchorId)
                .anchorCode(anchor.getAnchorCode())
                .operationType("BIND")
                .afterWindSpeed(route.getWindSpeed())
                .afterWeight(anchor.getMaxWeight())
                .reason(reason)
                .operator("system")
                .build();
        
        adaptLogRepository.save(logEntry);
        
        log.info("绑定锚点: 航线{} - 锚点{}", route.getRouteCode(), anchor.getAnchorCode());
        
        return AdaptResultDTO.builder()
                .valid(true)
                .routeId(routeId)
                .anchorId(anchorId)
                .bindId(saved.getId())
                .reason(reason)
                .build();
    }

    @Transactional
    public AdaptResultDTO unbindAnchor(Long routeId, Long anchorId) {
        Optional<RouteAnchor> existing = routeAnchorRepository.findByRouteIdAndAnchorId(routeId, anchorId);
        
        if (existing.isEmpty()) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason("绑定关系不存在")
                    .build();
        }
        
        RouteAnchor routeAnchor = existing.get();
        
        if (routeAnchor.getStatus() != 1) {
            return AdaptResultDTO.builder()
                    .valid(false)
                    .routeId(routeId)
                    .anchorId(anchorId)
                    .reason("绑定关系已解绑")
                    .build();
        }
        
        FlightRoute route = flightRouteRepository.findById(routeId).orElse(null);
        Anchor anchor = anchorRepository.findById(anchorId).orElse(null);
        
        routeAnchor.setStatus(0);
        routeAnchor.setUnbindTime(LocalDateTime.now());
        routeAnchorRepository.save(routeAnchor);

        // 释放主占（仅当主占确属该航线时才删，避免误删已被他人重新占用的记录）
        anchorOccupancyRepository.findById(anchorId).ifPresent(o -> {
            if (o.getRouteId().equals(routeId)) {
                anchorOccupancyRepository.deleteById(anchorId);
            }
        });
        
        String reason = "手动解绑";
        if (route != null && anchor != null) {
            reason = String.format("手动解绑锚点%s", anchor.getAnchorCode());
        }
        
        AdaptLog logEntry = AdaptLog.builder()
                .routeId(routeId)
                .routeCode(route != null ? route.getRouteCode() : "")
                .anchorId(anchorId)
                .anchorCode(anchor != null ? anchor.getAnchorCode() : "")
                .operationType("UNBIND")
                .beforeWindSpeed(route != null ? route.getWindSpeed() : null)
                .beforeWeight(anchor != null ? anchor.getMaxWeight() : null)
                .reason(reason)
                .operator("system")
                .build();
        
        adaptLogRepository.save(logEntry);
        
        log.info("解绑锚点: 航线{} - 锚点{}", route != null ? route.getRouteCode() : routeId, 
                anchor != null ? anchor.getAnchorCode() : anchorId);
        
        return AdaptResultDTO.builder()
                .valid(true)
                .routeId(routeId)
                .anchorId(anchorId)
                .bindId(routeAnchor.getId())
                .reason(reason)
                .build();
    }

    public AdaptResultDTO checkAdapt(Long routeId, Long anchorId) {
        FlightRoute route = flightRouteRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + routeId));
        
        Anchor anchor = anchorRepository.findById(anchorId)
                .orElseThrow(() -> new IllegalArgumentException("锚点不存在: " + anchorId));
        
        boolean valid = validateAdapt(route, anchor);
        
        String reason;
        if (valid) {
            reason = String.format("锚点%s适配气流区间[%.2f-%.2f]覆盖航线气流强度%.2fm/s",
                    anchor.getAnchorCode(), anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(), route.getWindSpeed());
        } else {
            reason = String.format("锚点%s适配气流上限%.2fm/s小于航线气流强度%.2fm/s",
                    anchor.getAnchorCode(), anchor.getMaxWindSpeed(), route.getWindSpeed());
        }
        
        return AdaptResultDTO.builder()
                .valid(valid)
                .routeId(routeId)
                .anchorId(anchorId)
                .reason(reason)
                .build();
    }

    @Transactional
    public AdaptResultDTO recheckRouteAnchors(Long routeId, BigDecimal oldWindSpeed, BigDecimal newWindSpeed) {
        FlightRoute route = flightRouteRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + routeId));
        
        BigDecimal currentWindSpeed = route.getWindSpeed();
        if (newWindSpeed == null) {
            newWindSpeed = currentWindSpeed;
        }
        if (oldWindSpeed == null) {
            oldWindSpeed = currentWindSpeed;
        }
        
        List<RouteAnchor> boundAnchors = routeAnchorRepository.findByRouteIdAndStatus(routeId, 1);
        
        int unbindCount = 0;
        int rebindCount = 0;
        List<Long> logIds = new ArrayList<>();
        
        for (RouteAnchor routeAnchor : boundAnchors) {
            Anchor anchor = anchorRepository.findById(routeAnchor.getAnchorId()).orElse(null);
            if (anchor == null) continue;
            
            boolean valid = validateAdapt(route, anchor);
            
            if (!valid) {
                routeAnchor.setStatus(0);
                routeAnchor.setUnbindTime(LocalDateTime.now());
                routeAnchorRepository.save(routeAnchor);

                // 自动解绑同样释放主占
                Long anchorId = anchor.getId();
                anchorOccupancyRepository.findById(anchorId).ifPresent(o -> {
                    if (o.getRouteId().equals(routeId)) {
                        anchorOccupancyRepository.deleteById(anchorId);
                    }
                });
                
                String reason = String.format("航线气流参数更新，锚点%s适配气流上限%.2fm/s小于新气流强度%.2fm/s，自动解绑",
                        anchor.getAnchorCode(), anchor.getMaxWindSpeed(), newWindSpeed);
                
                AdaptLog logEntry = AdaptLog.builder()
                        .routeId(routeId)
                        .routeCode(route.getRouteCode())
                        .anchorId(anchor.getId())
                        .anchorCode(anchor.getAnchorCode())
                        .operationType("UNBIND")
                        .beforeWindSpeed(oldWindSpeed)
                        .afterWindSpeed(newWindSpeed)
                        .beforeWeight(anchor.getMaxWeight())
                        .afterWeight(anchor.getMaxWeight())
                        .reason(reason)
                        .operator("system")
                        .build();
                
                AdaptLog savedLog = adaptLogRepository.save(logEntry);
                logIds.add(savedLog.getId());
                unbindCount++;
                
                log.info("自动解绑锚点: 航线{} - 锚点{}, 原因: {}", route.getRouteCode(), anchor.getAnchorCode(), reason);
            }
        }
        
        return AdaptResultDTO.builder()
                .valid(true)
                .routeId(routeId)
                .rebindCount(rebindCount)
                .unbindCount(unbindCount)
                .logIds(logIds)
                .reason(String.format("重新校验完成，共解绑%d个不适配锚点", unbindCount))
                .build();
    }

    private boolean validateAdapt(FlightRoute route, Anchor anchor) {
        return anchor.getMaxWindSpeed().compareTo(route.getWindSpeed()) >= 0;
    }

    public List<RouteAnchor> getBoundAnchors(Long routeId) {
        return routeAnchorRepository.findByRouteIdAndStatus(routeId, 1);
    }

    public List<RouteAnchor> getBoundRoutes(Long anchorId) {
        return routeAnchorRepository.findByAnchorIdAndStatus(anchorId, 1);
    }

    public List<AdaptLog> getLogs(Long routeId) {
        if (routeId == null) {
            return adaptLogRepository.findAll();
        }
        return adaptLogRepository.findByRouteId(routeId);
    }

    public List<AdaptLog> getLogsByAnchor(Long anchorId) {
        return adaptLogRepository.findByAnchorId(anchorId);
    }

    public Page<AdaptLog> getLogsPage(Long routeId, Pageable pageable) {
        if (routeId != null) {
            return adaptLogRepository.findByRouteId(routeId, pageable);
        }
        return adaptLogRepository.findAll(pageable);
    }
}
