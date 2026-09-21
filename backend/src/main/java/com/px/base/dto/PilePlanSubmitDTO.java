package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 航线成组配桩提交/预演请求：选定一条航线，一次性勾选多个地面锚点组成一套方案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilePlanSubmitDTO {
    private Long routeId;
    private List<Long> anchorIds;
    private String operator;

    /**
     * 仅用于验收演示：为 true 时在落库阶段人为抛出异常，验证缓存与库的一致回滚。生产应关闭。
     */
    private Boolean simulateDbFailure;
}
