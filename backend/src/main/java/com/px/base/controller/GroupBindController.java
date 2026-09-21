package com.px.base.controller;

import com.px.base.dto.GroupBindRequestDTO;
import com.px.base.dto.GroupRehearseResultDTO;
import com.px.base.dto.GroupSubmitResultDTO;
import com.px.base.dto.ResponseDTO;
import com.px.base.rule.GroupBindingEvaluator;
import com.px.base.service.GroupBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 航线成组配桩：先预演体检、确认无误后再提交落库。
 * 预演 /submit 走同一套判定引擎与同一套 ALL_OR_NOTHING 策略。
 */
@RestController
@RequestMapping("/api/group-binding")
@RequiredArgsConstructor
public class GroupBindController {

    private final GroupBindService groupBindService;
    private final GroupBindingEvaluator evaluator;

    /** 风级 -> 最低承重 对照表，供页面展示规则 */
    @GetMapping("/rules")
    public ResponseDTO<Map<String, BigDecimal>> rules() {
        return ResponseDTO.success(evaluator.ruleTable());
    }

    /** 预演体检：不写库，只给出整套与逐条结论 */
    @PostMapping("/rehearse")
    public ResponseDTO<GroupRehearseResultDTO> rehearse(@RequestBody GroupBindRequestDTO dto) {
        try {
            return ResponseDTO.success(groupBindService.rehearse(dto));
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }

    /**
     * 提交落库：合格整套原子落库；不合格/冲突/落库失败均 committed=false 且一条不落，
     * data.rehearsal 内带逐条原因，供运营对照。
     */
    @PostMapping("/submit")
    public ResponseDTO<GroupSubmitResultDTO> submit(@RequestBody GroupBindRequestDTO dto) {
        try {
            return ResponseDTO.success(groupBindService.submit(dto));
        } catch (IllegalArgumentException e) {
            return ResponseDTO.error(400, e.getMessage());
        }
    }
}
