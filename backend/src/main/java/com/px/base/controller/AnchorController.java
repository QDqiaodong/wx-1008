package com.px.base.controller;

import com.px.base.dto.AnchorDTO;
import com.px.base.dto.ResponseDTO;
import com.px.base.entity.Anchor;
import com.px.base.service.AnchorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/anchor")
@RequiredArgsConstructor
public class AnchorController {
    private final AnchorService anchorService;

    @PostMapping
    public ResponseDTO<Anchor> create(@RequestBody AnchorDTO dto) {
        try {
            Anchor anchor = anchorService.create(dto);
            return ResponseDTO.success(anchor);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<Anchor>> list() {
        List<Anchor> anchors = anchorService.findAll();
        return ResponseDTO.success(anchors);
    }

    @GetMapping("/{id}")
    public ResponseDTO<Anchor> getById(@PathVariable Long id) {
        return anchorService.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error(404, "锚点不存在"));
    }

    @PutMapping("/{id}")
    public ResponseDTO<Anchor> update(@PathVariable Long id, @RequestBody AnchorDTO dto) {
        try {
            Anchor anchor = anchorService.update(id, dto);
            return ResponseDTO.success(anchor);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        try {
            anchorService.delete(id);
            return ResponseDTO.success(null);
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    @GetMapping("/filter")
    public ResponseDTO<List<Anchor>> filter(
            @RequestParam(required = false) BigDecimal minWind,
            @RequestParam(required = false) BigDecimal maxWind) {
        if (minWind != null && maxWind != null) {
            List<Anchor> anchors = anchorService.filterByWindRange(minWind, maxWind);
            return ResponseDTO.success(anchors);
        }
        return ResponseDTO.success(anchorService.findAll());
    }
}
