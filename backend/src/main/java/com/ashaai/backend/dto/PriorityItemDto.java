package com.ashaai.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for Priority List items.
 * Matches the exact contract expected by PriorityList.jsx.
 */
public record PriorityItemDto(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("type") String type, // "child" or "pregnancy"
    @JsonProperty("risk_level") String riskLevel, // "CRITICAL", "HIGH", "MEDIUM", "LOW"
    @JsonProperty("risk_score") int riskScore,
    @JsonProperty("age_months") int ageMonths,
    @JsonProperty("primary_driver") String primaryDriver,
    @JsonProperty("recommended_action") String recommendedAction,
    @JsonProperty("days_overdue") int daysOverdue,
    @JsonProperty("village") String village,
    @JsonProperty("household_id") String householdId,
    @JsonProperty("person_id") String personId
) {}
