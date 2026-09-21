package com.px.base.service;

import com.px.base.entity.AdaptLog;
import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.entity.RouteAnchor;
import com.px.base.repository.AdaptLogRepository;
import com.px.base.repository.RouteAnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配桩方案的落库操作，独立成 Bean 并显式声明事务边界：
 * <ul>
 *     <li>{@link #persistBindings} 整套绑定 + BIND 流水在一个事务内，失败整体回滚，一条都不落；</li>
 *     <li>{@link #saveRejectLogs} 被拒流水使用独立新事务提交，
 *     保证即便主流程因落库失败回滚，“被拒留痕”仍然可靠写入。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PilePlanPersister {

    private final RouteAnchorRepository routeAnchorRepository;
    private final AdaptLogRepository adaptLogRepository;

    /**
     * 整套方案的绑定与 BIND 流水在同一事务内全部成功或全部回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BindOutcome persistBindings(FlightRoute route, List<Anchor> anchors, String operator) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> bindIds = new ArrayList<>();
        List<Long> logIds = new ArrayList<>();

        for (Anchor anchor : anchors) {
            RouteAnchor routeAnchor;
            Optional<RouteAnchor> existing =
                    routeAnchorRepository.findByRouteIdAndAnchorId(route.getId(), anchor.getId());
            if (existing.isPresent()) {
                routeAnchor = existing.get();
                routeAnchor.setStatus(1);
                routeAnchor.setUnbindTime(null);
                routeAnchor.setBindTime(now);
            } else {
                routeAnchor = RouteAnchor.builder()
                        .routeId(route.getId())
                        .anchorId(anchor.getId())
                        .status(1)
                        .bindTime(now)
                        .build();
            }
            bindIds.add(routeAnchorRepository.save(routeAnchor).getId());

            String reason = String.format(
                    "成组配桩成功：锚点%s适配气流区间[%.2f-%.2f]包住航线气流%.2fm/s（%s），承重%.0f≥等级最低承重%dkg",
                    anchor.getAnchorCode(),
                    anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(),
                    route.getWindSpeed(),
                    com.px.base.rule.WindLevel.fromSpeed(route.getWindSpeed()).getLabel(),
                    anchor.getMaxWeight(),
                    com.px.base.rule.WindLevel.fromSpeed(route.getWindSpeed()).getMinWeight());

            AdaptLog logEntry = AdaptLog.builder()
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .anchorId(anchor.getId())
                    .anchorCode(anchor.getAnchorCode())
                    .operationType("BIND")
                    .afterWindSpeed(route.getWindSpeed())
                    .afterWeight(anchor.getMaxWeight())
                    .reason(reason)
                    .operator(operator)
                    .build();
            logIds.add(adaptLogRepository.save(logEntry).getId());
        }
        return new BindOutcome(bindIds, logIds);
    }

    /** 落库结果：绑定关系ID与对应流水ID。 */
    public record BindOutcome(List<Long> bindIds, List<Long> logIds) {
    }

    /**
     * 被拒流水独立事务提交。配上或被拒都在适配流水里留痕，且写清是哪条判定没过。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> saveRejectLogs(FlightRoute route, List<RejectEntry> rejects, String operator) {
        List<Long> logIds = new ArrayList<>();
        for (RejectEntry reject : rejects) {
            AdaptLog logEntry = AdaptLog.builder()
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .anchorId(reject.anchorId())
                    .anchorCode(reject.anchorCode())
                    .operationType("REJECT")
                    .afterWindSpeed(route.getWindSpeed())
                    .reason(reject.reason())
                    .operator(operator)
                    .build();
            logIds.add(adaptLogRepository.save(logEntry).getId());
        }
        return logIds;
    }
}
