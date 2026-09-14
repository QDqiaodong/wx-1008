package com.px.base.controller;

import com.px.base.dto.ResponseDTO;
import com.px.base.dto.RouteDTO;
import com.px.base.entity.FlightRoute;
import com.px.base.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {
    private final RouteService routeService;

    @PostMapping
    public ResponseDTO<FlightRoute> create(@RequestBody RouteDTO dto) {
        try {
            FlightRoute route = routeService.create(dto);
            return ResponseDTO.success(route);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<FlightRoute>> list() {
        List<FlightRoute> routes = routeService.findAll();
        return ResponseDTO.success(routes);
    }

    @GetMapping("/{id}")
    public ResponseDTO<FlightRoute> getById(@PathVariable Long id) {
        return routeService.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error(404, "航线不存在"));
    }

    @PutMapping("/{id}")
    public ResponseDTO<FlightRoute> update(@PathVariable Long id, @RequestBody RouteDTO dto) {
        try {
            FlightRoute route = routeService.update(id, dto);
            return ResponseDTO.success(route);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        try {
            routeService.delete(id);
            return ResponseDTO.success(null);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping("/groups")
    public ResponseDTO<List<String>> getGroups() {
        List<String> groups = routeService.findAllGroups();
        return ResponseDTO.success(groups);
    }
}
