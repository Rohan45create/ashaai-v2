package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "district_sequences")
@IdClass(DistrictSequenceId.class)
public class DistrictSequence {

    @Id
    private String district;

    @Id
    private int year;

    private int currentValue;

    @Version
    private Long version;

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getCurrentValue() { return currentValue; }
    public void setCurrentValue(int currentValue) { this.currentValue = currentValue; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
