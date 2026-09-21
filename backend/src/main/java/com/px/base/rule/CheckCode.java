package com.px.base.rule;

/**
 * 配桩体检判定码。预演与提交共用同一套码值，流水留痕也记录它，便于定位是哪条判定没过。
 */
public final class CheckCode {

    private CheckCode() {
    }

    /** 预演/提交入参问题（如未勾选任何锚点）。 */
    public static final String EMPTY_PLAN = "EMPTY_PLAN";
    /** 航线不存在。 */
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    /** 航线已停用。 */
    public static final String ROUTE_DISABLED = "ROUTE_DISABLED";
    /** 锚点不存在。 */
    public static final String ANCHOR_NOT_FOUND = "ANCHOR_NOT_FOUND";
    /** 锚点已停用。 */
    public static final String ANCHOR_DISABLED = "ANCHOR_DISABLED";
    /** 同一套方案内重复勾选同一锚点。 */
    public static final String DUPLICATE_PICK = "DUPLICATE_PICK";
    /** 该锚点已在本航线上服役，重复配桩。 */
    public static final String ALREADY_BOUND = "ALREADY_BOUND";
    /** 气流下限不匹配：锚点下限高于航线气流，只适配大风的锚点不能配微风航线。 */
    public static final String WIND_LOW = "WIND_LOW";
    /** 气流上限不匹配：锚点上限低于航线气流。 */
    public static final String WIND_HIGH = "WIND_HIGH";
    /** 承重不足：锚点最大承重低于航线气流等级对应的最低承重要求。 */
    public static final String WEIGHT = "WEIGHT";
    /** 占用冲突：锚点已绑定在其他启用航线上，单锚点同一时间只能服役一条航线。 */
    public static final String OCCUPIED = "OCCUPIED";
    /** 整套方案总承重预算不足。 */
    public static final String BUDGET = "BUDGET";
    /** 并发抢占：等待锚点锁超时。 */
    public static final String LOCK_BUSY = "LOCK_BUSY";
    /** 提交落库阶段失败（用于演示与兜底）。 */
    public static final String DB_FAILURE = "DB_FAILURE";
}
