package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "medical_officers")
public class MedicalOfficer extends BaseEntity {
    private UUID authUserId;
    private String name;
    private String phone;
    private String email;
    private String district;

    public UUID getAuthUserId() { return authUserId; }
    public void setAuthUserId(UUID authUserId) { this.authUserId = authUserId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
}
