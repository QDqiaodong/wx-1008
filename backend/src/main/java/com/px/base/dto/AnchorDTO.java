package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnchorDTO {
    private String anchorCode;
    private BigDecimal maxWeight;
    private BigDecimal minWindSpeed;
    private BigDecimal maxWindSpeed;
    private String locationDesc;
}
