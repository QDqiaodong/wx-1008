package com.px.base.repository;

import com.px.base.entity.RouteAnchor;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
