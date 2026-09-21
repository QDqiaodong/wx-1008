package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个勾选项（锚点）的体检明细。预演与提交返回结构完全一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilePlanItemDTO {
    private Long anchorId;
    private String anchorCode;
    private BigDecimal maxWeight;
    private BigDecimal minWindSpeed;
    private BigDecimal maxWindSpeed;

    /** 该锚点是否通过全部独立判定（气流下限 / 气流上限 / 等级承重 / 唯一占用）。 */
    private boolean passed;

    /** 未通过的判定码集合，见 CheckCode。 */
    private List<String> failCodes;

    /** 未通过的具体人话原因，逐条列出，绝不只给“不适配”。 */
    private List<String> failReasons;

    /** 若已被其他启用航线占用，记录占用方航线编号，供运营处理冲突。 */
    private String occupiedByRouteCode;

    /** 已绑定在本航线上的提示，避免运营重复配桩。 */
    private boolean alreadyBound;
}
