package com.px.base.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 锚点"主占"表：一个锚点在同一时间只能真正服役于一条启用航线。
 *
 * 仅靠 route_anchor 的软状态无法在并发下阻止同一锚点被两条航线同时绑定，
 * 这里用 anchor_id 作为主键，从数据库层强制"一个锚点最多一条 active 主占"。
 * 两个运营并发抢同一个稀缺锚点时，第二个 insert 必然撞主键/唯一约束而失败，
 * 从而只有一条方案落库，另一条得到明确的占用冲突。
 */
@Entity
@Table(name = "anchor_occupancy",
        uniqueConstraints = @UniqueConstraint(name = "uk_anchor_occupancy", columnNames = "anchor_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnchorOccupancy {

    /** 直接以锚点ID作主键：天然保证一个锚点至多一条主占记录 */
    @Id
    @Column(name = "anchor_id")
    private Long anchorId;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "route_code", nullable = false, length = 50)
    private String routeCode;

    @Column(name = "bind_id")
    private Long bindId;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
