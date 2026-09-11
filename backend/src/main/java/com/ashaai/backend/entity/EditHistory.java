package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "edit_history")
public class EditHistory extends BaseEntity {

    private String tableName;
    private UUID recordId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private UUID editedBy;

    @CreationTimestamp
    private OffsetDateTime editedAt;

    private String reason;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public UUID getRecordId() { return recordId; }
    public void setRecordId(UUID recordId) { this.recordId = recordId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public UUID getEditedBy() { return editedBy; }
    public void setEditedBy(UUID editedBy) { this.editedBy = editedBy; }
    public OffsetDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(OffsetDateTime editedAt) { this.editedAt = editedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
