package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 成组配桩"提交"结果。
 * committed=true 表示整套方案已原子落库（缓存与库一致）；
 * committed=false 表示按 ALL_OR_NOTHING 整套回退、一条都不落，rehearsal 给出逐条原因。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSubmitResultDTO {

    private Long routeId;
    private String routeCode;

    /** 是否整套提交成功 */
    private boolean committed;

    /** 实际落库的绑定关系ID（成功时与勾选合格锚点一一对应） */
    private List<Long> bindIds;

    /** 提交条数 */
    private int submittedCount;

    /** 预演快照：提交前用同一引擎重新判一遍的完整结果，便于前端对照 */
    private GroupRehearseResultDTO rehearsal;

    /** 结果概述（成功/整套回退/并发冲突等） */
    private String message;

    /** 本次落库写入的适配流水ID */
    private List<Long> logIds;
}
