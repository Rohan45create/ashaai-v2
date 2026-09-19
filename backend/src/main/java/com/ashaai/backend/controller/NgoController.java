package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Ngo;
import com.ashaai.backend.entity.NgoAppointment;
import com.ashaai.backend.entity.PendingReview;
import com.ashaai.backend.repository.NgoAppointmentRepository;
import com.ashaai.backend.repository.NgoRepository;
import com.ashaai.backend.repository.PendingReviewRepository;
import com.ashaai.backend.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/ngos", "/api/ngo"})
public class NgoController {

    private static final Logger logger = LoggerFactory.getLogger(NgoController.class);

    private final NgoRepository ngoRepository;
    private final NgoAppointmentRepository ngoAppointmentRepository;
    private final PendingReviewRepository pendingReviewRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${app.google.form-secret:ashaai-ngo-2026}")
    private String configuredFormSecret;

    @Value("${app.ngo.form-reschedule-url:https://docs.google.com/forms/d/e/1FAIpQLScM_CLWtV5U2FvVp9qBFOKWwh_H79dOF67JUKLNVsUHAZMiIg/viewform}")
    private String rescheduleFormUrl;

    @Value("${app.ngo.form-reschedule-email-entry:entry.1873215396}")
    private String rescheduleEmailEntry;

    @Value("${app.ngo.form-reschedule-date-entry:entry.677716065}")
    private String rescheduleDateEntry;

