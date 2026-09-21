package com.px.base.service;

import com.px.base.dto.GroupAnchorResultDTO;
import com.px.base.dto.GroupBindRequestDTO;
import org.springframework.transaction.support.TransactionTemplate;
import com.px.base.dto.GroupRehearseResultDTO;
import com.px.base.dto.GroupSubmitResultDTO;
import com.px.base.entity.Anchor;
import com.px.base.entity.AnchorOccupancy;
import com.px.base.entity.FlightRoute;
import com.px.base.entity.RouteAnchor;
import com.px.base.repository.AnchorOccupancyRepository;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.repository.RouteAnchorRepository;
import com.px.base.rule.GroupBindingEvaluator;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 航线成组配桩：预演体检 + 提交落库。
 *
 * 一致性要点：
 *  - 预演 {@link #rehearse} 与提交 {@link #submit} 都只调用 {@link GroupBindingEvaluator#evaluate}，同一套判定；
 *  - 策略固定 ALL_OR_NOTHING：有任一锚点不合格，整套回退、一条不落；
 *  - 并发：按 anchorId 升序对锚点行加悲观锁串行化，配合 anchor_occupancy 主键唯一约束，
 *    两个运营抢同一稀缺锚点时只有一条方案落库，另一条得到 OCCUPIED 冲突；
 *  - 缓存：提交逐条落库后写 Redis 排序缓存；任一步落库失败，DB 事务回滚 + 按写前快照补偿缓存，杜绝脏数据；
 *  - 留痕：配上写 BIND；被拒/冲突用独立事务写 REJECT / OCCUPY_CONFLICT，业务回滚也不丢痕。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupBindService {

    private final FlightRouteRepository flightRouteRepository;
    private final AnchorRepository anchorRepository;
    private final RouteAnchorRepository routeAnchorRepository;
    private final AnchorOccupancyRepository occupancyRepository;
    private final GroupBindingEvaluator evaluator;
    private final AnchorRankCacheService cacheService;
    private final AdaptAuditRecorder auditRecorder;
    private final PlatformTransactionManager transactionManager;
    private final com.px.base.repository.AdaptLogRepository adaptLogRepository;

    /**
     * 验收用故障注入：当提交方案中包含该编号的锚点，并且它已写库写缓存后，
     * 人为抛出"落库失败"，以验证 DB 回滚与 Redis 缓存补偿。生产默认空串=关闭。
     */
    @Value("${px.binding.fault-after-cache-anchor-code:}")
    private String faultAfterCacheAnchorCode;

    /** 仅预演，不写任何东西。占用读取为普通快照读。 */
    public GroupRehearseResultDTO rehearse(GroupBindRequestDTO req) {
        FlightRoute route = loadEnabledRoute(req.getRouteId());
        List<Anchor> anchors = loadAnchorsOrdered(req.getAnchorIds());
        return evaluator.evaluate(route, anchors,
                id -> occupancyRepository.findByAnchorId(id).map(o -> o));
    }

    /** 提交：在单一数据库事务内完成"加锁→同一引擎判定→整套落库或整套拒绝"。 */
    public GroupSubmitResultDTO submit(GroupBindRequestDTO req) {
        FlightRoute route = loadEnabledRoute(req.getRouteId());
        if (req.getAnchorIds() == null || req.getAnchorIds().isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一个地面锚点再提交");
        }
        String operator = req.getOperator() == null || req.getOperator().isBlank() ? "operator" : req.getOperator();

        // 写缓存前先抓快照，落库失败时按它补偿
        List<Anchor> anchorsAtStart = loadAnchorsOrdered(req.getAnchorIds());
        List<AnchorRankCacheService.MemberSnapshot> snapshots = cacheService.snapshot(anchorsAtStart);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TxOutcome outcome;
        try {
            outcome = tx.execute(status -> doInTransaction(route, anchorsAtStart, operator));
        } catch (SimulatedSubmitFailure ex) {
            // 人为注入的落库失败：DB 已随事务回滚，按快照把已写进缓存的几条一起补偿回退
            log.warn("成组提交落库失败，触发DB回滚与缓存补偿: {}", ex.getMessage());
            cacheService.compensate(snapshots);
            safeRecordFailure(route, anchorsAtStart, ex, operator);
            return GroupSubmitResultDTO.builder()
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .committed(false)
                    .bindIds(List.of())
                    .submittedCount(0)
                    .message("提交失败，整套方案已回退（数据库与Redis缓存均未保留半成品）：" + ex.getMessage())
                    .build();
        } catch (DataIntegrityViolationException | PessimisticLockException ex) {
            // 并发兜底：唯一约束/锁冲突。DB 已回滚、缓存按快照补偿；再用同一引擎按最新占用复判，
            // 给出明确"占用冲突"逐条原因，绝不能让两条都落库或只抛一个笼统错误。
            log.warn("成组提交并发冲突，触发DB回滚与缓存补偿: {}", ex.getMessage());
            cacheService.compensate(snapshots);
            GroupRehearseResultDTO conflictView = evaluator.evaluate(route, anchorsAtStart,
                    id -> occupancyRepository.findByAnchorId(id));
            recordConflictLogs(route, anchorsAtStart, conflictView, operator);
            String occMsg = summarizeOccupancy(conflictView);
            return GroupSubmitResultDTO.builder()
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .committed(false)
                    .bindIds(List.of())
                    .submittedCount(0)
                    .rehearsal(conflictView)
                    .message("并发占用冲突，整套方案已回退、一条未落库：" + occMsg)
                    .build();
        }

        if (!outcome.committed) {
            return GroupSubmitResultDTO.builder()
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .committed(false)
                    .bindIds(List.of())
                    .submittedCount(0)
                    .rehearsal(outcome.rehearsal)
                    .logIds(outcome.logIds)
                    .message("预演体检未通过，按【整套全成或全回退】策略，一条都未落库，请按下方逐条原因调整后重提。")
                    .build();
        }

        return GroupSubmitResultDTO.builder()
                .routeId(route.getId())
                .routeCode(route.getRouteCode())
                .committed(true)
                .bindIds(outcome.bindIds)
                .submittedCount(outcome.bindIds.size())
                .rehearsal(outcome.rehearsal)
                .logIds(outcome.logIds)
                .message(String.format("整套方案已整体落库：%d 个锚点全部绑定成功，Redis承重/气流排序缓存已与数据库保持一致。",
                        outcome.bindIds.size()))
                .build();
    }

    private TxOutcome doInTransaction(FlightRoute route, List<Anchor> anchors, String operator) {
        // 1) 按 anchorId 升序对锚点行加悲观锁：保证并发方案对同一锚点串行，避免死锁（两边加锁顺序一致）
        List<Long> orderedIds = anchors.stream().filter(a -> a != null).map(Anchor::getId)
                .sorted(Comparator.naturalOrder()).toList();
        for (Long id : orderedIds) {
            anchorRepository.findByIdForUpdate(id); // 命中行锁即排队等待
        }

        // 2) 同一套判定引擎；占用读取走 FOR UPDATE，拿到的是锁内最新主占
        GroupRehearseResultDTO rehearsal = evaluator.evaluate(route, anchors, this::loadOccupancyForUpdate);

        // 3) 有任一不合格 -> 整套拒绝、一条不落；逐条留痕（独立事务，不随本次提交回滚丢失）
        if (!rehearsal.isGroupValid()) {
            List<Long> rejectLogIds = new ArrayList<>();
            for (int i = 0; i < anchors.size(); i++) {
                Anchor a = anchors.get(i);
                GroupAnchorResultDTO r = rehearsal.getAnchorResults().get(i);
                if (a == null || r.isEligible()) continue;
                boolean conflict = r.getFailedChecks().contains(GroupBindingEvaluator.OCCUPIED);
                String op = conflict ? AdaptAuditRecorder.OP_CONFLICT : AdaptAuditRecorder.OP_REJECT;
                String reason = "成组配桩被拒[" + String.join(",", r.getFailedChecks()) + "]："
                        + String.join("；", r.getReasons());
                rejectLogIds.add(auditRecorder.record(route, a, op, route.getWindSpeed(),
                        a.getMaxWeight(), truncate(reason), operator));
            }
            log.info("成组配桩整套拒绝: 航线{} 不合格{}/{}", route.getRouteCode(),
                    rehearsal.getRejectedCount(), rehearsal.getTotalCount());
            return new TxOutcome(false, List.of(), rejectLogIds, rehearsal);
        }

        // 4) 全部合格：逐条落库（绑定关系 + 唯一主占 + BIND流水），随即写该锚点缓存
        List<Long> bindIds = new ArrayList<>();
        List<Long> bindLogIds = new ArrayList<>();
        for (Anchor anchor : anchors) {
            RouteAnchor ra = routeAnchorRepository.findByRouteIdAndAnchorId(route.getId(), anchor.getId())
                    .orElseGet(() -> RouteAnchor.builder().routeId(route.getId()).anchorId(anchor.getId()).build());
            ra.setStatus(1);
            ra.setUnbindTime(null);
            ra.setBindTime(LocalDateTime.now());
            RouteAnchor savedBind = routeAnchorRepository.saveAndFlush(ra);
            bindIds.add(savedBind.getId());

            AnchorOccupancy occ = AnchorOccupancy.builder()
                    .anchorId(anchor.getId())
                    .routeId(route.getId())
                    .routeCode(route.getRouteCode())
                    .bindId(savedBind.getId())
                    .build();
            occupancyRepository.saveAndFlush(occ); // 撞主键/唯一约束 -> 并发冲突，整个事务回滚

            String reason = String.format("成组配桩成功：锚点%s区间[%.2f-%.2f]包住%s气流%.2f，承重%.0fkg达标且未被占用",
                    anchor.getAnchorCode(), anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(),
                    rehearsal.getWindLevel(), route.getWindSpeed(), anchor.getMaxWeight());
            com.px.base.entity.AdaptLog bindLog = com.px.base.entity.AdaptLog.builder()
                    .routeId(route.getId()).routeCode(route.getRouteCode())
                    .anchorId(anchor.getId()).anchorCode(anchor.getAnchorCode())
                    .operationType(AdaptAuditRecorder.OP_BIND)
                    .afterWindSpeed(route.getWindSpeed()).afterWeight(anchor.getMaxWeight())
                    .reason(truncate(reason)).operator(operator)
                    .build();
            bindLogIds.add(adaptLogRepository.saveAndFlush(bindLog).getId());

            cacheService.addAnchorRanks(anchor); // 落库成功后写缓存；后续若失败由外层按快照补偿

            // 故障注入：本锚点已写库写缓存后人为失败，验证"半成品"回滚
            if (faultAfterCacheAnchorCode != null && !faultAfterCacheAnchorCode.isBlank()
                    && faultAfterCacheAnchorCode.equals(anchor.getAnchorCode())) {
                throw new SimulatedSubmitFailure("模拟落库失败：锚点" + anchor.getAnchorCode()
                        + "写库写缓存后后续步骤失败，应整体回滚并补偿缓存");
            }
        }

            // bindIds 仅放绑定关系ID；流水ID走 logIds，二者不混
            return new TxOutcome(true, List.copyOf(bindIds), List.copyOf(bindLogIds), rehearsal);
    }

    private Optional<AnchorOccupancy> loadOccupancyForUpdate(Long anchorId) {
        return occupancyRepository.findByAnchorIdForUpdate(anchorId);
    }

    private void safeRecordFailure(FlightRoute route, List<Anchor> anchors, RuntimeException ex, String operator) {
        for (Anchor a : anchors) {
            if (a == null) continue;
            try {
                auditRecorder.record(route, a, AdaptAuditRecorder.OP_REJECT, route.getWindSpeed(),
                        a.getMaxWeight(), truncate("成组提交落库失败已整体回退并补偿缓存：" + ex.getMessage()), operator);
            } catch (Exception ignore) {
                log.warn("失败留痕异常 anchor={}", a.getAnchorCode());
            }
        }
    }

    /** 唯一约束/锁兜底路径下，对被判为占用冲突的锚点补写 OCCUPY_CONFLICT 流水（独立事务） */
    private void recordConflictLogs(FlightRoute route, List<Anchor> anchors,
                                    GroupRehearseResultDTO view, String operator) {
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            GroupAnchorResultDTO r = view.getAnchorResults().get(i);
            if (a == null || r.isEligible()) continue;
            String op = r.getFailedChecks().contains(GroupBindingEvaluator.OCCUPIED)
                    ? AdaptAuditRecorder.OP_CONFLICT : AdaptAuditRecorder.OP_REJECT;
            String reason = "并发提交冲突回退[" + String.join(",", r.getFailedChecks()) + "]："
                    + String.join("；", r.getReasons());
            try {
                auditRecorder.record(route, a, op, route.getWindSpeed(), a.getMaxWeight(),
                        truncate(reason), operator);
            } catch (Exception ignore) {
                log.warn("冲突留痕异常 anchor={}", a.getAnchorCode());
            }
        }
    }

    private String summarizeOccupancy(GroupRehearseResultDTO view) {
        return view.getAnchorResults().stream()
                .filter(r -> !r.isEligible() && r.getFailedChecks().contains(GroupBindingEvaluator.OCCUPIED))
                .map(r -> String.format("锚点%s已被启用航线%s占用", r.getAnchorCode(), r.getOccupiedByRouteCode()))
                .collect(Collectors.joining("；"));
    }

    private FlightRoute loadEnabledRoute(Long routeId) {
        if (routeId == null) {
            throw new IllegalArgumentException("请先选择一条航线");
        }
        FlightRoute route = flightRouteRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + routeId));
        if (route.getStatus() == null || route.getStatus() != 1) {
            throw new IllegalArgumentException("航线已停用，不能配桩：" + route.getRouteCode());
        }
        return route;
    }

    private List<Anchor> loadAnchorsOrdered(List<Long> anchorIds) {
        if (anchorIds == null) return List.of();
        // 去重，保持运营勾选顺序；查不到的槽位为 null，由引擎报 ANCHOR_NOT_FOUND
        List<Long> distinct = anchorIds.stream().distinct().collect(Collectors.toList());
        List<Anchor> list = new ArrayList<>();
        for (Long id : distinct) {
            list.add(anchorRepository.findById(id).orElse(null));
        }
        return list;
    }

    private String truncate(String s) {
        return s != null && s.length() > 480 ? s.substring(0, 480) : s;
    }

    /** 事务内部产物 */
    private static final class TxOutcome {
        final boolean committed;
        final List<Long> bindIds;
        final List<Long> logIds;
        final GroupRehearseResultDTO rehearsal;

        TxOutcome(boolean committed, List<Long> bindIds, List<Long> logIds, GroupRehearseResultDTO rehearsal) {
            this.committed = committed;
            this.bindIds = bindIds;
            this.logIds = logIds;
            this.rehearsal = rehearsal;
        }
    }

    /** 人为模拟的落库失败（验收缓存/库一致回滚用） */
    public static class SimulatedSubmitFailure extends RuntimeException {
        public SimulatedSubmitFailure(String message) {
            super(message);
        }
    }
}
