package com.ashaai.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SurveySubmissionDto {

    @JsonAlias({"template_id", "templateId"})
    private UUID templateId;

    @JsonAlias({"module_key", "moduleKey"})
    private String moduleKey;

    @JsonAlias({"household_id", "householdId"})
    private UUID householdId;

    @JsonAlias({"household_member_id", "householdMemberId", "member_id", "memberId"})
    private UUID householdMemberId;

    @JsonAlias({"asha_id", "ashaId"})
    private UUID ashaId;

    private Object data;

    @JsonAlias({"referred_to_nrc", "referredToNrc"})
    private Boolean referredToNrc;

    @JsonAlias({"submitted_at", "submittedAt"})
    private OffsetDateTime submittedAt;

    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }

    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }

    public UUID getHouseholdMemberId() { return householdMemberId; }
    public void setHouseholdMemberId(UUID householdMemberId) { this.householdMemberId = householdMemberId; }

    public UUID getAshaId() { return ashaId; }
    public void setAshaId(UUID ashaId) { this.ashaId = ashaId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public Boolean getReferredToNrc() { return referredToNrc; }
    public void setReferredToNrc(Boolean referredToNrc) { this.referredToNrc = referredToNrc; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
}
