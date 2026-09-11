package com.ashaai.backend.entity;

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

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> services;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
}
