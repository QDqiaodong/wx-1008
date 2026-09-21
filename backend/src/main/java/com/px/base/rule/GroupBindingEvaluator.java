package com.px.base.rule;

import com.px.base.dto.GroupAnchorResultDTO;
import com.px.base.dto.GroupRehearseResultDTO;
import com.px.base.entity.Anchor;
import com.px.base.entity.AnchorOccupancy;
import com.px.base.entity.FlightRoute;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 成组配桩的【唯一判定引擎】。
 *
 * 预演(rehearse)与提交(submit)都只能调用 {@link #evaluate}，不允许在别处另写一套判断，
 * 从根上杜绝"预演说能配、提交又放行别的"。
 *
 * 对每个锚点独立守住以下判定（缺一件都不算配上）：
 *  1) 气流区间真正包住航线当前气流：下限 anchor.minWindSpeed <= route.windSpeed（WIND_MIN）
 *     且上限 anchor.maxWindSpeed >= route.windSpeed（WIND_MAX）。旧系统只比上限，本引擎把下限补齐。
 *  2) 承重达标（双重，避免"只适配大风却配微风"被微风的低门槛蒙混）：
 *     WEIGHT       —— 锚点承重 >= 航线【当前风级】最低承重（微风500/轻风800/和风1200/强风1800/疾风2500）；
 *     WEIGHT_LEVEL —— 当锚点"适配下限"高于航线气流（区间向下都够不着、本就不是为该航线风级设计）时，
 *                     其承重还须 >= 锚点【起始适配风级】(由 minWindSpeed 决定)的等级承重。
 *                     例如"只适配强风(下限10)、自重600kg"的锚点配微风：起始即强风需1800kg，
 *                     600kg 远不够，暴露"差在等级要求上"，不会被微风500的低门槛放行。
 *                     而宽区间全能锚（下限0、起始微风、承重2000kg）不属此情形，不会被误拒。
 *  3) 单锚点唯一占用（OCCUPIED）：已被别的【启用航线】主占则不合格。
 *
 * 整套层面再守一道"总承重预算"：合格锚点承重合计 >= 当前风级单锚点门槛 × 锚点数。
 */
@Component
public class GroupBindingEvaluator {

    public static final String ANCHOR_DISABLED = "ANCHOR_DISABLED";
    public static final String ANCHOR_NOT_FOUND = "ANCHOR_NOT_FOUND";
    public static final String WIND_MIN = "WIND_MIN";
    public static final String WIND_MAX = "WIND_MAX";
    public static final String WEIGHT = "WEIGHT";
    public static final String WEIGHT_LEVEL = "WEIGHT_LEVEL";
    public static final String OCCUPIED = "OCCUPIED";
    public static final String ALREADY_BOUND = "ALREADY_BOUND";

    public static final String POLICY_ALL_OR_NOTHING = "ALL_OR_NOTHING";

    private static final String POLICY_NOTICE =
            "本套方案采用【整套一致·全成或全回退】策略：勾选的锚点中只要有一个不通过预演体检，"
            + "提交时整套方案一起回退，一条绑定都不落库；只有全部锚点都合格且整套承重预算达标，才会一次性整体落库。"
            + "预演与提交使用同一套判定，结论不会在两个环节互相矛盾。";

    /**
     * @param route           选中的航线
     * @param anchors         勾选的锚点（与 anchorIds 顺序对应；查不到的为 null 槽位）
     * @param occupancyLookup 锚点ID -> 当前主占（提交时在事务+行锁内提供，保证读到的是最新占用）
     */
    public GroupRehearseResultDTO evaluate(FlightRoute route,
                                           List<Anchor> anchors,
                                           Function<Long, Optional<AnchorOccupancy>> occupancyLookup) {
        WindWeightRule routeRule = WindWeightRule.resolve(route.getWindLevel(), route.getWindSpeed());
        BigDecimal requiredMinWeight = routeRule.getMinRequiredWeight();
        BigDecimal routeWind = route.getWindSpeed();

        List<GroupAnchorResultDTO> results = new ArrayList<>();
        int eligibleCount = 0;
        BigDecimal eligibleTotalWeight = BigDecimal.ZERO;

        for (Anchor anchor : anchors) {
            GroupAnchorResultDTO r = evaluateOne(route, routeRule, anchor, occupancyLookup);
            results.add(r);
            if (r.isEligible()) {
                eligibleCount++;
                eligibleTotalWeight = eligibleTotalWeight.add(anchor.getMaxWeight());
            }
        }

        int total = anchors.size();
        BigDecimal requiredTotalWeight = requiredMinWeight.multiply(BigDecimal.valueOf(total));
        boolean budgetOk = total > 0 && eligibleCount == total
                && eligibleTotalWeight.compareTo(requiredTotalWeight) >= 0;
        boolean groupValid = total > 0 && eligibleCount == total && budgetOk;

        return GroupRehearseResultDTO.builder()
                .routeId(route.getId())
                .routeCode(route.getRouteCode())
                .routeName(route.getRouteName())
                .routeWindSpeed(routeWind)
                .windLevel(routeRule.getLabel())
                .requiredMinWeight(requiredMinWeight)
                .totalCount(total)
                .eligibleCount(eligibleCount)
                .rejectedCount(total - eligibleCount)
                .eligibleTotalWeight(eligibleTotalWeight)
                .requiredTotalWeight(requiredTotalWeight)
                .totalWeightBudgetOk(budgetOk)
                .groupValid(groupValid)
                .policy(POLICY_ALL_OR_NOTHING)
                .policyNotice(POLICY_NOTICE)
                .anchorResults(results)
                .weightRuleTable(WindWeightRule.requirementTable())
                .build();
    }

