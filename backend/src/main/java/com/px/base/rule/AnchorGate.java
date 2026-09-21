package com.px.base.rule;

import com.px.base.rule.CheckCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 锚点对航线的三项独立适配件（不含“是否已被占用”这类需要查绑定关系的业务判定）。
 * 预演、提交、以及历史的单锚点绑定/重校接口都用它，保证全系统口径一致：
 * ① 气流下限 ≤ 航线气流；② 气流上限 ≥ 航线气流；③ 锚点承重 ≥ 航线气流等级查表的最低承重。
 */
public final class AnchorGate {

    private AnchorGate() {
    }

    /** 一条适配件判定结果：判定码 + 具体原因。 */
    public record GateFailure(String code, String message) {
    }

    /**
     * 对单个锚点跑三项适配件，返回未通过项（空集合代表三项全过）。这是全系统唯一的三项判定实现。
     */
    public static List<GateFailure> evaluate(BigDecimal anchorMinWind, BigDecimal anchorMaxWind,
                                             BigDecimal anchorWeight, BigDecimal routeWind) {
        WindLevel level = WindLevel.fromSpeed(routeWind);
        List<GateFailure> failures = new ArrayList<>();

        if (anchorMinWind.compareTo(routeWind) > 0) {
            failures.add(new GateFailure(CheckCode.WIND_LOW, String.format(
                    "气流下限不匹配：锚点只适配%.2fm/s及以上的大风（适配下限%.2fm/s），下限高于航线当前气流%.2fm/s，区间没有真正包住航线气流。",
                    anchorMinWind, anchorMinWind, routeWind)));
        }
        if (anchorMaxWind.compareTo(routeWind) < 0) {
            failures.add(new GateFailure(CheckCode.WIND_HIGH, String.format(
                    "气流上限不匹配：锚点适配气流上限%.2fm/s低于航线当前气流%.2fm/s。",
                    anchorMaxWind, routeWind)));
        }
        if (anchorWeight.compareTo(BigDecimal.valueOf(level.getMinWeight())) < 0) {
            failures.add(new GateFailure(CheckCode.WEIGHT, String.format(
                    "承重不足%s等级要求：锚点最大承重%.0fkg，低于%s航线按气流等级查表的最低承重%dkg（等级表：微风500/轻风800/和风1200/强风1800/疾风2500kg）。",
                    level.getLabel(), anchorWeight, level.getLabel(), level.getMinWeight())));
        }
        return failures;
    }

    /** 便捷方法：仅判断三项是否全过。 */
    public static boolean passes(BigDecimal anchorMinWind, BigDecimal anchorMaxWind,
                                 BigDecimal anchorWeight, BigDecimal routeWind) {
        return evaluate(anchorMinWind, anchorMaxWind, anchorWeight, routeWind).isEmpty();
    }
}
