package com.px.base.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "adapt_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "route_code", nullable = false, length = 50)
    private String routeCode;

    @Column(name = "anchor_id", nullable = false)
    private Long anchorId;

    @Column(name = "anchor_code", nullable = false, length = 50)
    private String anchorCode;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(name = "before_wind_speed", precision = 5, scale = 2)
    private BigDecimal beforeWindSpeed;

    @Column(name = "after_wind_speed", precision = 5, scale = 2)
    private BigDecimal afterWindSpeed;

    @Column(name = "before_weight", precision = 10, scale = 2)
    private BigDecimal beforeWeight;

    @Column(name = "after_weight", precision = 10, scale = 2)
    private BigDecimal afterWeight;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
