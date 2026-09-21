package com.px.base.repository;

import com.px.base.entity.RouteAnchor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteAnchorRepository extends JpaRepository<RouteAnchor, Long> {
    List<RouteAnchor> findByRouteIdAndStatus(Long routeId, Integer status);
    List<RouteAnchor> findByAnchorIdAndStatus(Long anchorId, Integer status);
    Optional<RouteAnchor> findByRouteIdAndAnchorId(Long routeId, Long anchorId);
    boolean existsByRouteIdAndAnchorIdAndStatus(Long routeId, Long anchorId, Integer status);
    int countByRouteIdAndStatus(Long routeId, Integer status);

    /**
     * 查找锚点当前的有效绑定关系（status=1）。一个锚点同一时刻最多一条。
     */
    Optional<RouteAnchor> findFirstByAnchorIdAndStatus(Long anchorId, Integer status);

    /**
     * 单锚点唯一占用判定：返回该锚点当前服役的“启用航线”ID。
     * 只有绑定关系有效（route_anchor.status=1）且航线启用（flight_route.status=1）才算真正占用。
     */
    @Query(value = "SELECT ra.route_id FROM route_anchor ra " +
            "JOIN flight_route fr ON fr.id = ra.route_id " +
            "WHERE ra.anchor_id = :anchorId AND ra.status = 1 AND fr.status = 1 " +
            "LIMIT 1", nativeQuery = true)
    Long findActiveOccupyingRouteId(@Param("anchorId") Long anchorId);
}
