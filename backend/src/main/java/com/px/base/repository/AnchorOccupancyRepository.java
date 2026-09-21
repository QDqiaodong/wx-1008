package com.px.base.repository;

import com.px.base.entity.AnchorOccupancy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnchorOccupancyRepository extends JpaRepository<AnchorOccupancy, Long> {

    /**
     * 悲观行锁：在事务内对该锚点的主占行加锁，串行化并发占用判定。
     * 行不存在时数据库会加间隙锁/下一键锁（MySQL/InnoDB），H2 也支持 FOR UPDATE，
     * 配合主键唯一约束形成"先锁后插、插入撞约束兜底"的双保险。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AnchorOccupancy o where o.anchorId = :anchorId")
    Optional<AnchorOccupancy> findByAnchorIdForUpdate(@Param("anchorId") Long anchorId);

    Optional<AnchorOccupancy> findByAnchorId(Long anchorId);

    List<AnchorOccupancy> findByRouteId(Long routeId);

    long countByRouteId(Long routeId);
}
