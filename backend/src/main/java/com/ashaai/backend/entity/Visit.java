package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "visits")
public class Visit extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "child_id")
    private Child child;

    @ManyToOne
    @JoinColumn(name = "pregnancy_id")
    private Pregnancy pregnancy;

    @ManyToOne
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    private LocalDate visitDate;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal muacMm;

    private String notes;
    private String actionTaken;
    private String photoUrl;
    private String source;

    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public Pregnancy getPregnancy() { return pregnancy; }
    public void setPregnancy(Pregnancy pregnancy) { this.pregnancy = pregnancy; }
    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public BigDecimal getMuacMm() { return muacMm; }
    public void setMuacMm(BigDecimal muacMm) { this.muacMm = muacMm; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
