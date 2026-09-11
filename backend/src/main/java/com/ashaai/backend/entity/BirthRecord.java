package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "birth_records")
public class BirthRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne
    @JoinColumn(name = "household_member_id")
    private HouseholdMember householdMember;

    private String childName;
    private LocalDate dob;
    private LocalTime timeOfBirth;
    private String placeOfBirth;
    private String gender;
    private BigDecimal birthWeightKg;

    @ManyToOne
    @JoinColumn(name = "mother_member_id")
    private HouseholdMember motherMember;

    @ManyToOne
    @JoinColumn(name = "father_member_id")
    private HouseholdMember fatherMember;

    private String deliveryType;
    private String birthAttendant;
    private Boolean breastfedWithinHour;
    private String registrationStatus;

    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }
    public HouseholdMember getHouseholdMember() { return householdMember; }
    public void setHouseholdMember(HouseholdMember householdMember) { this.householdMember = householdMember; }
    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public LocalTime getTimeOfBirth() { return timeOfBirth; }
    public void setTimeOfBirth(LocalTime timeOfBirth) { this.timeOfBirth = timeOfBirth; }
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public BigDecimal getBirthWeightKg() { return birthWeightKg; }
    public void setBirthWeightKg(BigDecimal birthWeightKg) { this.birthWeightKg = birthWeightKg; }
    public HouseholdMember getMotherMember() { return motherMember; }
    public void setMotherMember(HouseholdMember motherMember) { this.motherMember = motherMember; }
    public HouseholdMember getFatherMember() { return fatherMember; }
    public void setFatherMember(HouseholdMember fatherMember) { this.fatherMember = fatherMember; }
    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }
    public String getBirthAttendant() { return birthAttendant; }
    public void setBirthAttendant(String birthAttendant) { this.birthAttendant = birthAttendant; }
    public Boolean getBreastfedWithinHour() { return breastfedWithinHour; }
    public void setBreastfedWithinHour(Boolean breastfedWithinHour) { this.breastfedWithinHour = breastfedWithinHour; }
    public String getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(String registrationStatus) { this.registrationStatus = registrationStatus; }
}
