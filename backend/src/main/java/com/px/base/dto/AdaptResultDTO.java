package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptResultDTO {
    private boolean valid;
    private Long routeId;
    private Long anchorId;
    private Long bindId;
    private String reason;
    private Integer rebindCount;
    private Integer unbindCount;
    private List<Long> logIds;
}
