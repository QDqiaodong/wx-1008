package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 组桩方案里单个锚点的判定结果。预演与提交共用同一结构，
 * 每个锚点都带着自己逐条没过的判定码与人类可读原因，绝不只给一句"不通过"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnchorResultDTO {
    private Long anchorId;
    private String anchorCode;
    private BigDecimal maxWeight;
    private BigDecimal minWindSpeed;
    private BigDecimal maxWindSpeed;

    /** 该锚点是否可进入本套方案 */
    private boolean eligible;

    /** 未通过的判定码，如 WIND_MIN/WIND_MAX/WEIGHT/OCCUPIED/ANCHOR_DISABLED */
    private List<String> failedChecks;

    /** 逐条判定原因（合格时给出覆盖说明） */
    private List<String> reasons;

    /** 若被占用，当前占用它的航线编号（冲突提示用） */
    private String occupiedByRouteCode;
}
