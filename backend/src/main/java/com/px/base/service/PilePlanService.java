package com.px.base.service;

import com.px.base.dto.PilePlanItemDTO;
import com.px.base.dto.PilePlanResultDTO;
import com.px.base.dto.PilePlanSubmitDTO;
import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.repository.RouteAnchorRepository;
import com.px.base.rule.CheckCode;
import com.px.base.rule.WindLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 航线成组配桩：预演体检与提交落库共用同一套判定 {@link #evaluatePlan}。
 * <p>
 * 判定同时守住三件彼此独立的事，缺一件都不算配上：
 * <ol>
 *     <li>气流区间必须真正包住航线当前气流——下限不高于、上限不低于（修掉只看上限的老问题）；</li>
 *     <li>锚点最大承重必须达到航线气流等级查表得到的最低承重（微风500/轻风800/和风1200/强风1800/疾风2500）；</li>
 *     <li>单锚点唯一占用——已绑在其他“启用航线”上的锚点不能再进这套方案；
 *     另有整套方案总承重预算裁决。</li>
 * </ol>
 * 策略为【整组回退 / 全有或全无】：只要有一个锚点不合格或预算不足，预演不通过、提交也一条都不落。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PilePlanService {

    private final AnchorRepository anchorRepository;
    private final FlightRouteRepository flightRouteRepository;
    private final RouteAnchorRepository routeAnchorRepository;
    private final AnchorLockService anchorLockService;
    private final AnchorService anchorService;
    private final PilePlanPersister persister;

    /** 预演：只体检，不加锁、不写库、不留痕，供运营在提交前确认。 */
    public PilePlanResultDTO preview(PilePlanSubmitDTO dto) {
        return evaluatePlan(dto.getRouteId(), dto.getAnchorIds(), PilePlanResultDTO.Mode.PREVIEW);
    }

    /** 提交：锚点锁内用同一套判定复核，通过才整组落库；任一不过则整组回退并写 REJECT 流水。 */
    public PilePlanResultDTO submit(PilePlanSubmitDTO dto) {
        String operator = (dto.getOperator() == null || dto.getOperator().isBlank()) ? "operator" : dto.getOperator();
        List<Long> anchorIds = normalize(dto.getAnchorIds());

        try (AnchorLockService.AnchorLocks ignored = anchorLockService.lockAll(anchorIds)) {
            PilePlanResultDTO result = evaluatePlan(dto.getRouteId(), anchorIds, PilePlanResultDTO.Mode.SUBMIT);
            if (!result.isValid()) {
                FlightRoute route = flightRouteRepository.findById(dto.getRouteId()).orElse(null);
                if (route != null) {
                    result.setLogIds(persister.saveRejectLogs(route, buildRejectEntries(result), operator));
                }
                result.setSummary(result.getSummary() + " 已按整组回退处理：本次提交一条绑定都未落库，并已写入适配流水。");
                return result;
            }

            FlightRoute route = flightRouteRepository.findById(dto.getRouteId()).orElseThrow();
            List<Anchor> anchors = anchorIds.stream()
                    .map(anchorRepository::findById)
                    .flatMap(Optional::stream)
                    .toList();

            AnchorService.CacheSnapshot snapshot = anchorService.snapshot(anchors);
            try {
                // 缓存先与本次方案对齐，落库必须在同一请求内紧随其后成功，否则按快照回退。
                anchorService.syncCacheForBind(anchors);
                if (Boolean.TRUE.equals(dto.getSimulateDbFailure())) {
                    throw new IllegalStateException("人为制造的落库失败（验收开关 simulateDbFailure=true）");
                }
                PilePlanPersister.BindOutcome outcome = persister.persistBindings(route, anchors, operator);
                result.setBindIds(outcome.bindIds());
                result.setLogIds(outcome.logIds());
                result.setSummary(String.format(
                        "提交成功：%d个锚点已整组落库（绑定ID：%s），Redis承重与气流排序缓存已与数据库对齐。",
                        anchors.size(), outcome.bindIds()));
                return result;
            } catch (DataIntegrityViolationException e) {
                // 唯一占用兜底：理论上锁+判定已挡住，这里处理历史脏数据/边界竞争。回滚缓存后按占用冲突留痕。
                anchorService.restoreCache(snapshot, anchors);
                log.warn("提交触发唯一占用约束，整组回滚: {}", e.getMessage());
                PilePlanResultDTO recheck = evaluatePlan(route.getId(), anchorIds, PilePlanResultDTO.Mode.SUBMIT);
                recheck.setLogIds(persister.saveRejectLogs(route, buildRejectEntries(recheck), operator));
                recheck.setSummary("提交因锚点被并发占用而整组回退，一条未入库，缓存已同步回退。冲突锚点见明细与流水。");
                return recheck;
            } catch (RuntimeException e) {
                // 落库失败：回滚已写缓存，保证缓存与库对得上；独立事务写被拒流水。
                anchorService.restoreCache(snapshot, anchors);
                log.error("配桩落库失败，整组回滚并回退缓存: {}", e.getMessage(), e);
                List<RejectEntry> failureEntries = anchors.stream()
                        .map(a -> new RejectEntry(a.getId(), a.getAnchorCode(),
                                CheckCode.DB_FAILURE + "：提交落库阶段失败，整套方案已整组回退、缓存已同步回退，无半拉子数据。原始错误：" + e.getMessage()))
                        .toList();
                result.setValid(false);
                result.setLogIds(persister.saveRejectLogs(route, failureEntries, operator));
                result.setSummary("提交落库失败，已整组回滚：数据库无新增绑定，Redis缓存已按提交前快照回退，二者一致。详见流水。");
                return result;
            }
        } catch (AnchorLockService.AnchorBusyException e) {
            return buildLockBusyResult(dto.getRouteId(), anchorIds, e, operator);
        }
    }

    /**
     * 唯一判定入口（预演与提交都走这里，绝无两套口径）。只读、不加锁；提交时在锚点锁内调用以保证占用新鲜。
     */
    public PilePlanResultDTO evaluatePlan(Long routeId, List<Long> rawAnchorIds, PilePlanResultDTO.Mode mode) {
        List<Long> anchorIds = normalize(rawAnchorIds);

        PilePlanResultDTO.PilePlanResultDTOBuilder builder = PilePlanResultDTO.builder()
                .mode(mode)
                .routeId(routeId)
                .totalCount(anchorIds.size());

        Optional<FlightRoute> routeOpt = routeId == null ? Optional.empty() : flightRouteRepository.findById(routeId);
        if (routeOpt.isEmpty()) {
            return builder.valid(false)
                    .totalCount(anchorIds.size())
                    .passedCount(0).failedCount(anchorIds.size())
                    .budgetEnough(false)
                    .items(List.of())
                    .summary("体检未通过：航线不存在或未选择，无法配桩。")
                    .build();
        }
        FlightRoute route = routeOpt.get();
        WindLevel level = WindLevel.fromSpeed(route.getWindSpeed());

        builder.routeCode(route.getRouteCode())
                .routeName(route.getRouteName())
                .routeWindSpeed(route.getWindSpeed())
                .windLevel(level.getLabel())
                .levelMinWeight(level.getMinWeight());

        if (route.getStatus() != null && route.getStatus() != 1) {
            return builder.valid(false)
                    .passedCount(0).failedCount(anchorIds.size())
                    .budgetEnough(false)
                    .items(List.of())
                    .summary(String.format("体检未通过：航线%s已停用，不能配桩。", route.getRouteCode()))
                    .build();
        }

        if (anchorIds.isEmpty()) {
            return builder.valid(false)
                    .passedCount(0).failedCount(0)
                    .requiredTotalWeight(BigDecimal.ZERO).actualTotalWeight(BigDecimal.ZERO)
                    .budgetEnough(false)
                    .items(List.of())
                    .summary("体检未通过：未勾选任何锚点，配桩方案不能为空。")
                    .build();
        }

        BigDecimal requiredTotal = BigDecimal.valueOf(level.getMinWeight())
                .multiply(BigDecimal.valueOf(anchorIds.size()));

        List<PilePlanItemDTO> items = new ArrayList<>();
        BigDecimal actualTotal = BigDecimal.ZERO;
        int passedCount = 0;

        // 去重保序：同一锚点在方案内重复勾选直接判 DUPLICATE_PICK。
        Map<Long, Integer> seenCount = new LinkedHashMap<>();
        for (Long id : anchorIds) {
            seenCount.merge(id, 1, Integer::sum);
        }

        for (Long anchorId : anchorIds) {
            PilePlanItemDTO item = checkAnchor(route, level, anchorId, seenCount.get(anchorId) > 1);
            items.add(item);
            if (item.isPassed()) {
                Anchor anchor = anchorRepository.findById(anchorId).orElse(null);
                if (anchor != null) {
                    actualTotal = actualTotal.add(anchor.getMaxWeight());
                }
                passedCount++;
            }
        }

        int failedCount = anchorIds.size() - passedCount;
        boolean budgetEnough = actualTotal.compareTo(requiredTotal) >= 0;
        String budgetReason = null;
        if (!budgetEnough) {
            budgetReason = String.format(
                    "整套方案总承重预算不足：%s等级单锚点最低承重%dkg × %d个锚点 = 需%.0fkg，而合格锚点(%d个)最大承重合计仅%.0fkg。",
                    level.getLabel(), level.getMinWeight(), anchorIds.size(),
                    requiredTotal.doubleValue(), passedCount, actualTotal.doubleValue());
        }

        boolean valid = failedCount == 0 && budgetEnough;

        String summary;
        if (valid) {
            summary = String.format(
                    "体检通过：%d个锚点全部合格——气流区间包住%.2fm/s(%s)、承重均≥等级要求%dkg、且均未被别的启用航线占用；"
                            + "总承重%.0fkg≥方案预算%.0fkg。%s",
                    anchorIds.size(), route.getWindSpeed(), level.getLabel(), level.getMinWeight(),
                    actualTotal.doubleValue(), requiredTotal.doubleValue(),
                    mode == PilePlanResultDTO.Mode.PREVIEW ? "可提交，提交时将整组一次性落库。" : "");
        } else {
            summary = String.format(
                    "体检未通过：共%d个锚点，合格%d个、不合格/冲突%d个。按【整组回退 · 全有或全无】策略，"
                            + "只要有一个锚点不过或总承重预算不足，提交时这%d个锚点一个都不会落库（不会只落合格的），"
                            + "请剔除/替换不合格锚点后整组重新预演、提交。%s",
                    anchorIds.size(), passedCount, failedCount, anchorIds.size(),
                    budgetEnough ? "" : "另：" + budgetReason);
        }

        return builder.valid(valid)
                .passedCount(passedCount)
                .failedCount(failedCount)
                .requiredTotalWeight(requiredTotal)
                .actualTotalWeight(actualTotal)
                .budgetEnough(budgetEnough)
                .budgetReason(budgetReason)
                .items(items)
                .bindIds(new ArrayList<>())
                .logIds(new ArrayList<>())
                .summary(summary)
                .build();
    }

    /**
     * 对单个锚点跑全部独立判定，返回逐条原因；任一不过 passed=false。
     */
    private PilePlanItemDTO checkAnchor(FlightRoute route, WindLevel level, Long anchorId, boolean duplicate) {
        List<String> codes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        Optional<Anchor> anchorOpt = anchorRepository.findById(anchorId);
        if (anchorOpt.isEmpty()) {
            codes.add(CheckCode.ANCHOR_NOT_FOUND);
            reasons.add("锚点不存在(id=" + anchorId + ")，无法纳入方案。");
            return PilePlanItemDTO.builder()
                    .anchorId(anchorId).anchorCode(String.valueOf(anchorId))
                    .passed(false).failCodes(codes).failReasons(reasons).build();
        }
        Anchor anchor = anchorOpt.get();

        if (anchor.getStatus() != null && anchor.getStatus() != 1) {
            codes.add(CheckCode.ANCHOR_DISABLED);
            reasons.add(String.format("锚点%s已停用，不能配桩。", anchor.getAnchorCode()));
        }

        if (duplicate) {
            codes.add(CheckCode.DUPLICATE_PICK);
            reasons.add(String.format("锚点%s在本套方案中被重复勾选，一套方案里同一锚点只能出现一次。", anchor.getAnchorCode()));
        }

        if (Boolean.TRUE.equals(routeAnchorRepository
                .existsByRouteIdAndAnchorIdAndStatus(route.getId(), anchorId, 1))) {
            codes.add(CheckCode.ALREADY_BOUND);
            reasons.add(String.format("锚点%s已在本航线%s上服役，无需重复配桩。",
                    anchor.getAnchorCode(), route.getRouteCode()));
        }

        BigDecimal wind = route.getWindSpeed();
        // 判定一（区间真正包住，下限上限都查）+ 判定二（等级承重查表），统一走 AnchorGate（全系统唯一实现）。
        for (com.px.base.rule.AnchorGate.GateFailure f :
                com.px.base.rule.AnchorGate.evaluate(anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(),
                        anchor.getMaxWeight(), wind)) {
            codes.add(f.code());
            reasons.add(String.format("锚点%s %s", anchor.getAnchorCode(), f.message()));
        }

        // 判定三：单锚点唯一占用（只计其他“启用航线”上的有效绑定）。
        String occupiedByRouteCode = null;
        Long occupyingRouteId = routeAnchorRepository.findActiveOccupyingRouteId(anchorId);
        if (occupyingRouteId != null && !occupyingRouteId.equals(route.getId())) {
            occupiedByRouteCode = flightRouteRepository.findById(occupyingRouteId)
                    .map(FlightRoute::getRouteCode).orElse(String.valueOf(occupyingRouteId));
            codes.add(CheckCode.OCCUPIED);
            reasons.add(String.format(
                    "占用冲突：锚点%s同一时间只能真正服役于一条航线，现已绑定在其他启用航线%s上，不能再配入本航线%s。",
                    anchor.getAnchorCode(), occupiedByRouteCode, route.getRouteCode()));
        }

        return PilePlanItemDTO.builder()
                .anchorId(anchor.getId())
                .anchorCode(anchor.getAnchorCode())
                .maxWeight(anchor.getMaxWeight())
                .minWindSpeed(anchor.getMinWindSpeed())
                .maxWindSpeed(anchor.getMaxWindSpeed())
                .passed(codes.isEmpty())
                .failCodes(codes)
                .failReasons(reasons)
                .occupiedByRouteCode(occupiedByRouteCode)
                .alreadyBound(codes.contains(CheckCode.ALREADY_BOUND))
                .build();
    }

    /**
     * 把体检结果转成被拒流水条目：不合格锚点各记自己的原因；
     * 若只是预算不足，则把预算原因记到合格锚点头上，保证预算裁决也在流水里留痕。
     */
    private List<RejectEntry> buildRejectEntries(PilePlanResultDTO result) {
        List<RejectEntry> entries = new ArrayList<>();
        for (PilePlanItemDTO item : result.getItems()) {
            if (!item.isPassed()) {
                String codes = item.getFailCodes() == null ? "" : "[" + String.join(",", item.getFailCodes()) + "] ";
                entries.add(new RejectEntry(item.getAnchorId(), item.getAnchorCode(),
                        "REJECT " + codes + String.join("；", item.getFailReasons())));
            }
        }
        if (!result.isBudgetEnough()) {
            for (PilePlanItemDTO item : result.getItems()) {
                if (item.isPassed()) {
                    entries.add(new RejectEntry(item.getAnchorId(), item.getAnchorCode(),
                            "REJECT [" + CheckCode.BUDGET + "] " + result.getBudgetReason()));
                }
            }
        }
        return entries;
    }

    private PilePlanResultDTO buildLockBusyResult(Long routeId, List<Long> anchorIds,
                                                  AnchorLockService.AnchorBusyException e, String operator) {
        // 抢锁失败：以同一套判定补一份结果，并把争用锚点标记为占用冲突，确保返回结构与流水口径一致。
        PilePlanResultDTO result = evaluatePlan(routeId, anchorIds, PilePlanResultDTO.Mode.SUBMIT);
        Long busyAnchorId = e.getAnchorId();
        for (PilePlanItemDTO item : result.getItems()) {
            if (item.getAnchorId().equals(busyAnchorId) && item.isPassed()) {
                item.setPassed(false);
                item.setFailCodes(new ArrayList<>(item.getFailCodes() == null ? List.of() : item.getFailCodes()));
                item.setFailReasons(new ArrayList<>(item.getFailReasons() == null ? List.of() : item.getFailReasons()));
                item.getFailCodes().add(CheckCode.LOCK_BUSY);
                item.getFailReasons().add("并发占用冲突：" + e.getMessage());
            }
        }
        result.setValid(false);
        long failed = result.getItems().stream().filter(i -> !i.isPassed()).count();
        result.setFailedCount((int) failed);
        result.setPassedCount(result.getItems().size() - (int) failed);
        result.setSummary("并发占用冲突：稀缺锚点正被另一套方案抢占，本次提交整组回退、一条未落库，请重试。冲突锚点见明细。");

        Optional<FlightRoute> route = routeId == null ? Optional.empty() : flightRouteRepository.findById(routeId);
        route.ifPresent(r -> result.setLogIds(persister.saveRejectLogs(r, buildRejectEntries(result), operator)));
        return result;
    }

    private List<Long> normalize(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
