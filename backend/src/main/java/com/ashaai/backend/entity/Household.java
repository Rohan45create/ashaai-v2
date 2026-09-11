package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "households")
public class Household extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    private String houseNumber;
    private String address;
    private Double gpsLat;
    private Double gpsLng;
    private Integer totalMembers;
    private Boolean bplStatus;
    private String toiletFacility;
    private String waterSource;

    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getGpsLat() { return gpsLat; }
    public void setGpsLat(Double gpsLat) { this.gpsLat = gpsLat; }

    public Double getGpsLng() { return gpsLng; }
    public void setGpsLng(Double gpsLng) { this.gpsLng = gpsLng; }

    public Integer getTotalMembers() { return totalMembers; }
    public void setTotalMembers(Integer totalMembers) { this.totalMembers = totalMembers; }

    public Boolean getBplStatus() { return bplStatus; }
    public void setBplStatus(Boolean bplStatus) { this.bplStatus = bplStatus; }

    public String getToiletFacility() { return toiletFacility; }
    public void setToiletFacility(String toiletFacility) { this.toiletFacility = toiletFacility; }

    public String getWaterSource() { return waterSource; }
    public void setWaterSource(String waterSource) { this.waterSource = waterSource; }
}
