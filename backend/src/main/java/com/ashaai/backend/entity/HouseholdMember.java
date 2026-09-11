package com.ashaai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "household_members")
public class HouseholdMember extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    private Integer serialNumber;
    private String name;
    private String gender;
    private LocalDate dateOfBirth;
    private String relationshipToHead;
    private String maritalStatus;

    private String aadhaarEncrypted;

    @Column(name = "aadhaar_last4")
    private String aadhaarLast4;

    private String mobileNumber;
    private String abhaIdEncrypted;
    private String birthRegisterSerial;
    private String reasonRemoved;

    private String temporaryId;
    private String identityStatus;

    private Boolean hasGeneticCondition;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> geneticConditions;

    private String geneticConditionNotes;

    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }

    public Integer getSerialNumber() { return serialNumber; }
    public void setSerialNumber(Integer serialNumber) { this.serialNumber = serialNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getRelationshipToHead() { return relationshipToHead; }
    public void setRelationshipToHead(String relationshipToHead) { this.relationshipToHead = relationshipToHead; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getAadhaarEncrypted() { return aadhaarEncrypted; }
    public void setAadhaarEncrypted(String aadhaarEncrypted) { this.aadhaarEncrypted = aadhaarEncrypted; }

    public String getAadhaarLast4() { return aadhaarLast4; }
    public void setAadhaarLast4(String aadhaarLast4) { this.aadhaarLast4 = aadhaarLast4; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getAbhaIdEncrypted() { return abhaIdEncrypted; }
    public void setAbhaIdEncrypted(String abhaIdEncrypted) { this.abhaIdEncrypted = abhaIdEncrypted; }

    public String getBirthRegisterSerial() { return birthRegisterSerial; }
    public void setBirthRegisterSerial(String birthRegisterSerial) { this.birthRegisterSerial = birthRegisterSerial; }

    public String getReasonRemoved() { return reasonRemoved; }
    public void setReasonRemoved(String reasonRemoved) { this.reasonRemoved = reasonRemoved; }

    public String getTemporaryId() { return temporaryId; }
    public void setTemporaryId(String temporaryId) { this.temporaryId = temporaryId; }

    public String getIdentityStatus() { return identityStatus; }
    public void setIdentityStatus(String identityStatus) { this.identityStatus = identityStatus; }

    public Boolean getHasGeneticCondition() { return hasGeneticCondition; }
    public void setHasGeneticCondition(Boolean hasGeneticCondition) { this.hasGeneticCondition = hasGeneticCondition; }

    public List<String> getGeneticConditions() { return geneticConditions; }
    public void setGeneticConditions(List<String> geneticConditions) { this.geneticConditions = geneticConditions; }

    public String getGeneticConditionNotes() { return geneticConditionNotes; }
    public void setGeneticConditionNotes(String geneticConditionNotes) { this.geneticConditionNotes = geneticConditionNotes; }
}
