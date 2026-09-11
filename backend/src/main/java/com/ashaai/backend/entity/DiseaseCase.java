package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "disease_cases")
public class DiseaseCase extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_member_id", nullable = false)
    private HouseholdMember householdMember;

    private String diseaseType;
    private LocalDate onsetDate;
    private String symptoms;
    private Boolean sampleCollected;
    private String result;
    private Boolean treatmentStarted;
    private Integer durationDays;
    private Boolean referredToPhc;

    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public String getDiseaseType() { return diseaseType; }
    public void setDiseaseType(String diseaseType) { this.diseaseType = diseaseType; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public Boolean getSampleCollected() { return sampleCollected; }
    public void setSampleCollected(Boolean sampleCollected) { this.sampleCollected = sampleCollected; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Boolean getTreatmentStarted() { return treatmentStarted; }
    public void setTreatmentStarted(Boolean treatmentStarted) { this.treatmentStarted = treatmentStarted; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Boolean getReferredToPhc() { return referredToPhc; }
    public void setReferredToPhc(Boolean referredToPhc) { this.referredToPhc = referredToPhc; }
}
