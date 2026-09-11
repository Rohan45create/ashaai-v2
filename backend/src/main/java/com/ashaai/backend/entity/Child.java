package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "children")
public class Child extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_member_id", nullable = false)
    private HouseholdMember householdMember;

    @ManyToOne
    @JoinColumn(name = "mother_member_id")
    private HouseholdMember motherMember;

    @ManyToOne
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    private BigDecimal currentWeightKg;
    private BigDecimal currentHeightCm;
    private BigDecimal muacMm;

    private String malnutritionGrade;
    private String nrcReferralStatus;
    private Integer breastfeedingCessationMonths;
    private Boolean siblingMalnutritionHistory;

    private Integer ageMonths;
    private LocalDate lastVisitDate;
    private Boolean isOrphan;
    private Boolean hasParents;

    private Integer riskScore;
    private String riskLevel;
    private String riskPrimaryDriver;
    private String riskRecommendedAction;
    private OffsetDateTime riskUpdatedAt;

    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }

    public HouseholdMember getMotherMember() { return motherMember; }
    public void setMotherMember(HouseholdMember motherMember) { this.motherMember = motherMember; }

    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }

    public BigDecimal getCurrentWeightKg() { return currentWeightKg; }
    public void setCurrentWeightKg(BigDecimal currentWeightKg) { this.currentWeightKg = currentWeightKg; }

    public BigDecimal getCurrentHeightCm() { return currentHeightCm; }
    public void setCurrentHeightCm(BigDecimal currentHeightCm) { this.currentHeightCm = currentHeightCm; }

    public BigDecimal getMuacMm() { return muacMm; }
    public void setMuacMm(BigDecimal muacMm) { this.muacMm = muacMm; }

    public String getMalnutritionGrade() { return malnutritionGrade; }
    public void setMalnutritionGrade(String malnutritionGrade) { this.malnutritionGrade = malnutritionGrade; }

    public String getNrcReferralStatus() { return nrcReferralStatus; }
    public void setNrcReferralStatus(String nrcReferralStatus) { this.nrcReferralStatus = nrcReferralStatus; }

    public Integer getBreastfeedingCessationMonths() { return breastfeedingCessationMonths; }
    public void setBreastfeedingCessationMonths(Integer breastfeedingCessationMonths) { this.breastfeedingCessationMonths = breastfeedingCessationMonths; }

    public Boolean getSiblingMalnutritionHistory() { return siblingMalnutritionHistory; }
    public void setSiblingMalnutritionHistory(Boolean siblingMalnutritionHistory) { this.siblingMalnutritionHistory = siblingMalnutritionHistory; }

    public Integer getAgeMonths() { return ageMonths; }
    public void setAgeMonths(Integer ageMonths) { this.ageMonths = ageMonths; }

    public LocalDate getLastVisitDate() { return lastVisitDate; }
    public void setLastVisitDate(LocalDate lastVisitDate) { this.lastVisitDate = lastVisitDate; }

    public Boolean getIsOrphan() { return isOrphan; }
    public void setIsOrphan(Boolean isOrphan) { this.isOrphan = isOrphan; }

    public Boolean getHasParents() { return hasParents; }
    public void setHasParents(Boolean hasParents) { this.hasParents = hasParents; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRiskPrimaryDriver() { return riskPrimaryDriver; }
    public void setRiskPrimaryDriver(String riskPrimaryDriver) { this.riskPrimaryDriver = riskPrimaryDriver; }

    public String getRiskRecommendedAction() { return riskRecommendedAction; }
    public void setRiskRecommendedAction(String riskRecommendedAction) { this.riskRecommendedAction = riskRecommendedAction; }

    public OffsetDateTime getRiskUpdatedAt() { return riskUpdatedAt; }
    public void setRiskUpdatedAt(OffsetDateTime riskUpdatedAt) { this.riskUpdatedAt = riskUpdatedAt; }
}
