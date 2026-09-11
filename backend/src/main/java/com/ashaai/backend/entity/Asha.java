package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "ashas")
public class Asha extends BaseEntity {
    private UUID authUserId;

    @ManyToOne
    @JoinColumn(name = "head_id")
    private AshaHead head;

    private String name;
    private String phone;
    private String village;
    private String district;

    @JdbcTypeCode(SqlTypes.JSON)
    private String coverageZone;

    private String fcmToken;

    public UUID getAuthUserId() { return authUserId; }
    public void setAuthUserId(UUID authUserId) { this.authUserId = authUserId; }

    public AshaHead getHead() { return head; }
    public void setHead(AshaHead head) { this.head = head; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getCoverageZone() { return coverageZone; }
    public void setCoverageZone(String coverageZone) { this.coverageZone = coverageZone; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
