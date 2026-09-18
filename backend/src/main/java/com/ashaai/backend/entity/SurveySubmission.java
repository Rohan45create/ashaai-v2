package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "survey_submissions")
public class SurveySubmission extends BaseEntity {

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SurveyTemplate template;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "asha_id")
    private Asha asha;

    @JdbcTypeCode(SqlTypes.JSON)
    private String data;

    private OffsetDateTime submittedAt;

    public SurveyTemplate getTemplate() { return template; }
    public void setTemplate(SurveyTemplate template) { this.template = template; }
    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }
    public Asha getAsha() { return asha; }
    public void setAsha(Asha asha) { this.asha = asha; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
}
