package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "survey_templates")
public class SurveyTemplate extends BaseEntity {

    private String nameEn;
    private String nameMr;
    private String nameHi;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private AshaHead createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    private String fields;

    private Boolean isPublished;

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameMr() { return nameMr; }
    public void setNameMr(String nameMr) { this.nameMr = nameMr; }
    public String getNameHi() { return nameHi; }
    public void setNameHi(String nameHi) { this.nameHi = nameHi; }
    public AshaHead getCreatedBy() { return createdBy; }
    public void setCreatedBy(AshaHead createdBy) { this.createdBy = createdBy; }
    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
}
