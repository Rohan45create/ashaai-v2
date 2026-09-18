package com.ashaai.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "module_submissions")
public class ModuleSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asha_id", nullable = false)
    private Asha asha;

    @Column(name = "module_type", nullable = false)
    private String moduleType;

    @Column(name = "source")
    private String source;

    @Column(name = "synced_from_offline", nullable = false)
    private Boolean syncedFromOffline = false;

    @Column(name = "notes")
    private String notes;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }

    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Boolean getSyncedFromOffline() { return syncedFromOffline; }
    public void setSyncedFromOffline(Boolean syncedFromOffline) { this.syncedFromOffline = syncedFromOffline; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
}
