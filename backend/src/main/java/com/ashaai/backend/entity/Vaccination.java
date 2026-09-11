package com.ashaai.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "vaccinations")
public class Vaccination extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    private String vaccineName;
    private LocalDate dueDate;
    private LocalDate givenDate;
    private String batch;

    public Child getChild() { return child; }
    public void setChild(Child child) { this.child = child; }
    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getGivenDate() { return givenDate; }
    public void setGivenDate(LocalDate givenDate) { this.givenDate = givenDate; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
}
