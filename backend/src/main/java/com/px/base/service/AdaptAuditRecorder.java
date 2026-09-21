package com.px.base.service;

import com.px.base.entity.AdaptLog;
import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.repository.AdaptLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 适配流水记录器。
 *
 * 用 REQUIRES_NEW 独立事务：成组提交在"被拒 / 并发冲突 / 落库失败整体回滚"时，
 * 业务绑定事务会回滚，但留痕不能跟着丢——每一次配上或被拒都必须在流水里写清是哪条判定没过。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptAuditRecorder {

    public static final String OP_BIND = "BIND";
    public static final String OP_REJECT = "REJECT";
    public static final String OP_CONFLICT = "OCCUPY_CONFLICT";

    private final AdaptLogRepository adaptLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(FlightRoute route, Anchor anchor, String operationType,
                       BigDecimal afterWind, BigDecimal afterWeight, String reason, String operator) {
        AdaptLog entry = AdaptLog.builder()
                .routeId(route.getId())
                .routeCode(route.getRouteCode())
                .anchorId(anchor != null ? anchor.getId() : -1L)
                .anchorCode(anchor != null ? anchor.getAnchorCode() : "-")
                .operationType(operationType)
                .afterWindSpeed(afterWind)
                .afterWeight(afterWeight)
                .reason(reason)
                .operator(operator == null ? "system" : operator)
                .build();
        return adaptLogRepository.save(entry).getId();
    }
}
