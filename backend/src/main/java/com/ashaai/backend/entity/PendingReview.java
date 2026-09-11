package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pending_reviews")
public class PendingReview extends BaseEntity {

    private String tableName;
    private UUID recordId;
    private String reason;
    private UUID flaggedBy;
    private String status;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public UUID getRecordId() { return recordId; }
    public void setRecordId(UUID recordId) { this.recordId = recordId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getFlaggedBy() { return flaggedBy; }
    public void setFlaggedBy(UUID flaggedBy) { this.flaggedBy = flaggedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
