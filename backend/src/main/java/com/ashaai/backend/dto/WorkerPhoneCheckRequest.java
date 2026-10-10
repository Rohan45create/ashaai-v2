package com.ashaai.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class WorkerPhoneCheckRequest {

    @NotBlank(message = "Phone number is required")
    private String phone;

    public WorkerPhoneCheckRequest() {}

    public WorkerPhoneCheckRequest(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
