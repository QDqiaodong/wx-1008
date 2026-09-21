package com.px.base.rule;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 气流等级 -> 最低承重要求 规则表（整套判定的单一事实来源）。
 *
 * 这是多约束里"承重等级表"的唯一出处：微风 500、轻风 800、和风 1200、强风 1800、疾风 2500（公斤）。
 * 等级按"当前气流强度 m/s"归一化得到，预演与提交都只能从这里取阈值，
 * 不允许在任何 Controller / Service 里再硬编码第二份。
 */
public enum WindWeightRule {

    WEAK("微风", new BigDecimal("3"), new BigDecimal("500")),
    LIGHT("轻风", new BigDecimal("6"), new BigDecimal("800")),
    MODERATE("和风", new BigDecimal("10"), new BigDecimal("1200")),
    STRONG("强风", new BigDecimal("15"), new BigDecimal("1800")),
    GALE("疾风", null, new BigDecimal("2500"));

    private final String label;
    /** 该等级的上边界（不含）；疾风无上限为 null */
    private final BigDecimal upperBoundExclusive;
    /** 该等级要求的锚点最低承重（kg） */
    private final BigDecimal minRequiredWeight;

    WindWeightRule(String label, BigDecimal upperBoundExclusive, BigDecimal minRequiredWeight) {
        this.label = label;
        this.upperBoundExclusive = upperBoundExclusive;
        this.minRequiredWeight = minRequiredWeight;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getMinRequiredWeight() {
        return minRequiredWeight;
    }

    /** 按当前气流强度(m/s) 归一化到唯一等级（与既有航线的分段口径保持一致） */
    public static WindWeightRule ofSpeed(BigDecimal windSpeed) {
        if (windSpeed == null) {
            return WEAK;
        }
        double s = windSpeed.doubleValue();
        if (s < 3) return WEAK;
        if (s < 6) return LIGHT;
        if (s < 10) return MODERATE;
        if (s < 15) return STRONG;
        return GALE;
    }

    /**
     * 允许直接传航线已落库的 wind_level（中文标签）；若标签缺失/异常则回退到按风速归一化。
     * 这样无论前端/历史数据里 wind_level 是否准确，承重阈值都不会取错。
     */
    public static WindWeightRule resolve(String windLevelLabel, BigDecimal windSpeed) {
        if (windLevelLabel != null) {
            Optional<WindWeightRule> byLabel = Arrays.stream(values())
                    .filter(r -> r.label.equals(windLevelLabel.trim()))
                    .findFirst();
            if (byLabel.isPresent()) {
                return byLabel.get();
            }
        }
        return ofSpeed(windSpeed);
    }

    /** 供页面展示等级->承重对照表，保持枚举声明顺序 */
    public static Map<String, BigDecimal> requirementTable() {
        Map<String, BigDecimal> table = new LinkedHashMap<>();
        for (WindWeightRule r : values()) {
            table.put(r.label, r.minRequiredWeight);
        }
        return table;
    }
}
