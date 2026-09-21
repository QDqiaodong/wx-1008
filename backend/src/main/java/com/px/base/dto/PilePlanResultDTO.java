package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一套配桩方案的预演/提交结果。预演与提交共用这一份结构与同一套判定，
 * 区别仅在于 mode=PREVIEW 不落库，mode=SUBMIT 在判定全部通过后才落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilePlanResultDTO {

    public enum Mode {
        /** 预演：只体检、不落库。 */
        PREVIEW,
        /** 提交：体检通过后落库。 */
        SUBMIT
    }

    /** 整套方案是否合格（全有或全无：任一锚点不过或总承重预算不足都为 false）。 */
    private boolean valid;

    private Mode mode;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private BigDecimal routeWindSpeed;
    private String windLevel;
    /** 该气流等级对单个锚点的最低承重要求（kg）。 */
    private Integer levelMinWeight;

    private int totalCount;
    private int passedCount;
    private int failedCount;

    /** 整套方案总承重预算：本等级单锚点最低承重 × 勾选数量。 */
    private BigDecimal requiredTotalWeight;
    /** 合格锚点最大承重之和。 */
    private BigDecimal actualTotalWeight;
    private boolean budgetEnough;
    /** 预算不足时的说明（合格锚点才计入总承重）。 */
    private String budgetReason;

    private List<PilePlanItemDTO> items;

    /** 方案级别的汇总原因（含策略后果说明），页面直接展示。 */
    private String summary;

    /** 提交成功后新建的绑定关系ID；预演或被拒时为空。 */
    private List<Long> bindIds;
    /** 本次适配流水ID（配上或被拒均留痕）。 */
    private List<Long> logIds;
}
