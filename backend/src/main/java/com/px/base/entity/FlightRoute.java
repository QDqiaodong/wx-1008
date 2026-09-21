package com.px.base.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flight_route")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_code", unique = true, nullable = false, length = 50)
    private String routeCode;

    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName;

    @Column(name = "route_group", nullable = false, length = 50)
    private String routeGroup;

    @Column(name = "wind_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal windSpeed;

    @Column(name = "wind_level", length = 20)
    private String windLevel;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status")
    @Builder.Default
    private Integer status = 1;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (windLevel == null || windLevel.isEmpty()) {
            windLevel = calculateWindLevel(windSpeed);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (windLevel == null || windLevel.isEmpty()) {
            windLevel = calculateWindLevel(windSpeed);
        }
    }

    private String calculateWindLevel(BigDecimal windSpeed) {
        // 统一走 WindLevel 规则表，避免实体与判定各算一套等级
        return com.px.base.rule.WindLevel.fromSpeed(windSpeed).getLabel();
    }
}
