package com.px.base.controller;

import com.px.base.dto.AdaptResultDTO;
import com.px.base.dto.BindDTO;
import com.px.base.dto.ResponseDTO;
import com.px.base.entity.AdaptLog;
import com.px.base.entity.RouteAnchor;
import com.px.base.service.AdaptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/adapt")
@RequiredArgsConstructor
public class AdaptController {
    private final AdaptService adaptService;

    @PostMapping("/bind")
    public ResponseDTO<AdaptResultDTO> bind(@RequestBody BindDTO dto) {
        try {
            AdaptResultDTO result = adaptService.bindAnchor(dto.getRouteId(), dto.getAnchorId());
            if (result.isValid()) {
                return ResponseDTO.success(result);
            } else {
                return ResponseDTO.error(400, result.getReason());
            }
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @PostMapping("/unbind")
    public ResponseDTO<AdaptResultDTO> unbind(@RequestBody BindDTO dto) {
        try {
            AdaptResultDTO result = adaptService.unbindAnchor(dto.getRouteId(), dto.getAnchorId());
            if (result.isValid()) {
                return ResponseDTO.success(result);
            } else {
                return ResponseDTO.error(400, result.getReason());
            }
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping("/check")
    public ResponseDTO<AdaptResultDTO> check(
            @RequestParam Long routeId,
            @RequestParam Long anchorId) {
        try {
            AdaptResultDTO result = adaptService.checkAdapt(routeId, anchorId);
            return ResponseDTO.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @PostMapping("/recheck/{routeId}")
    public ResponseDTO<AdaptResultDTO> recheck(@PathVariable Long routeId) {
        try {
            AdaptResultDTO result = adaptService.recheckRouteAnchors(routeId, null, null);
            return ResponseDTO.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping("/logs")
    public ResponseDTO<List<AdaptLog>> getLogs(
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long anchorId) {
        List<AdaptLog> logs;
        if (routeId != null) {
            logs = adaptService.getLogs(routeId);
        } else if (anchorId != null) {
            logs = adaptService.getLogsByAnchor(anchorId);
        } else {
            logs = adaptService.getLogs(null);
        }
        return ResponseDTO.success(logs);
    }

    @GetMapping("/bound/{routeId}")
    public ResponseDTO<List<RouteAnchor>> getBoundAnchors(@PathVariable Long routeId) {
        List<RouteAnchor> anchors = adaptService.getBoundAnchors(routeId);
        return ResponseDTO.success(anchors);
    }
}
