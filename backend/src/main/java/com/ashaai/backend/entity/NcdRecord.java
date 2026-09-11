package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ncd_records")
public class NcdRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_member_id", nullable = false)
    private HouseholdMember householdMember;

    private String condition;

    @JdbcTypeCode(SqlTypes.JSON)
    private String readings;

    private String medication;
    private String compliance;

    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getReadings() { return readings; }
    public void setReadings(String readings) { this.readings = readings; }
    public String getMedication() { return medication; }
    public void setMedication(String medication) { this.medication = medication; }
    public String getCompliance() { return compliance; }
    public void setCompliance(String compliance) { this.compliance = compliance; }
}
