package com.ashaai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "death_records")
public class DeathRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_member_id", nullable = false)
    private HouseholdMember householdMember;

    private LocalDate dateOfDeath;
    private String cause;
    private Boolean maternalDeathFlag;

    @Column(name = "under5_death_flag")
    private Boolean under5DeathFlag;

    private String placeOfDeath;
    private Boolean headAcknowledged;

    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public LocalDate getDateOfDeath() { return dateOfDeath; }
    public void setDateOfDeath(LocalDate dateOfDeath) { this.dateOfDeath = dateOfDeath; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public Boolean getMaternalDeathFlag() { return maternalDeathFlag; }
    public void setMaternalDeathFlag(Boolean maternalDeathFlag) { this.maternalDeathFlag = maternalDeathFlag; }
    public Boolean getUnder5DeathFlag() { return under5DeathFlag; }
    public void setUnder5DeathFlag(Boolean under5DeathFlag) { this.under5DeathFlag = under5DeathFlag; }
    public String getPlaceOfDeath() { return placeOfDeath; }
    public void setPlaceOfDeath(String placeOfDeath) { this.placeOfDeath = placeOfDeath; }
    public Boolean getHeadAcknowledged() { return headAcknowledged; }
    public void setHeadAcknowledged(Boolean headAcknowledged) { this.headAcknowledged = headAcknowledged; }
}
