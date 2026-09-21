package com.px.base.config;

import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;

/**
 * 演示/验收用种子数据：编号不存在时才补，已存在数据不覆盖。
 * 其中刻意放入：
 * - ANCHOR-W600：只适配强风(10-15)且承重600kg —— 配微风航线会触发“气流下限不匹配”（其承重600≥微风500）；
 * - ANCHOR-W400：只适配强风且承重400kg —— 配微风航线会同时触发“气流下限不匹配”与“承重不足微风等级要求500kg”，
 *   用来直观演示一条锚点同时给出多条具体判定原因。
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Order(20)
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final AnchorRepository anchorRepository;
    private final FlightRouteRepository flightRouteRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedRoutes();
        seedAnchors();
    }

    private void seedRoutes() {
        createRouteIfAbsent("ROUTE-BREEZE", "训练微风航线", "训练组", "2.00", "新手训练，气流平稳");
        createRouteIfAbsent("ROUTE-LIGHT", "常规轻风航线", "常规组", "4.50", "常规观光航线");
        createRouteIfAbsent("ROUTE-MODERATE", "进阶和风航线", "进阶组", "8.00", "进阶训练航线");
        createRouteIfAbsent("ROUTE-STRONG", "专业强风航线", "专业组", "12.00", "专业飞行员航线");
    }

    private void seedAnchors() {
        // 编号, 最大承重, 下限, 上限, 位置
        createAnchorIfAbsent("ANCHOR-A01", 1500, 0, 12, "基地A区-01 通用锚点");
        createAnchorIfAbsent("ANCHOR-A02", 900, 0, 8, "基地A区-02 中小风锚点");
        createAnchorIfAbsent("ANCHOR-B01", 2000, 0, 16, "基地B区-01 加强通用锚点");
        createAnchorIfAbsent("ANCHOR-W600", 600, 10, 15, "基地C区-强风专用600kg");
        createAnchorIfAbsent("ANCHOR-W400", 400, 10, 15, "基地C区-强风专用400kg");
        createAnchorIfAbsent("ANCHOR-G2600", 2600, 0, 20, "基地D区-疾风全风域锚点");
    }

    private void createRouteIfAbsent(String code, String name, String group, String wind, String desc) {
        if (flightRouteRepository.existsByRouteCode(code)) {
            return;
        }
        flightRouteRepository.save(FlightRoute.builder()
                .routeCode(code)
                .routeName(name)
                .routeGroup(group)
                .windSpeed(new BigDecimal(wind))
                .description(desc)
                .status(1)
                .build());
        log.info("种子航线已写入: {}", code);
    }

    private void createAnchorIfAbsent(String code, int weight, int minWind, int maxWind, String location) {
        if (anchorRepository.existsByAnchorCode(code)) {
            return;
        }
        anchorRepository.save(Anchor.builder()
                .anchorCode(code)
                .maxWeight(new BigDecimal(weight))
                .minWindSpeed(new BigDecimal(minWind))
                .maxWindSpeed(new BigDecimal(maxWind))
                .locationDesc(location)
                .status(1)
                .build());
        log.info("种子锚点已写入: {}", code);
    }
}
