package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "recipient_asha_id")
    private Asha recipientAsha;

    @ManyToOne
    @JoinColumn(name = "recipient_head_id")
    private AshaHead recipientHead;

    private String title;
    private String body;
    private Boolean isRead;

    public Asha getRecipientAsha() { return recipientAsha; }
    public void setRecipientAsha(Asha recipientAsha) { this.recipientAsha = recipientAsha; }
    public AshaHead getRecipientHead() { return recipientHead; }
    public void setRecipientHead(AshaHead recipientHead) { this.recipientHead = recipientHead; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
}
