package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 成组配桩预演 / 提交请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupBindRequestDTO {
    private Long routeId;
    /** 运营一次性勾选的多个地面锚点ID */
    private List<Long> anchorIds;
    /** 操作人（可空，默认 operator 字段或 system） */
    private String operator;
}
