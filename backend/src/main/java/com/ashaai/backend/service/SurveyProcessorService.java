package com.ashaai.backend.service;

import com.ashaai.backend.dto.SurveySubmissionDto;
import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SurveyProcessorService {

    private static final Logger log = LoggerFactory.getLogger(SurveyProcessorService.class);

    private final PregnancyRepository pregnancyRepository;
    private final SurveySubmissionRepository surveySubmissionRepository;
    private final SurveyTemplateRepository surveyTemplateRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final ChildRepository childRepository;
    private final ReferralRepository referralRepository;
    private final AshaRepository ashaRepository;
    private final LinkageService linkageService;
    private final ObjectMapper objectMapper;
    private final VisitRepository visitRepository;

    public SurveyProcessorService(
            PregnancyRepository pregnancyRepository,
            SurveySubmissionRepository surveySubmissionRepository,
            SurveyTemplateRepository surveyTemplateRepository,
            HouseholdRepository householdRepository,
            HouseholdMemberRepository memberRepository,
            ChildRepository childRepository,
            ReferralRepository referralRepository,
            AshaRepository ashaRepository,
            LinkageService linkageService,
            ObjectMapper objectMapper,
            VisitRepository visitRepository
    ) {
        this.pregnancyRepository = pregnancyRepository;
        this.surveySubmissionRepository = surveySubmissionRepository;
        this.surveyTemplateRepository = surveyTemplateRepository;
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.childRepository = childRepository;
        this.referralRepository = referralRepository;
        this.ashaRepository = ashaRepository;
        this.linkageService = linkageService;
        this.objectMapper = objectMapper;
        this.visitRepository = visitRepository;
    }

    @Transactional
    public void processFamilySurveyPregnancyFlag(HouseholdMember member, Household household, Asha asha) {
        Pregnancy draftPregnancy = new Pregnancy();
        draftPregnancy.setMotherMember(member);
        draftPregnancy.setHousehold(household);
        draftPregnancy.setAsha(asha);
        draftPregnancy.setStatus("draft");
        
        pregnancyRepository.save(draftPregnancy);
    }

    /**
     * Processes a survey submission in a single atomic database transaction.
     * If referred_to_nrc is true (e.g. from a Child Growth survey), it also:
     * 1. Resolves the child and household member.
     * 2. Updates the child's malnutrition grade and risk status.
     * 3. Inserts a referral row (status='pending', reason derived from malnutrition grade).
     * 
     * All of this occurs in the SAME transaction as the survey_submissions insert.
     */
    @Transactional
    public SurveySubmission processSurveySubmission(SurveySubmissionDto dto, UUID authenticatedAshaId) {
        UUID effectiveAshaId = dto.getAshaId() != null ? dto.getAshaId() : authenticatedAshaId;
        Asha asha = null;
        if (effectiveAshaId != null) {
            asha = ashaRepository.findById(effectiveAshaId).orElse(null);
        }

        // 1. Resolve SurveyTemplate
        SurveyTemplate template = null;
        if (dto.getTemplateId() != null) {
            template = surveyTemplateRepository.findById(dto.getTemplateId()).orElse(null);
        }
        if (template == null && dto.getModuleKey() != null) {
            template = surveyTemplateRepository.findByModuleKey(dto.getModuleKey()).orElse(null);
        }
        if (template == null) {
            // Fallback: search all
            List<SurveyTemplate> allTemplates = surveyTemplateRepository.findAll();
            if (!allTemplates.isEmpty()) {
                if (dto.getModuleKey() != null) {
                    template = allTemplates.stream()
                            .filter(t -> dto.getModuleKey().equalsIgnoreCase(t.getModuleKey()))
                            .findFirst()
                            .orElse(allTemplates.get(0));
                } else {
                    template = allTemplates.get(0);
                }
            }
        }

        // 2. Parse dataMap and rawJson
        Map<String, Object> dataMap = new HashMap<>();
        String rawJson = "{}";
        if (dto.getData() != null) {
            if (dto.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) dto.getData();
                dataMap = map;
                try {
                    rawJson = objectMapper.writeValueAsString(map);
                } catch (JsonProcessingException e) {
                    rawJson = "{}";
                }
            } else if (dto.getData() instanceof String str) {
                rawJson = str;
                try {
                    dataMap = objectMapper.readValue(str, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    dataMap = new HashMap<>();
                }
            }
        }

        // 3. Resolve Household
        Household household = null;
        UUID householdId = dto.getHouseholdId();
        if (householdId == null && dataMap.get("household_id") != null) {
            try {
                householdId = UUID.fromString(String.valueOf(dataMap.get("household_id")));
            } catch (Exception ignored) {}
        }
        if (householdId == null && dataMap.get("_prefill_household_id") != null) {
            try {
                householdId = UUID.fromString(String.valueOf(dataMap.get("_prefill_household_id")));
            } catch (Exception ignored) {}
        }
        if (householdId != null) {
            household = householdRepository.findById(householdId).orElse(null);
        }

        // 4. Save survey_submissions row
        SurveySubmission submission = new SurveySubmission();
        submission.setTemplate(template);
        submission.setHousehold(household);
        submission.setAsha(asha);
        submission.setData(rawJson);
        submission.setSubmittedAt(dto.getSubmittedAt() != null ? dto.getSubmittedAt() : OffsetDateTime.now());
        submission = surveySubmissionRepository.save(submission);

        log.info("event=survey_submission_saved submission_id={} asha_id={}", submission.getId(), effectiveAshaId);

        // 5. Check if referred_to_nrc is true
        boolean referredToNrc = Boolean.TRUE.equals(dto.getReferredToNrc());
        if (!referredToNrc && dataMap != null) {
            Object refVal = dataMap.get("referred_to_nrc");
            if (Boolean.TRUE.equals(refVal) || "true".equalsIgnoreCase(String.valueOf(refVal))) {
                referredToNrc = true;
            }
        }

        // 6. Module-specific normalization & decomposition into real tables
        String moduleKey = dto.getModuleKey();
        if (moduleKey == null && template != null) {
            moduleKey = template.getModuleKey();
        }

        boolean isChildGrowth = "child_growth".equalsIgnoreCase(moduleKey)
                || (template != null && "child_growth".equalsIgnoreCase(template.getModuleKey()))
                || (template != null && "Child Growth".equalsIgnoreCase(template.getNameEn()));

        if (isChildGrowth) {
            processChildGrowthSubmission(submission, template, household, asha, dto.getHouseholdMemberId(), dataMap, referredToNrc);
        } else if (referredToNrc) {
            processChildGrowthSubmission(submission, template, household, asha, dto.getHouseholdMemberId(), dataMap, true);
        }

        return submission;
    }

    private void processChildGrowthSubmission(
            SurveySubmission submission,
            SurveyTemplate template,
            Household household,
            Asha asha,
            UUID explicitMemberId,
            Map<String, Object> dataMap,
            boolean referredToNrc
    ) {
        // Fallback: If household is null and ASHA is known, resolve ASHA's first household
        if (household == null && asha != null) {
            List<Household> ashaHouseholds = householdRepository.findAll().stream()
                    .filter(h -> h.getAsha() != null && h.getAsha().getId().equals(asha.getId()))
                    .toList();
            if (!ashaHouseholds.isEmpty()) {
                household = ashaHouseholds.get(0);
            }
        }

        // Resolve HouseholdMember
        HouseholdMember member = null;
        UUID memberId = explicitMemberId;
        if (memberId == null && dataMap.get("_prefill_member_id") != null) {
            try {
                memberId = UUID.fromString(String.valueOf(dataMap.get("_prefill_member_id")));
            } catch (Exception ignored) {}
        }
        if (memberId == null && dataMap.get("household_member_id") != null) {
            try {
                memberId = UUID.fromString(String.valueOf(dataMap.get("household_member_id")));
            } catch (Exception ignored) {}
        }

        if (memberId != null) {
            member = memberRepository.findById(memberId).orElse(null);
        }

        String childName = dataMap.get("child_name") != null ? String.valueOf(dataMap.get("child_name")).trim() : null;
        if (member == null && childName != null && !childName.isEmpty()) {
            List<HouseholdMember> matching = memberRepository.findByNameIgnoreCase(childName);
            if (!matching.isEmpty()) {
                member = matching.get(0);
            }
        }

        if (member == null && household != null && childName != null && !childName.isEmpty()) {
            // Create a temporary household member for this child
            member = new HouseholdMember();
            member.setHousehold(household);
            member.setName(childName);
            member.setGender(dataMap.get("gender") != null ? String.valueOf(dataMap.get("gender")) : "Other");
            member.setIdentityStatus("temporary");
            member.setTemporaryId(linkageService.generateTemporaryId("DEFAULT"));
            member = memberRepository.save(member);
        }

        // Resolve or create Child entity
        Child child = null;
        if (member != null) {
            child = childRepository.findByHouseholdMember_Id(member.getId()).orElse(null);
        }
        if (child == null && member != null) {
            child = new Child();
            child.setHouseholdMember(member);
            child.setAsha(asha);
            child.setIsOrphan(false);
            child.setHasParents(true);
            child.setCreatedAt(OffsetDateTime.now());
        }

        String gradeStr = dataMap.get("malnutritionGrade") != null ? String.valueOf(dataMap.get("malnutritionGrade")) : "Normal";
        boolean isSam = "RED".equalsIgnoreCase(gradeStr) || "SAM".equalsIgnoreCase(gradeStr);
        boolean isMam = "YELLOW".equalsIgnoreCase(gradeStr) || "MAM".equalsIgnoreCase(gradeStr);
        boolean isNormal = "NORMAL".equalsIgnoreCase(gradeStr) || "GREEN".equalsIgnoreCase(gradeStr);

        if (child != null) {
            if (dataMap.get("weight_kg") != null) {
                try { child.setCurrentWeightKg(new BigDecimal(String.valueOf(dataMap.get("weight_kg")))); } catch (Exception ignored) {}
            }
            if (dataMap.get("height_cm") != null) {
                try { child.setCurrentHeightCm(new BigDecimal(String.valueOf(dataMap.get("height_cm")))); } catch (Exception ignored) {}
            }
            if (dataMap.get("muac_cm") != null) {
                try {
                    BigDecimal cm = new BigDecimal(String.valueOf(dataMap.get("muac_cm")));
                    child.setMuacMm(cm.multiply(BigDecimal.TEN));
                } catch (Exception ignored) {}
            }
            if (dataMap.get("age_months") != null) {
                try { child.setAgeMonths(Integer.parseInt(String.valueOf(dataMap.get("age_months")))); } catch (Exception ignored) {}
            }

            if (isSam) {
                child.setMalnutritionGrade("SAM");
                child.setRiskLevel("CRITICAL");
                child.setRiskScore(92);
                child.setRiskPrimaryDriver("Severe wasting, MUAC <115mm");
                child.setRiskRecommendedAction("Immediate NRC referral required");
            } else if (isMam) {
                child.setMalnutritionGrade("MAM");
                child.setRiskLevel("HIGH");
                child.setRiskScore(68);
                child.setRiskPrimaryDriver("Moderate acute malnutrition");
                child.setRiskRecommendedAction("Supplementary feeding + NRC follow-up");
            } else {
                child.setMalnutritionGrade("Normal");
                child.setRiskLevel("LOW");
                child.setRiskScore(15);
                child.setRiskPrimaryDriver("Normal growth parameters");
                child.setRiskRecommendedAction("Routine monitoring");
            }
            child.setNrcReferralStatus(referredToNrc ? "pending" : null);
            child.setLastVisitDate(LocalDate.now());
            child.setRiskUpdatedAt(OffsetDateTime.now());
            child.setUpdatedAt(OffsetDateTime.now());
            child = childRepository.save(child);
            log.info("event=child_growth_normalized child_id={} member_id={} grade={} risk_level={}",
                    child.getId(), member.getId(), child.getMalnutritionGrade(), child.getRiskLevel());

            // Always record a Visit for this child growth measurement
            Visit visit = new Visit();
            visit.setChild(child);
            visit.setAsha(asha);
            visit.setVisitDate(LocalDate.now());
            visit.setWeightKg(child.getCurrentWeightKg());
            visit.setHeightCm(child.getCurrentHeightCm());
            visit.setMuacMm(child.getMuacMm());
            if (dataMap.get("illness_signs") != null) {
                visit.setNotes(String.valueOf(dataMap.get("illness_signs")));
            }
            visit.setActionTaken(referredToNrc ? "Referred to NRC" : "Routine growth monitoring");
            visit.setSource("manual");
            visit.setCreatedAt(OffsetDateTime.now());
            visitRepository.save(visit);
            log.info("event=visit_recorded visit_id={} child_id={} asha_id={}", visit.getId(), child.getId(), asha != null ? asha.getId() : null);
        }

        // Build Referral entity only if referredToNrc is true
        if (referredToNrc) {
            Referral referral = new Referral();
            if (child != null) {
                referral.setChild(child);
            }
            if (member != null) {
                referral.setHouseholdMember(member);
            }
            referral.setAsha(asha);

            String confidenceNote = "";
            if (dataMap.get("malnutrition_report") instanceof Map<?, ?> reportMap) {
                Object conf = reportMap.get("confidence");
                if (conf != null) {
                    confidenceNote = " (" + conf + "% confidence)";
                }
            }

            String reason;
            if (isSam) {
                reason = "Severe Acute Malnutrition (SAM)" + confidenceNote + " — referred via malnutrition scanner";
            } else if (isMam) {
                reason = "Moderate Acute Malnutrition (MAM)" + confidenceNote + " — referred via malnutrition scanner";
            } else {
                reason = "Malnutrition (" + gradeStr + ")" + confidenceNote + " — referred via malnutrition scanner";
            }

            referral.setReason(reason);
            referral.setStatus("pending");
            referral.setReferredDate(OffsetDateTime.now());

            referralRepository.save(referral);
            log.info("event=nrc_referral_created referral_id={} child_id={} member_id={} asha_id={}",
                    referral.getId(), child != null ? child.getId() : null, member != null ? member.getId() : null, asha != null ? asha.getId() : null);
        }
    }
}
