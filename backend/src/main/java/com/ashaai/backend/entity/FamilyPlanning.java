package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "family_planning")
public class FamilyPlanning extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_member_id", nullable = false)
    private HouseholdMember householdMember;

    private String method;
    private LocalDate methodStartDate;
    private LocalDate lastCheckinDate;
    private String adverseEffects;

    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public LocalDate getMethodStartDate() { return methodStartDate; }
    public void setMethodStartDate(LocalDate methodStartDate) { this.methodStartDate = methodStartDate; }
    public LocalDate getLastCheckinDate() { return lastCheckinDate; }
    public void setLastCheckinDate(LocalDate lastCheckinDate) { this.lastCheckinDate = lastCheckinDate; }
    public String getAdverseEffects() { return adverseEffects; }
    public void setAdverseEffects(String adverseEffects) { this.adverseEffects = adverseEffects; }
}
