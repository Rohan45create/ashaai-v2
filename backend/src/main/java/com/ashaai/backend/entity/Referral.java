package com.ashaai.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "referrals")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Referral extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "child_id")
    private Child child;

    @ManyToOne
    @JoinColumn(name = "household_member_id")
    private HouseholdMember householdMember;

    @ManyToOne
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    private String reason;
    private String status;
    private OffsetDateTime referredDate;

    private LocalDate admittedDate;
    private LocalDate dischargedDate;
    private LocalDate followUpDueDate;
    private String nrcName;

    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getReferredDate() { return referredDate; }
    public void setReferredDate(OffsetDateTime referredDate) { this.referredDate = referredDate; }
    public LocalDate getAdmittedDate() { return admittedDate; }
    public void setAdmittedDate(LocalDate admittedDate) { this.admittedDate = admittedDate; }
    public LocalDate getDischargedDate() { return dischargedDate; }
    public void setDischargedDate(LocalDate dischargedDate) { this.dischargedDate = dischargedDate; }
    public LocalDate getFollowUpDueDate() { return followUpDueDate; }
    public void setFollowUpDueDate(LocalDate followUpDueDate) { this.followUpDueDate = followUpDueDate; }
    public String getNrcName() { return nrcName; }
    public void setNrcName(String nrcName) { this.nrcName = nrcName; }

    @JsonProperty("childId")
    public UUID getChildId() {
        return child != null ? child.getId() : null;
    }

    @JsonProperty("householdMemberId")
    public UUID getHouseholdMemberId() {
        if (householdMember != null) {
            return householdMember.getId();
        }
        if (child != null && child.getHouseholdMember() != null) {
            return child.getHouseholdMember().getId();
        }
        return null;
    }

    @JsonProperty("ashaId")
    public UUID getAshaId() {
        return asha != null ? asha.getId() : null;
    }

    @JsonProperty("ashaName")
    public String getAshaName() {
        return asha != null ? asha.getName() : null;
    }

    @JsonProperty("riskScore")
    public Integer getRiskScore() {
        return child != null ? child.getRiskScore() : null;
    }

    @JsonProperty("childName")
    public String getChildName() {
        if (child != null && child.getHouseholdMember() != null && child.getHouseholdMember().getName() != null) {
            return child.getHouseholdMember().getName();
        }
        if (householdMember != null && householdMember.getName() != null) {
            return householdMember.getName();
        }
        return "Child";
    }

    @JsonProperty("riskLevel")
    public String getRiskLevel() {
        if (child != null && child.getRiskLevel() != null) {
            return child.getRiskLevel();
        }
        return "CRITICAL";
    }

    @JsonProperty("village")
    public String getVillage() {
        if (child != null && child.getHouseholdMember() != null && child.getHouseholdMember().getHousehold() != null) {
            return child.getHouseholdMember().getHousehold().getAddress();
        }
        if (householdMember != null && householdMember.getHousehold() != null) {
            return householdMember.getHousehold().getAddress();
        }
        return "";
    }
}