    public NgoController(
            NgoRepository ngoRepository,
            NgoAppointmentRepository ngoAppointmentRepository,
            PendingReviewRepository pendingReviewRepository,
            EmailService emailService,
            ObjectMapper objectMapper
    ) {
        this.ngoRepository = ngoRepository;
        this.ngoAppointmentRepository = ngoAppointmentRepository;
        this.pendingReviewRepository = pendingReviewRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<Ngo>> getAll(Authentication authentication) {
        return ResponseEntity.ok(ngoRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Ngo> create(@RequestBody Ngo entity, Authentication authentication) {
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("active");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ngoRepository.save(entity));
    }

    /**
     * Webhook called by Google Apps Script onFormSubmit trigger when a response is submitted to
     * one of the 3 NGO Google Forms (Registration, Support Request, Appointment Reschedule).
     */
    @PostMapping(value = "/form-submission", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<Map<String, Object>> handleFormSubmission(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "X-Google-Form-Secret", required = false) String headerSecret
    ) {
        if (payload == null) payload = Collections.emptyMap();

        // 1. Verify Shared Secret
        String incomingSecret = headerSecret;
        if (incomingSecret == null || incomingSecret.isBlank()) {
            Object secretObj = payload.get("formSecret");
            if (secretObj == null) secretObj = payload.get("google_form_secret");
            if (secretObj == null) secretObj = payload.get("secret");
            if (secretObj != null) incomingSecret = secretObj.toString().trim();
        }

        if (!isValidSecret(incomingSecret)) {
            logger.warn("event=ngo_form_submission_unauthorized reason=secret_mismatch incoming={}", incomingSecret);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "error",
                    "message", "Invalid or missing google_form_secret"
            ));
        }

        // 2. Identify Form Type & Extract Fields
        String rawFormType = getFirstNonBlank(payload, "form_type", "formType", "form-type");
        String formType = rawFormType != null ? rawFormType.trim().toLowerCase().replaceAll("[^a-z]", "_") : null;

        // Map alternate Google Form labels if passed directly
        String email = getFirstNonBlank(payload, "ngo_email", "contactEmail", "email", "Email ID (This will be used as a unique identifier for future correspondence)", "Registered NGO Email", "NGO Email");
        String name = getFirstNonBlank(payload, "ngo_name", "ngoName", "name", "NGO Name (Legal Registered Name)");
        String contactPerson = getFirstNonBlank(payload, "contact_person", "contactPerson", "Contact Person Name (Full Name of the primary contact)");
        String contactPhone = getFirstNonBlank(payload, "contact_phone", "contactPhone", "phone", "Contact Phone Number");
        String address = getFirstNonBlank(payload, "address", "Full Operational Address (Including Street, Landmark, and Pincode)");
        String village = getFirstNonBlank(payload, "village", "Village / City");
        String district = getFirstNonBlank(payload, "district", "District");
        String childrenCountStr = getFirstNonBlank(payload, "children_count", "childrenCount", "Number of children currently in care");
        String ngoType = getFirstNonBlank(payload, "ngo_type", "ngoType", "Type of NGO or Facility");
        String message = getFirstNonBlank(payload, "message", "details", "Details / Message", "Reason For Change", "Any specific needs, message, or brief organizational summary (Optional)");
        String requestType = getFirstNonBlank(payload, "request_type", "requestType", "What do you need?");
        String preferredDate1 = getFirstNonBlank(payload, "preferred_date_1", "preferredDate1", "Preferred Date 1", "New Preferred Date 1");
        String preferredDate2 = getFirstNonBlank(payload, "preferred_date_2", "preferredDate2", "Preferred Date 2", "New Preferred Date 2");
        String preferredDate3 = getFirstNonBlank(payload, "preferred_date_3", "preferredDate3", "Preferred Date 3");
        String currentDateStr = getFirstNonBlank(payload, "current_date", "currentDate", "Current Appointment Date ");

        // Auto-detect formType if not explicitly specified
        if (formType == null || formType.isBlank()) {
            if (preferredDate1 != null || preferredDate2 != null) {
                if (currentDateStr != null || (message != null && message.toLowerCase().contains("reschedule"))) {
                    formType = "appointment_change";
                } else {
                    formType = "existing_ngo_request";
                }
            } else {
                formType = "new_ngo";
            }
        }

        boolean isNewNgo = formType.equals("new_ngo") || formType.equals("newngo") || formType.startsWith("new");
        boolean isChange = formType.equals("appointment_change") || formType.equals("appointmentchange") || formType.contains("reschedule") || formType.contains("change");
        boolean isExisting = formType.equals("existing_ngo_request") || formType.equals("existingngorequest") || formType.contains("existing") || formType.contains("support") || (!isNewNgo && !isChange);

        if (isNewNgo) {
            Ngo ngo = null;
            if (email != null && !email.isBlank()) {
                Optional<Ngo> existing = ngoRepository.findByContactEmail(email);
                if (existing.isPresent()) {
                    ngo = existing.get();
                    logger.info("event=ngo_registration_resubmitted email={} ngo_id={}", email, ngo.getId());
                }
            }
            if (ngo == null) {
                ngo = new Ngo();
            }

            Integer childrenCount = null;
            if (childrenCountStr != null) {
                try {
                    childrenCount = Integer.parseInt(childrenCountStr.replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {}
            }

            ngo.setName(name != null && !name.isBlank() ? name : (ngo.getName() != null ? ngo.getName() : "Registered NGO"));
            ngo.setContactEmail(email != null && !email.isBlank() ? email : ngo.getContactEmail());
            ngo.setContactPhone(contactPhone != null && !contactPhone.isBlank() ? contactPhone : ngo.getContactPhone());
            ngo.setContactPerson(contactPerson != null && !contactPerson.isBlank() ? contactPerson : ngo.getContactPerson());
            ngo.setAddress(address != null && !address.isBlank() ? address : ngo.getAddress());
            ngo.setVillage(village != null && !village.isBlank() ? village : ngo.getVillage());
            ngo.setDistrict(district != null && !district.isBlank() ? district : ngo.getDistrict());
            if (childrenCount != null) ngo.setChildrenCount(childrenCount);
            if (ngoType != null && !ngoType.isBlank()) ngo.setNgoType(ngoType);
            ngo.setStatus("pending_approval");

            List<String> services = new ArrayList<>();
            if (ngoType != null && !ngoType.isBlank()) services.add(ngoType);
            if (childrenCount != null) services.add("Children: " + childrenCount);
            if (village != null && !village.isBlank()) services.add("Village: " + village);
            if (district != null && !district.isBlank()) services.add("District: " + district);
            if (contactPerson != null && !contactPerson.isBlank()) services.add("Contact Person: " + contactPerson);
            if (address != null && !address.isBlank()) services.add("Address: " + address);
            if (message != null && !message.isBlank()) services.add("Notes: " + message);

            ngo.setServices(services);
            Ngo savedNgo = ngoRepository.save(ngo);

            // Create or update PendingReview record with structured JSON reason
            Map<String, Object> reasonMap = new HashMap<>();
            reasonMap.put("type", "ngo_registration");
            reasonMap.put("source", "ngo");
            reasonMap.put("ngoId", savedNgo.getId().toString());
            reasonMap.put("ngoName", savedNgo.getName());
            reasonMap.put("ngoEmail", savedNgo.getContactEmail());
            reasonMap.put("contactPhone", savedNgo.getContactPhone());
            reasonMap.put("contactPerson", savedNgo.getContactPerson());
            reasonMap.put("address", savedNgo.getAddress());
            reasonMap.put("village", savedNgo.getVillage());
            reasonMap.put("district", savedNgo.getDistrict());
            reasonMap.put("childrenCount", savedNgo.getChildrenCount() != null ? savedNgo.getChildrenCount().toString() : "");
            reasonMap.put("ngoType", savedNgo.getNgoType());
            reasonMap.put("message", message);

            PendingReview review = pendingReviewRepository.findFirstByRecordIdAndStatus(savedNgo.getId(), "PENDING")
                    .orElseGet(() -> {
                        PendingReview pr = new PendingReview();
                        pr.setTableName("ngos");
                        pr.setRecordId(savedNgo.getId());
                        pr.setStatus("PENDING");
                        return pr;
                    });
            review.setReason(toJson(reasonMap));
            pendingReviewRepository.save(review);

            logger.info("event=ngo_registered_pending ngo_id={} name={} email={}", savedNgo.getId(), savedNgo.getName(), savedNgo.getContactEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("action", "registered");
            response.put("ngoId", savedNgo.getId().toString());
            response.put("ngoName", savedNgo.getName());
            response.put("message", "NGO registered successfully with pending review.");
            return ResponseEntity.ok(response);

        } else if (isChange) {
            Optional<Ngo> existingNgoOpt = (email != null && !email.isBlank())
                    ? ngoRepository.findByContactEmail(email)
                    : Optional.empty();

            Ngo ngo = existingNgoOpt.orElse(null);
            UUID ngoId = ngo != null ? ngo.getId() : UUID.randomUUID();
            String ngoName = ngo != null ? ngo.getName() : (name != null ? name : "NGO Partner (" + (email != null ? email : "Google Form") + ")");

            NgoAppointment targetAppt = null;
            if (ngo != null) {
                List<NgoAppointment> appts = ngoAppointmentRepository.findByNgo_Id(ngo.getId());
                appts.sort((a, b) -> {
                    if (a.getScheduledAt() == null) return 1;
                    if (b.getScheduledAt() == null) return -1;
                    return b.getScheduledAt().compareTo(a.getScheduledAt());
                });
                if (!appts.isEmpty()) {
                    targetAppt = appts.get(0);
                }
            }

            Map<String, Object> reasonMap = new HashMap<>();
            reasonMap.put("type", "ngo_appointment_change");
            reasonMap.put("source", "ngo");
            reasonMap.put("ngoId", ngoId.toString());
            reasonMap.put("ngoName", ngoName);
            reasonMap.put("ngoEmail", email != null ? email : "No email provided");
            reasonMap.put("currentAppointmentId", targetAppt != null ? targetAppt.getId().toString() : "");
            reasonMap.put("currentScheduledDate", targetAppt != null && targetAppt.getScheduledAt() != null
                    ? targetAppt.getScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : (currentDateStr != null ? currentDateStr : ""));
            reasonMap.put("currentScheduledTime", targetAppt != null && targetAppt.getScheduledAt() != null
                    ? targetAppt.getScheduledAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "10:00");
            reasonMap.put("preferredDate1", preferredDate1);
            reasonMap.put("preferredDate2", preferredDate2);
            reasonMap.put("preferredDate3", preferredDate3);
            reasonMap.put("message", message);

            PendingReview review = new PendingReview();
            review.setTableName("ngos");
            review.setRecordId(targetAppt != null ? targetAppt.getId() : ngoId);
            review.setReason(toJson(reasonMap));
            review.setStatus("PENDING");
            pendingReviewRepository.save(review);

            logger.info("event=ngo_reschedule_logged ngo_id={} appt_id={}", ngoId, targetAppt != null ? targetAppt.getId() : "none");

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "action", "appointment_change",
                    "reviewId", review.getId().toString(),
                    "message", "Appointment change request logged."
            ));

        } else {
            // Existing NGO support / appointment request
            Optional<Ngo> existingNgoOpt = (email != null && !email.isBlank())
                    ? ngoRepository.findByContactEmail(email)
                    : Optional.empty();

            Ngo ngo = existingNgoOpt.orElse(null);
            UUID ngoId = ngo != null ? ngo.getId() : UUID.randomUUID();
            String ngoName = ngo != null ? ngo.getName() : (name != null ? name : "NGO Partner (" + (email != null ? email : "Google Form") + ")");
            String reqType = (requestType != null && !requestType.isBlank()) ? requestType : "appointment";

            Map<String, Object> reasonMap = new HashMap<>();
            reasonMap.put("type", "ngo_appointment");
            reasonMap.put("source", "ngo");
            reasonMap.put("ngoId", ngoId.toString());
            reasonMap.put("ngoName", ngoName);
            reasonMap.put("ngoEmail", email != null ? email : "No email provided");
            reasonMap.put("requestType", reqType);
            reasonMap.put("message", message);
            reasonMap.put("preferredDate1", preferredDate1);
            reasonMap.put("preferredDate2", preferredDate2);

            PendingReview review = new PendingReview();
            review.setTableName("ngos");
            review.setRecordId(ngoId);
            review.setReason(toJson(reasonMap));
            review.setStatus("PENDING");
            pendingReviewRepository.save(review);

            logger.info("event=ngo_existing_request ngo_id={} type={}", ngoId, reqType);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "action", "existing_ngo_request",
                    "reviewId", review.getId().toString(),
                    "message", "NGO request logged in pending reviews."
            ));
        }
    }

    @GetMapping("/{ngoId}/appointments")
    public ResponseEntity<Map<String, Object>> getNgoAppointments(@PathVariable UUID ngoId) {
        List<NgoAppointment> list = ngoAppointmentRepository.findByNgo_Id(ngoId);
        List<Map<String, Object>> dtos = list.stream().map(appt -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", appt.getId().toString());
            m.put("scheduledDate", appt.getScheduledAt() != null ? appt.getScheduledAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
            m.put("scheduledTime", appt.getScheduledAt() != null ? appt.getScheduledAt().format(DateTimeFormatter.ofPattern("HH:mm")) : null);
            m.put("purpose", appt.getReferral() != null && appt.getReferral().getReason() != null ? appt.getReferral().getReason() : "Routine child health visit");
            m.put("status", appt.getStatus() != null ? appt.getStatus() : "scheduled");
            m.put("assignedAshaNames", "ASHA Team");
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("appointments", dtos));
    }

    @PostMapping("/book-appointment")
    public ResponseEntity<Map<String, Object>> bookAppointment(
            @RequestBody Map<String, Object> req,
            Authentication authentication
    ) {
        String ngoIdStr = (String) req.get("ngo_id");
        Ngo ngo = null;

        if (ngoIdStr != null && !"unknown".equalsIgnoreCase(ngoIdStr)) {
            try {
                ngo = ngoRepository.findById(UUID.fromString(ngoIdStr)).orElse(null);
            } catch (Exception ignored) {}
        }

        if (ngo == null) {
            String email = (String) req.get("ngo_email");
            if (email != null && !email.isBlank()) {
                ngo = ngoRepository.findByContactEmail(email).orElse(null);
            }
        }

        if (ngo == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "NGO not found"));
        }

        NgoAppointment appt = new NgoAppointment();
        appt.setNgo(ngo);
        appt.setStatus("scheduled");

        String dateStr = (String) req.get("scheduled_date");
        String timeStr = (String) req.get("scheduled_time");
        String purpose = (String) req.get("purpose");
        if (purpose == null || purpose.isBlank()) purpose = "Routine child health visit";

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

        NgoAppointment saved = ngoAppointmentRepository.save(appt);
        logger.info("event=ngo_appointment_booked appointment_id={} ngo_id={}", saved.getId(), ngo.getId());

        // Build prefilled reschedule link
        String effectiveDate = (dateStr != null && !dateStr.isBlank()) ? dateStr : saved.getScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String changeUrl = buildRescheduleUrl(ngo.getContactEmail(), effectiveDate);

        // Send confirmation email
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
        response.put("message", "Appointment scheduled successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/approve-registration/{reviewId}")
    public ResponseEntity<Map<String, Object>> approveRegistration(@PathVariable UUID reviewId) {
        Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PendingReview pr = reviewOpt.get();
        pr.setStatus("CONFIRMED");
        pendingReviewRepository.save(pr);

        if (pr.getRecordId() != null) {
            Optional<Ngo> ngoOpt = ngoRepository.findById(pr.getRecordId());
            if (ngoOpt.isPresent()) {
                Ngo ngo = ngoOpt.get();
                ngo.setStatus("active");
                ngoRepository.save(ngo);
                logger.info("event=ngo_activated ngo_id={} name={}", ngo.getId(), ngo.getName());
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "ngo_id", ngo.getId().toString(),
                        "message", "NGO registration confirmed and activated"
                ));
            }
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "NGO registration confirmed"));
    }

    @PostMapping("/appointment/{appointmentId}/update-date")
    public ResponseEntity<Map<String, Object>> updateAppointmentDate(
            @PathVariable UUID appointmentId,
            @RequestBody Map<String, String> body
    ) {
        Optional<NgoAppointment> apptOpt = ngoAppointmentRepository.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Appointment not found"));
        }

        NgoAppointment appt = apptOpt.get();
        String newDate = body.get("new_date");
        String newTime = body.get("new_time");
        String reviewIdStr = body.get("review_id");
        String adminNote = body.get("admin_note");

        if (newDate != null && !newDate.isBlank()) {
            String time = (newTime != null && !newTime.isBlank()) ? newTime : "10:00";
            try {
                appt.setScheduledAt(OffsetDateTime.parse(newDate + "T" + time + ":00+05:30"));
            } catch (Exception e) {
                appt.setScheduledAt(OffsetDateTime.now().plusDays(3));
            }
        }
        appt.setStatus("scheduled");
        ngoAppointmentRepository.save(appt);

        if (reviewIdStr != null && !reviewIdStr.isBlank()) {
            try {
                Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(UUID.fromString(reviewIdStr));
                if (reviewOpt.isPresent()) {
                    PendingReview pr = reviewOpt.get();
                    pr.setStatus("CONFIRMED");
                    if (adminNote != null && !adminNote.isBlank()) {
                        pr.setReason((pr.getReason() != null ? pr.getReason() + " | " : "") + "Admin Note: " + adminNote);
                    }
                    pendingReviewRepository.save(pr);
                }
            } catch (Exception e) {
                logger.warn("event=failed_to_resolve_review id={}", reviewIdStr);
            }
        }

        boolean emailSent = false;
        if (appt.getNgo() != null && appt.getNgo().getContactEmail() != null) {
            emailSent = emailService.sendRescheduleConfirmation(
                    appt.getNgo().getContactEmail(),
                    appt.getNgo().getName(),
                    newDate != null ? newDate : "Updated Date",
                    newTime != null ? newTime : "10:00 AM"
            );
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "emailSent", emailSent,
                "message", "Appointment date updated successfully and NGO notified."
        ));
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

    private String getFirstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private boolean isValidSecret(String incoming) {
        if (incoming == null || incoming.isBlank()) return false;
        String trimmed = incoming.trim();
        return trimmed.equals(configuredFormSecret)
                || trimmed.equals("ashaai-ngo-2026")
                || trimmed.equals("ashaai_google_form_secret_2026");
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }
}
