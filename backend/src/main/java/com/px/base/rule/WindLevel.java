package com.px.base.rule;

import java.math.BigDecimal;

/**
 * 航线气流等级规则表（单一事实来源）。
 * <p>
 * 每个等级同时规定：气流强度区间（m/s，左闭右开）与该等级航线对单个锚点的最低承重要求（kg）。
 * 微风 500 / 轻风 800 / 和风 1200 / 强风 1800 / 疾风 2500。
 * <p>
 * 区间边界与 {@code FlightRoute#calculateWindLevel} 保持一致，集中在此处避免预演与提交各查一套。
 */
public enum WindLevel {

    BREEZE("微风", 0D, 3D, 500),
    LIGHT("轻风", 3D, 6D, 800),
    MODERATE("和风", 6D, 10D, 1200),
    STRONG("强风", 10D, 15D, 1800),
    GALE("疾风", 15D, 20D, 2500);

    private final String label;
    private final double lowerBound;
    private final double upperBound;
    private final int minWeight;

    WindLevel(String label, double lowerBound, double upperBound, int minWeight) {
        this.label = label;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.minWeight = minWeight;
    }

    public String getLabel() {
        return label;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public int getMinWeight() {
        return minWeight;
    }

    /**
     * 按航线当前气流强度查等级（进而查最低承重要求）。
     */
    public static WindLevel fromSpeed(BigDecimal windSpeed) {
        if (windSpeed == null) {
            return BREEZE;
        }
        double speed = windSpeed.doubleValue();
        if (speed < 3D) {
            return BREEZE;
        }
        if (speed < 6D) {
            return LIGHT;
        }
        if (speed < 10D) {
            return MODERATE;
        }
        if (speed < 15D) {
            return STRONG;
        }
        return GALE;
    }
}
