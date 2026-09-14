package com.px.base.repository;

import com.px.base.entity.Anchor;
import org.springframework.data.jpa.repository.JpaRepository;
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
    
    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.maxWindSpeed >= :windSpeed")
    List<Anchor> findByWindSpeedAdaptable(@Param("windSpeed") BigDecimal windSpeed);
    
    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.minWindSpeed <= :maxWind AND a.maxWindSpeed >= :minWind")
    List<Anchor> findByWindRange(@Param("minWind") BigDecimal minWind, @Param("maxWind") BigDecimal maxWind);
    
    @Query("SELECT a FROM Anchor a WHERE a.status = 1 AND a.maxWeight >= :minWeight")
    List<Anchor> findByMinWeight(@Param("minWeight") BigDecimal minWeight);
}
