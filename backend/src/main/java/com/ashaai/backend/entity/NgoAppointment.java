package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ngo_appointments")
public class NgoAppointment extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "ngo_id", nullable = false)
    private Ngo ngo;

    @ManyToOne
    @JoinColumn(name = "referral_id")
    private Referral referral;

    private OffsetDateTime scheduledAt;
    private String status;

    public Ngo getNgo() { return ngo; }
    public void setNgo(Ngo ngo) { this.ngo = ngo; }
    public Referral getReferral() { return referral; }
    public void setReferral(Referral referral) { this.referral = referral; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
