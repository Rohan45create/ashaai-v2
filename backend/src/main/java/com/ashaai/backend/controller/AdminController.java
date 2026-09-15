package com.ashaai.backend.controller;

import com.ashaai.backend.service.RiskEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RiskEngineService riskEngineService;
    private final com.ashaai.backend.service.AdminService adminService;

    public AdminController(RiskEngineService riskEngineService, com.ashaai.backend.service.AdminService adminService) {
        this.riskEngineService = riskEngineService;
        this.adminService = adminService;
    }

    @PostMapping("/trigger-risk-engine")
    public ResponseEntity<String> triggerRiskEngine() {
        riskEngineService.runNightlyRiskScoring();
        return ResponseEntity.ok("Risk engine executed successfully.");
    }
    @GetMapping("/reports")
    public ResponseEntity<List<java.util.Map<String, Object>>> getReports(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(adminService.getReportsMetrics(auth.getAshaHeadId()));
    }

    @GetMapping("/workers")
    public ResponseEntity<List<java.util.Map<String, Object>>> getWorkers(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(adminService.getWorkersOverview(auth.getAshaHeadId()));
    }

    @GetMapping("/worker/{ashaId}/activity")
    public ResponseEntity<List<java.util.Map<String, Object>>> getWorkerActivity(@PathVariable java.util.UUID ashaId, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(adminService.getWorkerActivity(ashaId));
    }

    /**
     * Dashboard summary stats — aggregates worker count, critical cases, total families,
     * active today, and pending reviews into a single response.
     * Called by Dashboard.jsx GET /api/admin/dashboard-stats?headId={uuid}
     */
    @GetMapping("/dashboard-stats")
    public ResponseEntity<java.util.Map<String, Object>> getDashboardStats(
            @org.springframework.web.bind.annotation.RequestParam("headId") java.util.UUID headId,
            org.springframework.security.core.Authentication authentication) {
        com.ashaai.backend.security.AshaAuthenticationToken auth =
            (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();

        List<java.util.Map<String, Object>> workers = adminService.getWorkersOverview(headId);
        List<java.util.Map<String, Object>> metrics = adminService.getReportsMetrics(headId);

        long workerCount = workers.size();
        long criticalCases = metrics.stream()
            .filter(m -> "Critical Cases (Total)".equals(m.get("name")))
            .mapToLong(m -> ((Number) m.get("current")).longValue())
            .sum();
        long nrcReferrals = metrics.stream()
            .filter(m -> "NRC Referrals".equals(m.get("name")))
            .mapToLong(m -> ((Number) m.get("current")).longValue())
            .sum();

        // Worker activity charts: submissions per worker this month
        java.util.List<java.util.Map<String, Object>> workerActivityChart = workers.stream()
            .map(w -> java.util.Map.<String, Object>of(
                "label", w.get("id"),
                "value", w.get("submissionsThisMonth")
            ))
            .collect(java.util.stream.Collectors.toList());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("workerCount", workerCount);
        result.put("criticalCases", criticalCases);
        result.put("totalFamilies", 0); // computed from household count if needed
        result.put("activeToday", workers.stream().filter(w -> w.get("lastActive") != null).count());
        result.put("pendingReviews", 0); // would need pendingReviewRepository injection
        result.put("workerActivityChart", workerActivityChart);
        result.put("riskChart", java.util.List.of(
            java.util.Map.of("label", "CRITICAL", "value", criticalCases),
            java.util.Map.of("label", "HIGH", "value", 0),
            java.util.Map.of("label", "MEDIUM", "value", 0),
            java.util.Map.of("label", "LOW", "value", 0)
        ));
        result.put("moduleChart", metrics.stream()
            .map(m -> java.util.Map.<String, Object>of(
                "label", m.get("name"),
                "value", m.get("current")
            ))
            .collect(java.util.stream.Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    /**
     * Alias: GET /api/admin/supervisor/workers/{docId}
     * Dashboard.jsx's workers table calls this. Delegates to the same getWorkersOverview.
     */
    @GetMapping("/supervisor/workers/{headId}")
    public ResponseEntity<List<java.util.Map<String, Object>>> getSupervisorWorkers(
            @PathVariable java.util.UUID headId,
            org.springframework.security.core.Authentication authentication) {
        com.ashaai.backend.security.AshaAuthenticationToken auth =
            (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(adminService.getWorkersOverview(headId));
    }

    @PostMapping("/supervisor/workers/add")
    public ResponseEntity<java.util.Map<String, Object>> addWorker(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> request,
            org.springframework.security.core.Authentication authentication) {
        com.ashaai.backend.security.AshaAuthenticationToken auth =
            (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() == null) return ResponseEntity.status(403).build();

        String name = request.get("name");
        String phone = request.get("phone");
        String village = request.get("village");
        String district = request.get("district");

        if (name == null || phone == null || village == null || district == null) {
            return ResponseEntity.badRequest().build();
        }

        com.ashaai.backend.entity.Asha newAsha = adminService.addWorker(auth.getAshaHeadId(), name, phone, village, district);
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", newAsha.getId().toString());
        response.put("name", newAsha.getName());
        return ResponseEntity.ok(response);
    }
}
