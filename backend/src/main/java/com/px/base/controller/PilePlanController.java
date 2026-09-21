package com.px.base.controller;

import com.px.base.dto.PilePlanResultDTO;
import com.px.base.dto.PilePlanSubmitDTO;
import com.px.base.dto.ResponseDTO;
import com.px.base.service.PilePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 航线成组配桩：预演体检 + 提交落库。两个接口共用后端同一套判定，不会出现预演说能配、提交放行别的。
 */
@RestController
@RequestMapping("/api/pile-plan")
@RequiredArgsConstructor
public class PilePlanController {

    private final PilePlanService pilePlanService;

    /** 预演：只体检，不落库。无论通过与否都返回 200，合格与否看 data.valid 与逐条原因。 */
    @PostMapping("/preview")
    public ResponseDTO<PilePlanResultDTO> preview(@RequestBody PilePlanSubmitDTO dto) {
        if (dto == null || dto.getRouteId() == null) {
            return ResponseDTO.error(400, "请先选择一条航线");
        }
        return ResponseDTO.success(pilePlanService.preview(dto));
    }

    /** 提交：同一套判定复核后整组落库；不合格整组回退，绝不只落合格的。 */
    @PostMapping("/submit")
    public ResponseDTO<PilePlanResultDTO> submit(@RequestBody PilePlanSubmitDTO dto) {
        if (dto == null || dto.getRouteId() == null) {
            return ResponseDTO.error(400, "请先选择一条航线");
        }
        if (dto.getAnchorIds() == null || dto.getAnchorIds().isEmpty()) {
            return ResponseDTO.error(400, "请至少勾选一个锚点");
        }
        return ResponseDTO.success(pilePlanService.submit(dto));
    }
}
