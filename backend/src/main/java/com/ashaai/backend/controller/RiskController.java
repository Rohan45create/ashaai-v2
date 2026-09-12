package com.ashaai.backend.controller;

import com.ashaai.backend.dto.PriorityItemDto;
import com.ashaai.backend.entity.Child;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.service.PriorityListService;
import com.ashaai.backend.service.RiskEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final PriorityListService priorityListService;
    private final RiskEngineService riskEngineService;
    private final ChildRepository childRepository;

    public RiskController(
        PriorityListService priorityListService,
        RiskEngineService riskEngineService,
        ChildRepository childRepository
    ) {
        this.priorityListService = priorityListService;
        this.riskEngineService = riskEngineService;
        this.childRepository = childRepository;
    }

    /**
     * Endpoint called by PriorityList.jsx:
     * GET /api/risk/priority/{ashaId}
     * Returns the 3-list merged, deduplicated, and ranked priority list.
     */
    @GetMapping("/priority/{ashaId}")
    public ResponseEntity<List<PriorityItemDto>> getPriorityList(@PathVariable UUID ashaId) {
        List<PriorityItemDto> mergedList = priorityListService.getMergedPriorityList(ashaId);
        return ResponseEntity.ok(mergedList);
    }

    /**
     * Endpoint called by PriorityList.jsx:
     * POST /api/risk/calculate-now/{ashaId}
     * Re-calculates risk scores for all children assigned to this ASHA worker.
     */
    @PostMapping("/calculate-now/{ashaId}")
    public ResponseEntity<Map<String, Object>> calculateNow(@PathVariable UUID ashaId) {
        List<Child> children = childRepository.findByAshaId(ashaId);
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Child c : children) {
            riskEngineService.evaluateChild(c, today, false);
            count++;
        }
        return ResponseEntity.ok(Map.of(
            "status", "COMPLETED",
            "ashaId", ashaId.toString(),
            "calculatedCount", count,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Endpoint called by Home.jsx:
     * GET /api/risk/critical/{ashaId}
     * Returns only the CRITICAL priority cases for the ASHA's dashboard.
     */
    @GetMapping("/critical/{ashaId}")
    public ResponseEntity<List<PriorityItemDto>> getCriticalCases(@PathVariable UUID ashaId) {
        List<PriorityItemDto> mergedList = priorityListService.getMergedPriorityList(ashaId);
        List<PriorityItemDto> criticalOnly = mergedList.stream()
            .filter(item -> "CRITICAL".equalsIgnoreCase(item.riskLevel()))
            .toList();
        return ResponseEntity.ok(criticalOnly);
    }
}
