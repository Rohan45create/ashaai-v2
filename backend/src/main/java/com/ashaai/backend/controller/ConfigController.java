package com.ashaai.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {

    @Value("${app.google.maps.api-key}")
    private String googleMapsApiKey;

    @Value("${app.supabase.url}")
    private String supabaseUrl;

    @Value("${app.supabase.anon-key}")
    private String supabaseAnonKey;

    @Value("${app.ngo.form-new-url}")
    private String ngoFormNewUrl;

    @Value("${app.ngo.form-existing-url}")
    private String ngoFormExistingUrl;

    @Value("${app.ngo.form-reschedule-url}")
    private String ngoFormRescheduleUrl;

    @GetMapping("/public-config")
    public Map<String, String> getPublicConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("googleMapsApiKey", googleMapsApiKey);
        config.put("supabaseUrl", supabaseUrl);
        config.put("supabaseAnonKey", supabaseAnonKey);
        config.put("ngoFormNewUrl", ngoFormNewUrl);
        config.put("ngoFormExistingUrl", ngoFormExistingUrl);
        config.put("ngoFormRescheduleUrl", ngoFormRescheduleUrl);
        return config;
    }
}
