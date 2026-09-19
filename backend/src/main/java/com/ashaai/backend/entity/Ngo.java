package com.ashaai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "ngos")
public class Ngo extends BaseEntity {

    private String name;
    private String contactPhone;
    private String contactEmail;

    @Column(name = "status")
    private String status = "pending_review";

    private String address;
    private String village;
    private String district;

    @Column(name = "children_count")
    private Integer childrenCount;

    @Column(name = "ngo_type")
    private String ngoType;

    @Column(name = "contact_person")
    private String contactPerson;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> services;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public Integer getChildrenCount() { return childrenCount; }
    public void setChildrenCount(Integer childrenCount) { this.childrenCount = childrenCount; }

    public String getNgoType() { return ngoType; }
    public void setNgoType(String ngoType) { this.ngoType = ngoType; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
}
