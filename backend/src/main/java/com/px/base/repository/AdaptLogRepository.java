package com.px.base.repository;

import com.px.base.entity.AdaptLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdaptLogRepository extends JpaRepository<AdaptLog, Long> {
    List<AdaptLog> findByRouteId(Long routeId);
    List<AdaptLog> findByAnchorId(Long anchorId);
    List<AdaptLog> findByOperationType(String operationType);
    List<AdaptLog> findByRouteIdAndAnchorId(Long routeId, Long anchorId);
    Page<AdaptLog> findByRouteId(Long routeId, Pageable pageable);
    Page<AdaptLog> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
