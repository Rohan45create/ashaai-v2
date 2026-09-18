package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Ngo;
import com.ashaai.backend.entity.NgoAppointment;
import com.ashaai.backend.entity.PendingReview;
import com.ashaai.backend.repository.NgoAppointmentRepository;
import com.ashaai.backend.repository.NgoRepository;
import com.ashaai.backend.repository.PendingReviewRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @Value("${app.google.form-secret:ashaai_google_form_secret_2026}")
    private String configuredFormSecret;

    public NgoController(
            NgoRepository ngoRepository,
            NgoAppointmentRepository ngoAppointmentRepository,
            PendingReviewRepository pendingReviewRepository
    ) {
        this.ngoRepository = ngoRepository;
        this.ngoAppointmentRepository = ngoAppointmentRepository;
        this.pendingReviewRepository = pendingReviewRepository;
    }

    @GetMapping
    public ResponseEntity<List<Ngo>> getAll(Authentication authentication) {
        return ResponseEntity.ok(ngoRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Ngo> create(@RequestBody Ngo entity, Authentication authentication) {
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

        if (incomingSecret == null || !incomingSecret.equals(configuredFormSecret)) {
            logger.warn("event=ngo_form_submission_unauthorized reason=secret_mismatch incoming={}", incomingSecret);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "error",
                    "message", "Invalid or missing google_form_secret"
            ));
        }

        // 2. Identify Form Type & Extract Fields
        String formType = getString(payload, "formType");
        String ngoName = getFirstNonBlank(payload, "ngoName", "NGO Name (Legal Registered Name)", "name");
        String contactEmail = getFirstNonBlank(payload, "contactEmail", "Email ID (This will be used as a unique identifier for future correspondence)", "Registered NGO Email", "NGO Email", "email");
        String contactPhone = getFirstNonBlank(payload, "contactPhone", "Contact Phone Number", "phone");
        String contactPerson = getFirstNonBlank(payload, "contactPerson", "Contact Person Name (Full Name of the primary contact)");
        String address = getFirstNonBlank(payload, "address", "Full Operational Address (Including Street, Landmark, and Pincode)");
        String village = getFirstNonBlank(payload, "village", "Village / City");
        String district = getFirstNonBlank(payload, "district", "District");
        String childrenCount = getFirstNonBlank(payload, "childrenCount", "Number of children currently in care");
        String ngoType = getFirstNonBlank(payload, "ngoType", "Type of NGO or Facility", "services");
        String message = getFirstNonBlank(payload, "message", "Any specific needs, message, or brief organizational summary (Optional)", "Details / Message", "Reason For Change");
        String preferredDate1 = getFirstNonBlank(payload, "preferredDate1", "Preferred Date 1", "New Preferred Date 1");
        String preferredDate2 = getFirstNonBlank(payload, "preferredDate2", "Preferred Date 2", "New Preferred Date 2");

        boolean isRegistration = "NEW_NGO".equalsIgnoreCase(formType) || (ngoName != null && !ngoName.isBlank());

        if (isRegistration) {
            // Find existing or create new
            Ngo ngo = (contactEmail != null && !contactEmail.isBlank())
                    ? ngoRepository.findByContactEmail(contactEmail).orElse(new Ngo())
                    : new Ngo();

            ngo.setName(ngoName != null && !ngoName.isBlank() ? ngoName : "Registered NGO");
            ngo.setContactEmail(contactEmail);
            ngo.setContactPhone(contactPhone);

            List<String> services = new ArrayList<>();
            if (ngoType != null && !ngoType.isBlank()) services.add(ngoType);
            if (childrenCount != null && !childrenCount.isBlank()) services.add("Children: " + childrenCount);
            if (village != null && !village.isBlank()) services.add("Village: " + village);
            if (district != null && !district.isBlank()) services.add("District: " + district);
            if (contactPerson != null && !contactPerson.isBlank()) services.add("Contact Person: " + contactPerson);
            if (address != null && !address.isBlank()) services.add("Address: " + address);
            if (message != null && !message.isBlank()) services.add("Notes: " + message);

            ngo.setServices(services);
            Ngo savedNgo = ngoRepository.save(ngo);

            // Create PendingReview record
            PendingReview review = new PendingReview();
            review.setTableName("ngos");
            review.setRecordId(savedNgo.getId());
            review.setReason("New NGO Registration via Google Form: " + savedNgo.getName());
            review.setStatus("PENDING");
            pendingReviewRepository.save(review);

            logger.info("event=ngo_registered_via_form ngo_id={} name={} email={}", savedNgo.getId(), savedNgo.getName(), savedNgo.getContactEmail());

            // Mock or SMTP email confirmation
            logConfirmationEmail(savedNgo.getContactEmail(), savedNgo.getName());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("action", "registered");
            response.put("ngoId", savedNgo.getId().toString());
            response.put("ngoName", savedNgo.getName());
            response.put("message", "NGO registered successfully and pending review created.");
            return ResponseEntity.ok(response);
        } else {
            // Appointment request or reschedule
            Optional<Ngo> existingNgoOpt = (contactEmail != null && !contactEmail.isBlank())
                    ? ngoRepository.findByContactEmail(contactEmail)
                    : Optional.empty();

            UUID recordId = existingNgoOpt.map(Ngo::getId).orElse(UUID.randomUUID());

            PendingReview review = new PendingReview();
            review.setTableName("ngo_appointments");
            review.setRecordId(recordId);
            review.setReason("NGO Appointment Request via Google Form: " + (contactEmail != null ? contactEmail : "unknown") + " | " + message);
            review.setStatus("PENDING");
            pendingReviewRepository.save(review);

            logger.info("event=ngo_appointment_request_via_form email={} reason={}", contactEmail, review.getReason());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("action", "appointment_requested");
            response.put("reviewId", review.getId().toString());
            response.put("message", "NGO request received and logged in pending reviews.");
            return ResponseEntity.ok(response);
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
        if (ngoIdStr == null || "unknown".equalsIgnoreCase(ngoIdStr)) {
            // Find by email if id is unknown
            String email = (String) req.get("ngo_email");
            if (email != null) {
                Optional<Ngo> opt = ngoRepository.findByContactEmail(email);
                if (opt.isPresent()) ngoIdStr = opt.get().getId().toString();
            }
        }

        if (ngoIdStr == null || "unknown".equalsIgnoreCase(ngoIdStr)) {
            // Default to first NGO if any
            List<Ngo> all = ngoRepository.findAll();
            if (!all.isEmpty()) ngoIdStr = all.get(0).getId().toString();
            else return ResponseEntity.badRequest().body(Map.of("error", "No NGO specified or registered"));
        }

        Ngo ngo = ngoRepository.findById(UUID.fromString(ngoIdStr)).orElseThrow();
        NgoAppointment appt = new NgoAppointment();
        appt.setNgo(ngo);
        appt.setStatus("scheduled");

        String dateStr = (String) req.get("scheduled_date");
        String timeStr = (String) req.get("scheduled_time");
        if (dateStr != null) {
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

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "appointmentId", saved.getId().toString(),
                "message", "Appointment scheduled successfully"
        ));
    }

    @PostMapping("/approve-registration/{reviewId}")
    public ResponseEntity<Map<String, Object>> approveRegistration(@PathVariable UUID reviewId) {
        Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(reviewId);
        if (reviewOpt.isPresent()) {
            PendingReview pr = reviewOpt.get();
            pr.setStatus("CONFIRMED");
            pendingReviewRepository.save(pr);
            return ResponseEntity.ok(Map.of("status", "success", "message", "NGO registration confirmed"));
        }
        return ResponseEntity.notFound().build();
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

    private void logConfirmationEmail(String toEmail, String ngoName) {
        if (toEmail == null || toEmail.isBlank()) return;
        // Mock email dispatch log; will use JavaMailSender if configured
        logger.info("event=email_dispatch_confirmation to={} subject='NGO Registration Confirmed - AshaAI' ngo_name={}", toEmail, ngoName);
    }
}
