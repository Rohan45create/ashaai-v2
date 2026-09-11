package com.ashaai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "pregnancies")
public class Pregnancy extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "mother_member_id", nullable = false)
    private HouseholdMember motherMember;

    @ManyToOne
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    private String status;
    private LocalDate lmp;
    private LocalDate edd;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "haemoglobin_gdl")
    private BigDecimal haemoglobinGdl;

    @Column(name = "anc1_date")
    private LocalDate anc1Date;

    @Column(name = "anc2_date")
    private LocalDate anc2Date;

    @Column(name = "anc3_date")
    private LocalDate anc3Date;

    @Column(name = "anc4_date")
    private LocalDate anc4Date;

    @Column(name = "last_anc_date")
    private LocalDate lastAncDate;

    @Column(name = "high_risk_flag")
    private Boolean highRiskFlag;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "high_risk_reasons")
    private List<String> highRiskReasons;

    @Column(name = "delivery_institution")
    private String deliveryInstitution;

    @Column(name = "jsy_benefit")
    private Boolean jsyBenefit;

    // --- explicit getters and setters ---

    public HouseholdMember getMotherMember() { return motherMember; }
    public void setMotherMember(HouseholdMember motherMember) { this.motherMember = motherMember; }

    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }

    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getLmp() { return lmp; }
    public void setLmp(LocalDate lmp) { this.lmp = lmp; }

    public LocalDate getEdd() { return edd; }
    public void setEdd(LocalDate edd) { this.edd = edd; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public BigDecimal getHaemoglobinGdl() { return haemoglobinGdl; }
    public void setHaemoglobinGdl(BigDecimal haemoglobinGdl) { this.haemoglobinGdl = haemoglobinGdl; }

    public LocalDate getAnc1Date() { return anc1Date; }
    public void setAnc1Date(LocalDate anc1Date) { this.anc1Date = anc1Date; }

    public LocalDate getAnc2Date() { return anc2Date; }
    public void setAnc2Date(LocalDate anc2Date) { this.anc2Date = anc2Date; }

    public LocalDate getAnc3Date() { return anc3Date; }
    public void setAnc3Date(LocalDate anc3Date) { this.anc3Date = anc3Date; }

    public LocalDate getAnc4Date() { return anc4Date; }
    public void setAnc4Date(LocalDate anc4Date) { this.anc4Date = anc4Date; }

    public LocalDate getLastAncDate() { return lastAncDate; }
    public void setLastAncDate(LocalDate lastAncDate) { this.lastAncDate = lastAncDate; }

    public Boolean getHighRiskFlag() { return highRiskFlag; }
    public void setHighRiskFlag(Boolean highRiskFlag) { this.highRiskFlag = highRiskFlag; }

    public List<String> getHighRiskReasons() { return highRiskReasons; }
    public void setHighRiskReasons(List<String> highRiskReasons) { this.highRiskReasons = highRiskReasons; }

    public String getDeliveryInstitution() { return deliveryInstitution; }
    public void setDeliveryInstitution(String deliveryInstitution) { this.deliveryInstitution = deliveryInstitution; }

    public Boolean getJsyBenefit() { return jsyBenefit; }
    public void setJsyBenefit(Boolean jsyBenefit) { this.jsyBenefit = jsyBenefit; }
}