    private GroupAnchorResultDTO evaluateOne(FlightRoute route,
                                             WindWeightRule routeRule,
                                             Anchor anchor,
                                             Function<Long, Optional<AnchorOccupancy>> occupancyLookup) {
        List<String> failed = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (anchor == null) {
            return GroupAnchorResultDTO.builder()
                    .eligible(false)
                    .failedChecks(List.of(ANCHOR_NOT_FOUND))
                    .reasons(List.of("锚点不存在或已被删除，无法纳入方案"))
                    .build();
        }

        // 0) 锚点启用状态
        if (anchor.getStatus() == null || anchor.getStatus() != 1) {
            failed.add(ANCHOR_DISABLED);
            reasons.add(String.format("锚点%s已停用，不能进入配桩方案", anchor.getAnchorCode()));
        }

        BigDecimal routeWind = route.getWindSpeed();

        // 1a) 气流下限必须真正包住：anchor.minWindSpeed <= 航线气流
        if (anchor.getMinWindSpeed().compareTo(routeWind) > 0) {
            failed.add(WIND_MIN);
            reasons.add(String.format(
                    "气流下限不匹配：锚点%s只适配 %.2f m/s 以上的风（%s段），其下限 %.2f 高于航线当前气流 %.2f m/s（%s），区间无法向下覆盖该航线",
                    anchor.getAnchorCode(), anchor.getMinWindSpeed(),
                    WindWeightRule.ofSpeed(anchor.getMinWindSpeed()).getLabel(),
                    anchor.getMinWindSpeed(), routeWind, routeRule.getLabel()));
        }

        // 1b) 气流上限：anchor.maxWindSpeed >= 航线气流
        if (anchor.getMaxWindSpeed().compareTo(routeWind) < 0) {
            failed.add(WIND_MAX);
            reasons.add(String.format(
                    "气流上限不匹配：锚点%s适配上限 %.2f m/s 低于航线当前气流 %.2f m/s，区间无法向上覆盖",
                    anchor.getAnchorCode(), anchor.getMaxWindSpeed(), routeWind));
        }

        // 2a) 承重 >= 航线当前风级门槛
        if (anchor.getMaxWeight().compareTo(routeRule.getMinRequiredWeight()) < 0) {
            failed.add(WEIGHT);
            reasons.add(String.format(
                    "承重不足：锚点%s最大承重 %.0fkg 低于航线当前风级【%s】的最低承重要求 %.0fkg",
                    anchor.getAnchorCode(), anchor.getMaxWeight(),
                    routeRule.getLabel(), routeRule.getMinRequiredWeight()));
        }

        // 2b) 区间向下都够不着航线气流时，承重还须达到锚点"起始适配风级"的等级门槛，
        //     堵住"只适配大风却借本航线低风级门槛放行"的口子（宽区间全能锚不触发此项）
        boolean windMinFail = failed.contains(WIND_MIN);
        if (windMinFail) {
            WindWeightRule anchorStartRule = WindWeightRule.ofSpeed(anchor.getMinWindSpeed());
            if (anchor.getMaxWeight().compareTo(anchorStartRule.getMinRequiredWeight()) < 0) {
                failed.add(WEIGHT_LEVEL);
                reasons.add(String.format(
                        "承重等级不匹配：该锚点起始适配风级为【%s】（下限%.2fm/s），等级承重要求 %.0fkg，"
                                + "锚点自重仅 %.0fkg，达不到起始风级门槛（不能仅以本航线%s门槛 %.0fkg 放行；差在风级等级要求上）",
                        anchorStartRule.getLabel(), anchor.getMinWindSpeed(),
                        anchorStartRule.getMinRequiredWeight(), anchor.getMaxWeight(),
                        routeRule.getLabel(), routeRule.getMinRequiredWeight()));
            }
        }

        // 3) 单锚点唯一占用
        Optional<AnchorOccupancy> occ = occupancyLookup.apply(anchor.getId());
        String occupiedByRoute = null;
        if (occ.isPresent()) {
            AnchorOccupancy o = occ.get();
            if (o.getRouteId().equals(route.getId())) {
                failed.add(ALREADY_BOUND);
                reasons.add(String.format("锚点%s已在本航线服役，无需重复配桩", anchor.getAnchorCode()));
            } else {
                occupiedByRoute = o.getRouteCode();
                failed.add(OCCUPIED);
                reasons.add(String.format(
                        "唯一占用冲突：锚点%s已被【启用航线 %s】主占服役，同一时间不能再配给本航线 %s",
                        anchor.getAnchorCode(), o.getRouteCode(), route.getRouteCode()));
            }
        }

        boolean eligible = failed.isEmpty();
        if (eligible) {
            reasons.add(String.format(
                    "合格：适配气流区间[%.2f-%.2f]包住航线气流%.2fm/s（%s）；承重%.0fkg≥门槛%.0fkg；当前未被其他航线占用",
                    anchor.getMinWindSpeed(), anchor.getMaxWindSpeed(), routeWind,
                    routeRule.getLabel(), anchor.getMaxWeight(), routeRule.getMinRequiredWeight()));
        }

        return GroupAnchorResultDTO.builder()
                .anchorId(anchor.getId())
                .anchorCode(anchor.getAnchorCode())
                .maxWeight(anchor.getMaxWeight())
                .minWindSpeed(anchor.getMinWindSpeed())
                .maxWindSpeed(anchor.getMaxWindSpeed())
                .eligible(eligible)
                .failedChecks(failed)
                .reasons(reasons)
                .occupiedByRouteCode(occupiedByRoute)
                .build();
    }

    /** 风级承重对照表，供控制器/页面单独取用 */
    public Map<String, BigDecimal> ruleTable() {
        return WindWeightRule.requirementTable();
    }
}
