package com.px.base.repository;

import com.px.base.entity.Anchor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnchorRepository extends JpaRepository<Anchor, Long> {
    Optional<Anchor> findByAnchorCode(String anchorCode);
    boolean existsByAnchorCode(String anchorCode);
    List<Anchor> findByStatus(Integer status);

    /** 成组配桩并发控制：对锚点行加悲观写锁，串行化同一稀缺锚点的争抢 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anchor a where a.id = :id")
    Optional<Anchor> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.maxWindSpeed >= :windSpeed")
    List<Anchor> findByWindSpeedAdaptable(@Param("windSpeed") BigDecimal windSpeed);
    
    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.minWindSpeed <= :maxWind AND a.maxWindSpeed >= :minWind")
    List<Anchor> findByWindRange(@Param("minWind") BigDecimal minWind, @Param("maxWind") BigDecimal maxWind);
    
    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.maxWeight >= :minWeight")
    List<Anchor> findByMinWeight(@Param("minWeight") BigDecimal minWeight);
}
