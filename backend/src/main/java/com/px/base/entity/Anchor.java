package com.px.base.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "anchor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anchor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anchor_code", unique = true, nullable = false, length = 50)
    private String anchorCode;

    @Column(name = "max_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxWeight;

    @Column(name = "min_wind_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal minWindSpeed;

    @Column(name = "max_wind_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxWindSpeed;

    @Column(name = "location_desc", length = 200)
    private String locationDesc;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
