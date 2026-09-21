package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 整套配桩方案的"预演体检"结果。提交接口也返回同一结构，
 * 保证预演看到的逐条结论与提交真正落库时的裁决完全一致（同一套判定引擎）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupRehearseResultDTO {

    private Long routeId;
    private String routeCode;
    private String routeName;
    private BigDecimal routeWindSpeed;
    private String windLevel;

    /** 该航线风级要求的单锚点最低承重(kg) */
    private BigDecimal requiredMinWeight;

    /** 勾选锚点总数 / 合格数 / 不合格数 */
    private int totalCount;
    private int eligibleCount;
    private int rejectedCount;

    /** 整套方案：合格锚点承重合计（总承重预算） */
    private BigDecimal eligibleTotalWeight;
    /** 本风级下整套方案的最低承重预算门槛 = 单锚点门槛 * 锚点数 */
    private BigDecimal requiredTotalWeight;
    private boolean totalWeightBudgetOk;

    /** 整套是否可提交：必须每个锚点都合格且总承重预算达标 */
    private boolean groupValid;

    /** 一致策略标识：ALL_OR_NOTHING（整套全成或全回退） */
    private String policy;
    /** 给运营看的策略后果说明（页面直接展示） */
    private String policyNotice;

    /** 每个锚点的逐条判定 */
    private List<GroupAnchorResultDTO> anchorResults;

    /** 风级->最低承重 对照表，供页面展示，避免前端再维护一份 */
    private Map<String, BigDecimal> weightRuleTable;
}
