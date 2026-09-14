package com.px.base.repository;

import com.px.base.entity.FlightRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRouteRepository extends JpaRepository<FlightRoute, Long> {
    Optional<FlightRoute> findByRouteCode(String routeCode);
    boolean existsByRouteCode(String routeCode);
    List<FlightRoute> findByStatus(Integer status);
    List<FlightRoute> findByRouteGroup(String routeGroup);
    List<FlightRoute> findByRouteGroupAndStatus(String routeGroup, Integer status);
}
