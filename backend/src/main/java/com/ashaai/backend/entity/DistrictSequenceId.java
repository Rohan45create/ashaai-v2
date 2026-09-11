package com.ashaai.backend.entity;

import java.io.Serializable;
import java.util.Objects;

public class DistrictSequenceId implements Serializable {
    private String district;
    private int year;

    public DistrictSequenceId() {}

    public DistrictSequenceId(String district, int year) {
        this.district = district;
        this.year = year;
    }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DistrictSequenceId that = (DistrictSequenceId) o;
        return year == that.year && Objects.equals(district, that.district);
    }

    @Override
    public int hashCode() {
        return Objects.hash(district, year);
    }
}
