package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Ngo;
import com.ashaai.backend.entity.NgoAppointment;
import com.ashaai.backend.entity.Referral;
import com.ashaai.backend.repository.NgoAppointmentRepository;
import com.ashaai.backend.repository.NgoRepository;
import com.ashaai.backend.repository.ReferralRepository;
import com.ashaai.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/appointments", produces = MediaType.APPLICATION_JSON_VALUE)
public class AppointmentController {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentController.class);

    private final NgoAppointmentRepository appointmentRepository;
    private final NgoRepository ngoRepository;
    private final ReferralRepository referralRepository;
    private final EmailService emailService;

    @Value("${app.ngo.form-reschedule-url:https://docs.google.com/forms/d/e/1FAIpQLScM_CLWtV5U2FvVp9qBFOKWwh_H79dOF67JUKLNVsUHAZMiIg/viewform}")
    private String rescheduleFormUrl;

    @Value("${app.ngo.form-reschedule-email-entry:entry.1873215396}")
    private String rescheduleEmailEntry;

    @Value("${app.ngo.form-reschedule-date-entry:entry.677716065}")
    private String rescheduleDateEntry;

    @Autowired
    public AppointmentController(
            NgoAppointmentRepository appointmentRepository,
            NgoRepository ngoRepository,
            ReferralRepository referralRepository,
            EmailService emailService) {
        this.appointmentRepository = appointmentRepository;
        this.ngoRepository = ngoRepository;
        this.referralRepository = referralRepository;
        this.emailService = emailService;
    }

    @GetMapping("/upcoming/{ashaId}")
    public ResponseEntity<List<Map<String, Object>>> getUpcomingAppointments(@PathVariable UUID ashaId) {
        List<NgoAppointment> appointments = appointmentRepository
                .findByReferral_Asha_IdAndScheduledAtGreaterThanEqualAndStatusNotOrderByScheduledAtAsc(
                        ashaId, OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0), "completed"
                );

        List<Map<String, Object>> response = appointments.stream().map(appt -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", appt.getId());
            map.put("type", "ngo");

            if (appt.getScheduledAt() != null) {
                map.put("scheduledDate", appt.getScheduledAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                map.put("scheduledTime", appt.getScheduledAt().format(DateTimeFormatter.ofPattern("HH:mm")));
            }

            if (appt.getReferral() != null) {
                map.put("targetName", appt.getReferral().getChildName());
                map.put("purpose", appt.getReferral().getReason());
            }

            if (appt.getNgo() != null) {
                if (map.get("targetName") == null) {
                    map.put("targetName", appt.getNgo().getName());
                }
                map.put("ngoName", appt.getNgo().getName());
                map.put("ngoAddress", appt.getNgo().getAddress());
                map.put("address", appt.getNgo().getAddress());
            }
            
            map.put("status", appt.getStatus());

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> completeAppointment(@PathVariable UUID id) {
        return appointmentRepository.findById(id).map(appt -> {
            appt.setStatus("completed");
            appointmentRepository.save(appt);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/book-referral")
    public ResponseEntity<Map<String, Object>> bookReferralAppointment(@RequestBody Map<String, Object> req) {
        String ngoIdStr = (String) req.get("ngoId");
        String referralIdStr = (String) req.get("referralId");
        
        if (ngoIdStr == null || referralIdStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "ngoId and referralId are required"));
        }
        
        Ngo ngo = ngoRepository.findById(UUID.fromString(ngoIdStr)).orElse(null);
        Referral referral = referralRepository.findById(UUID.fromString(referralIdStr)).orElse(null);
        
        if (ngo == null || referral == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "NGO or Referral not found"));
        }
        
        NgoAppointment appt = new NgoAppointment();
        appt.setNgo(ngo);
        appt.setReferral(referral);
        appt.setStatus("scheduled");
        
        String dateStr = (String) req.get("scheduledDate");
        String timeStr = (String) req.get("scheduledTime");
        
        if (dateStr != null && !dateStr.isBlank()) {
            String time = (timeStr != null && !timeStr.isBlank()) ? timeStr : "10:00";
            try {
                appt.setScheduledAt(OffsetDateTime.parse(dateStr + "T" + time + ":00+05:30"));
            } catch (Exception e) {
                appt.setScheduledAt(OffsetDateTime.now().plusDays(3));
            }
        } else {
            appt.setScheduledAt(OffsetDateTime.now().plusDays(3));
        }

        NgoAppointment saved = appointmentRepository.save(appt);
        logger.info("event=referral_appointment_booked appointment_id={} ngo_id={} referral_id={}",
                saved.getId(), ngo.getId(), referral.getId());

        // Build pre-filled reschedule link
        String effectiveDate = (dateStr != null && !dateStr.isBlank())
                ? dateStr
                : saved.getScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String changeUrl = buildRescheduleUrl(ngo.getContactEmail(), effectiveDate);

        // Send confirmation email via Gmail SMTP
        String purpose = referral.getReason() != null ? referral.getReason() : "Child health follow-up visit";
        boolean emailSent = false;
        if (ngo.getContactEmail() != null && !ngo.getContactEmail().isBlank()) {
            emailSent = emailService.sendAppointmentConfirmation(
                    ngo.getContactEmail(),
                    ngo.getName(),
                    effectiveDate,
                    (timeStr != null && !timeStr.isBlank()) ? timeStr : "10:00 AM",
                    purpose,
                    changeUrl
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("appointmentId", saved.getId().toString());
        response.put("emailSent", emailSent);
        response.put("rescheduleUrl", changeUrl);
        response.put("message", "Referral appointment scheduled successfully");

        return ResponseEntity.ok(response);
    }

    private String buildRescheduleUrl(String email, String dateStr) {
        String encodedEmail = URLEncoder.encode(email != null ? email : "", StandardCharsets.UTF_8);
        String encodedDate = URLEncoder.encode(dateStr != null ? dateStr : "", StandardCharsets.UTF_8);
        return String.format("%s?%s=%s&%s=%s",
                rescheduleFormUrl,
                rescheduleEmailEntry,
                encodedEmail,
                rescheduleDateEntry,
                encodedDate
        );
    }
}
