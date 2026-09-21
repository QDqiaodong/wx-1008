package com.px.base.config;

import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.service.AnchorRankCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 本地验收用幂等种子数据（仅 local profile）。
 * 关键验收样本：A-SF600「只适配强风、最大承重600kg」拿去配微风航线 R-WEAK，
 * 应同时暴露"气流下限不匹配"与"承重达不到强风等级1800"。
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private final AnchorRepository anchorRepository;
    private final FlightRouteRepository routeRepository;
    private final AnchorRankCacheService cacheService;

    @Override
    @Transactional
    public void run(String... args) {
        seedAnchor("A-GOOD-2000", "2000", "0.00", "20.00", "全能型重载锚点");
        seedAnchor("A-MID-900", "900", "1.00", "9.00", "中小风常规锚点");
        seedAnchor("A-SF600", "600", "10.00", "14.00", "只适配强风但承重仅600kg的锚点");
        seedAnchor("A-WEAK400", "400", "0.00", "8.00", "承重不足的微风锚点");
        seedAnchor("A-GALE2600", "2600", "12.00", "25.00", "疾风重载锚点");
        seedAnchor("A-SCARCE-1800", "1800", "0.00", "16.00", "稀缺通用锚点(并发争抢样本)");

        seedRoute("R-WEAK", "晨曦微风线", "东区", "2.00", "微风");
        seedRoute("R-LIGHT", "轻风巡航线", "东区", "4.50", "轻风");
        seedRoute("R-MOD", "和风观景线", "南区", "8.00", "和风");
        seedRoute("R-STRONG", "强风挑战线", "西区", "12.00", "强风");
        seedRoute("R-GALE", "疾风极限线", "北区", "16.00", "疾风");

        // 启动时把全部启用锚点写入 Redis 排序缓存，保证库/缓存基线一致
        List<Anchor> all = anchorRepository.findByStatus(1);
        all.forEach(cacheService::addAnchorRanks);
        log.info("本地种子数据就绪：锚点{}个、航线{}个，Redis排序缓存已全量初始化",
                all.size(), routeRepository.findByStatus(1).size());
    }

    private void seedAnchor(String code, String weight, String minWind, String maxWind, String desc) {
        if (anchorRepository.existsByAnchorCode(code)) return;
        anchorRepository.save(Anchor.builder()
                .anchorCode(code)
                .maxWeight(new BigDecimal(weight))
                .minWindSpeed(new BigDecimal(minWind))
                .maxWindSpeed(new BigDecimal(maxWind))
                .locationDesc(desc)
                .status(1)
                .build());
    }

    private void seedRoute(String code, String name, String group, String wind, String level) {
        if (routeRepository.existsByRouteCode(code)) return;
        routeRepository.save(FlightRoute.builder()
                .routeCode(code)
                .routeName(name)
                .routeGroup(group)
                .windSpeed(new BigDecimal(wind))
                .windLevel(level)
                .description(name)
                .status(1)
                .build());
    }
}
