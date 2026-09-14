package com.px.base.service;

import com.px.base.dto.RouteDTO;
import com.px.base.entity.FlightRoute;
import com.px.base.repository.FlightRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {
    private final FlightRouteRepository routeRepository;
    private final AdaptService adaptService;

    @Transactional
    public FlightRoute create(RouteDTO dto) {
        if (routeRepository.existsByRouteCode(dto.getRouteCode())) {
            throw new IllegalArgumentException("航线编号已存在: " + dto.getRouteCode());
        }
        
        FlightRoute route = FlightRoute.builder()
                .routeCode(dto.getRouteCode())
                .routeName(dto.getRouteName())
                .routeGroup(dto.getRouteGroup())
                .windSpeed(dto.getWindSpeed())
                .windLevel(calculateWindLevel(dto.getWindSpeed()))
                .description(dto.getDescription())
                .status(1)
                .build();
        
        FlightRoute saved = routeRepository.save(route);
        log.info("创建航线: {}", saved.getRouteCode());
        return saved;
    }

    @Transactional
    public FlightRoute update(Long id, RouteDTO dto) {
        FlightRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + id));
        
        if (!route.getRouteCode().equals(dto.getRouteCode()) && 
            routeRepository.existsByRouteCode(dto.getRouteCode())) {
            throw new IllegalArgumentException("航线编号已存在: " + dto.getRouteCode());
        }
        
        BigDecimal oldWindSpeed = route.getWindSpeed();
        
        route.setRouteCode(dto.getRouteCode());
        route.setRouteName(dto.getRouteName());
        route.setRouteGroup(dto.getRouteGroup());
        route.setDescription(dto.getDescription());
        
        if (dto.getWindSpeed() != null && !dto.getWindSpeed().equals(oldWindSpeed)) {
            route.setWindSpeed(dto.getWindSpeed());
            route.setWindLevel(calculateWindLevel(dto.getWindSpeed()));
            
            adaptService.recheckRouteAnchors(id, oldWindSpeed, dto.getWindSpeed());
            log.info("更新航线气流参数, 触发适配校验: {}", route.getRouteCode());
        }
        
        FlightRoute saved = routeRepository.save(route);
        log.info("更新航线: {}", saved.getRouteCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        FlightRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + id));
        
        route.setStatus(0);
        routeRepository.save(route);
        log.info("删除航线: {}", route.getRouteCode());
    }

    public Optional<FlightRoute> findById(Long id) {
        return routeRepository.findById(id);
    }

    public Optional<FlightRoute> findByCode(String code) {
        return routeRepository.findByRouteCode(code);
    }

    public List<FlightRoute> findAll() {
        return routeRepository.findAll();
    }

    public List<FlightRoute> findByStatus(Integer status) {
        return routeRepository.findByStatus(status);
    }

    public List<FlightRoute> findByGroup(String group) {
        return routeRepository.findByRouteGroup(group);
    }

    public List<String> findAllGroups() {
        return routeRepository.findAll().stream()
                .map(FlightRoute::getRouteGroup)
                .distinct()
                .collect(Collectors.toList());
    }

    private String calculateWindLevel(BigDecimal windSpeed) {
        if (windSpeed == null) return "微风";
        double speed = windSpeed.doubleValue();
        if (speed < 3) return "微风";
        if (speed < 6) return "轻风";
        if (speed < 10) return "和风";
        if (speed < 15) return "强风";
        return "疾风";
    }
}
