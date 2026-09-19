package com.ashaai.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SurveySubmissionResponseDto {

    private UUID id;
    private UUID templateId;
    private String moduleType;
    private UUID householdId;
    private UUID householdMemberId;
    private UUID ashaId;
    private String familyName;
    private OffsetDateTime submittedAt;
    private Object data;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }

    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }

    public UUID getHouseholdMemberId() { return householdMemberId; }
    public void setHouseholdMemberId(UUID householdMemberId) { this.householdMemberId = householdMemberId; }

    public UUID getAshaId() { return ashaId; }
    public void setAshaId(UUID ashaId) { this.ashaId = ashaId; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
