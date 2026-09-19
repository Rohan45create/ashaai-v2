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

        boolean isFamilySurvey = "family_survey".equalsIgnoreCase(moduleKey)
                || (template != null && "family_survey".equalsIgnoreCase(template.getModuleKey()))
                || (template != null && "Family Survey".equalsIgnoreCase(template.getNameEn()))
                || (dataMap != null && dataMap.get("members") != null);

        boolean isChildGrowth = "child_growth".equalsIgnoreCase(moduleKey)
                || (template != null && "child_growth".equalsIgnoreCase(template.getModuleKey()))
                || (template != null && "Child Growth".equalsIgnoreCase(template.getNameEn()));

        if (isFamilySurvey) {
            processFamilySurveySubmission(submission, template, asha, dataMap);
        } else if (isChildGrowth) {
            processChildGrowthSubmission(submission, template, household, asha, dto.getHouseholdMemberId(), dataMap, referredToNrc);
        } else if (referredToNrc) {
            processChildGrowthSubmission(submission, template, household, asha, dto.getHouseholdMemberId(), dataMap, true);
        }

        return submission;
    }

    private void processFamilySurveySubmission(
            SurveySubmission submission,
            SurveyTemplate template,
            Asha asha,
            Map<String, Object> dataMap
    ) {
        if (dataMap == null) return;

        // 1. Resolve / Upsert Household
        Map<String, Object> hhMap = null;
        if (dataMap.get("household") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) dataMap.get("household");
            hhMap = casted;
        }

        String houseNumber = null;
        if (hhMap != null) {
            if (hhMap.get("house_number") != null) houseNumber = String.valueOf(hhMap.get("house_number")).trim();
            else if (hhMap.get("houseNumber") != null) houseNumber = String.valueOf(hhMap.get("houseNumber")).trim();
        }
        if (houseNumber == null || houseNumber.isEmpty()) {
            if (dataMap.get("house_number") != null) houseNumber = String.valueOf(dataMap.get("house_number")).trim();
            else if (dataMap.get("houseNumber") != null) houseNumber = String.valueOf(dataMap.get("houseNumber")).trim();
        }
        if (houseNumber == null || houseNumber.isEmpty()) {
            houseNumber = "H-" + (System.currentTimeMillis() % 100000);
        }

        // Determine BPL status
        Boolean bplStatus = null;
        Object bplObj = hhMap != null && hhMap.containsKey("bplStatus") ? hhMap.get("bplStatus") :
                (hhMap != null && hhMap.containsKey("bpl_status") ? hhMap.get("bpl_status") : dataMap.get("bplStatus"));
        if (bplObj instanceof Boolean b) {
            bplStatus = b;
        } else if (bplObj != null) {
            bplStatus = "yes".equalsIgnoreCase(String.valueOf(bplObj)) || "true".equalsIgnoreCase(String.valueOf(bplObj));
        }

        // Extract members list to count total members
        List<?> rawMembers = null;
        if (dataMap.get("members") instanceof List<?> list) {
            rawMembers = list;
        }

        int totalMembers = rawMembers != null ? rawMembers.size() : 1;

        // Look up or create household
        Household household = submission.getHousehold();
        if (household == null && asha != null) {
            household = householdRepository.findByAsha_IdAndHouseNumber(asha.getId(), houseNumber).orElse(null);
        }
        if (household == null) {
            household = new Household();
            household.setAsha(asha);
            household.setHouseNumber(houseNumber);
            household.setAddress(asha != null && asha.getVillage() != null ? asha.getVillage() : "Village Area");
            household.setTotalMembers(totalMembers);
            household.setBplStatus(bplStatus);
            household.setCreatedAt(OffsetDateTime.now());
            household.setUpdatedAt(OffsetDateTime.now());
            household = householdRepository.save(household);
            log.info("Created new household id={} houseNumber={} for asha={}", household.getId(), houseNumber, asha != null ? asha.getId() : null);
        } else {
            household.setTotalMembers(totalMembers);
            if (bplStatus != null) household.setBplStatus(bplStatus);
            household.setUpdatedAt(OffsetDateTime.now());
            household = householdRepository.save(household);
        }

        // Link household to submission if not set
        submission.setHousehold(household);
        surveySubmissionRepository.save(submission);

        // 2. Process each member
        if (rawMembers == null || rawMembers.isEmpty()) {
            return;
        }

        HouseholdMember firstFemaleAdult = null;

        for (int i = 0; i < rawMembers.size(); i++) {
            Object item = rawMembers.get(i);
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;

            String name = m.get("member_name") != null ? String.valueOf(m.get("member_name")).trim() :
                    (m.get("name") != null ? String.valueOf(m.get("name")).trim() : "Member " + (i + 1));

            String gender = "Other";
            if (m.get("gender") != null) {
                String g = String.valueOf(m.get("gender")).trim();
                if ("Male".equalsIgnoreCase(g) || "M".equalsIgnoreCase(g)) gender = "Male";
                else if ("Female".equalsIgnoreCase(g) || "F".equalsIgnoreCase(g)) gender = "Female";
            }

            // DOB & Age
            LocalDate dob = null;
            Object dobObj = m.get("date_of_birth") != null ? m.get("date_of_birth") : m.get("dob");
            if (dobObj != null && !String.valueOf(dobObj).isBlank()) {
                try {
                    dob = LocalDate.parse(String.valueOf(dobObj).substring(0, 10));
                } catch (Exception ignored) {}
            }
            Integer age = null;
            if (m.get("age") != null) {
                try {
                    age = Integer.parseInt(String.valueOf(m.get("age")).replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {}
            }
            if (dob == null && age != null && age > 0) {
                dob = LocalDate.now().minusYears(age).withMonth(1).withDayOfMonth(1);
            }

            // Relationship to head
            String rel = m.get("relationship_to_head") != null ? String.valueOf(m.get("relationship_to_head")).trim() :
                    (m.get("relationshipToHead") != null ? String.valueOf(m.get("relationshipToHead")).trim() : (i == 0 ? "Self" : "Other"));

            // Marital status - check constraint in DB: ('Married','Unmarried','Widow','Separated')
            String maritalStatus = null;
            Object msObj = m.get("marital_status") != null ? m.get("marital_status") : m.get("maritalStatus");
            if (msObj != null) {
                String ms = String.valueOf(msObj).trim();
                if ("Married".equalsIgnoreCase(ms)) maritalStatus = "Married";
                else if ("Unmarried".equalsIgnoreCase(ms) || "Single".equalsIgnoreCase(ms)) maritalStatus = "Unmarried";
                else if ("Widow".equalsIgnoreCase(ms) || "Widowed".equalsIgnoreCase(ms)) maritalStatus = "Widow";
                else if ("Separated".equalsIgnoreCase(ms) || "Divorced".equalsIgnoreCase(ms)) maritalStatus = "Separated";
            }

            // Aadhaar & Temporary ID
            String aadhaarRaw = m.get("aadhaar_raw") != null ? String.valueOf(m.get("aadhaar_raw")).replaceAll("[^0-9]", "") : null;
            String aadhaarLast4 = m.get("aadhaar_last4") != null ? String.valueOf(m.get("aadhaar_last4")).trim() : null;
            if (aadhaarLast4 == null && aadhaarRaw != null && aadhaarRaw.length() >= 4) {
                aadhaarLast4 = aadhaarRaw.substring(aadhaarRaw.length() - 4);
            }

            String temporaryId = m.get("temporary_id") != null ? String.valueOf(m.get("temporary_id")).trim() :
                    (m.get("temporaryId") != null ? String.valueOf(m.get("temporaryId")).trim() : null);

            // Database constraint: has_an_identifier check (aadhaar_last4 is not null or temporary_id is not null)
            String identityStatus;
            if (aadhaarLast4 != null && aadhaarLast4.length() == 4) {
                identityStatus = "aadhaar_confirmed";
            } else {
                identityStatus = "temporary";
                if (temporaryId == null || temporaryId.isBlank()) {
                    String dist = (asha != null && asha.getDistrict() != null && !asha.getDistrict().isBlank())
                            ? asha.getDistrict().trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase()
                            : "MH";
                    temporaryId = linkageService.generateTemporaryId(dist);
                }
            }

            String mobile = m.get("mobile_number") != null ? String.valueOf(m.get("mobile_number")).trim() :
                    (m.get("mobileNumber") != null ? String.valueOf(m.get("mobileNumber")).trim() : null);

            String abhaId = m.get("abha_id") != null ? String.valueOf(m.get("abha_id")).trim() :
                    (m.get("abhaId") != null ? String.valueOf(m.get("abhaId")).trim() : null);

            Boolean hasGenetic = Boolean.TRUE.equals(m.get("has_genetic_condition")) || "true".equalsIgnoreCase(String.valueOf(m.get("has_genetic_condition")));
            List<String> geneticConditionsList = null;
            Object gcObj = m.get("genetic_conditions");
            if (gcObj instanceof List<?> gcl) {
                geneticConditionsList = gcl.stream().map(String::valueOf).toList();
            } else if (gcObj instanceof String gcs && !gcs.isBlank()) {
                geneticConditionsList = Arrays.stream(gcs.split(",")).map(String::trim).toList();
            }
            String geneticNotes = m.get("genetic_condition_notes") != null ? String.valueOf(m.get("genetic_condition_notes")).trim() : null;

            // Check if updating existing member or creating new
            HouseholdMember member = null;
            if (m.get("existingId") != null) {
                try {
                    UUID existUuid = UUID.fromString(String.valueOf(m.get("existingId")));
                    member = memberRepository.findById(existUuid).orElse(null);
                } catch (Exception ignored) {}
            }
            if (member == null && aadhaarLast4 != null && asha != null) {
                List<HouseholdMember> existing = memberRepository.findByAadhaarLast4AndAshaId(aadhaarLast4, asha.getId());
                if (!existing.isEmpty()) {
                    member = existing.get(0);
                }
            }
            String rawProvidedTempId = m.get("temporary_id") != null ? String.valueOf(m.get("temporary_id")).trim() :
                    (m.get("temporaryId") != null ? String.valueOf(m.get("temporaryId")).trim() : null);
            if (member == null && rawProvidedTempId != null && !rawProvidedTempId.isBlank()) {
                member = memberRepository.findByTemporaryId(rawProvidedTempId).orElse(null);
            }

            if (member == null) {
                member = new HouseholdMember();
                member.setHousehold(household);
                member.setCreatedAt(OffsetDateTime.now());
            }
            member.setHousehold(household);
            member.setSerialNumber(i + 1);
            member.setName(name);
            member.setGender(gender);
            member.setDateOfBirth(dob);
            member.setRelationshipToHead(rel);
            member.setMaritalStatus(maritalStatus);
            if (aadhaarLast4 != null && aadhaarLast4.length() == 4) {
                member.setAadhaarLast4(aadhaarLast4);
                member.setAadhaarEncrypted(aadhaarRaw != null ? aadhaarRaw : ("XXXXXXXX" + aadhaarLast4));
                member.setIdentityStatus("aadhaar_confirmed");
                member.setTemporaryId(null);
            } else {
                member.setAadhaarLast4(null);
                member.setTemporaryId(temporaryId);
                member.setIdentityStatus("temporary");
            }
            member.setMobileNumber(mobile);
            member.setAbhaIdEncrypted(abhaId);
            member.setHasGeneticCondition(hasGenetic);
            member.setGeneticConditions(geneticConditionsList);
            member.setGeneticConditionNotes(geneticNotes);
            member.setUpdatedAt(OffsetDateTime.now());

            member = memberRepository.save(member);

            if ("Female".equalsIgnoreCase(gender) && firstFemaleAdult == null) {
                firstFemaleAdult = member;
            }

            // 3. Auto-draft Pregnancy (OLD_REPO_AUDIT.md Bug 3 fix)
            boolean isPregnant = Boolean.TRUE.equals(m.get("is_pregnant")) || "true".equalsIgnoreCase(String.valueOf(m.get("is_pregnant")))
                    || Boolean.TRUE.equals(m.get("pregnant")) || "true".equalsIgnoreCase(String.valueOf(m.get("pregnant")));
            if (isPregnant && "Female".equalsIgnoreCase(gender)) {
                List<Pregnancy> existingPregnancies = pregnancyRepository.findByMotherMember_Id(member.getId());
                boolean hasActiveOrDraft = existingPregnancies.stream()
                        .anyMatch(p -> "active".equalsIgnoreCase(p.getStatus()) || "draft".equalsIgnoreCase(p.getStatus()));
                if (!hasActiveOrDraft) {
                    Pregnancy draft = new Pregnancy();
                    draft.setMotherMember(member);
                    draft.setHousehold(household);
                    draft.setAsha(asha);
                    draft.setStatus("draft");
                    draft.setCreatedAt(OffsetDateTime.now());
                    draft.setUpdatedAt(OffsetDateTime.now());
                    pregnancyRepository.save(draft);
                    log.info("Auto-drafted pregnancy record for member id={} name={}", member.getId(), member.getName());
                }
            }

            // 4. Auto-register Child if under 5 years old (age < 5 or dob >= 5 years ago)
            boolean isUnderFive = (age != null && age < 5);
            if (dob != null && dob.isAfter(LocalDate.now().minusYears(5))) {
                isUnderFive = true;
            }
            if (isUnderFive) {
                List<Child> existingChildren = childRepository.findByHouseholdMember_Id(member.getId());
                if (existingChildren.isEmpty()) {
                    Child child = new Child();
                    child.setHouseholdMember(member);
                    if (firstFemaleAdult != null && !firstFemaleAdult.getId().equals(member.getId())) {
                        child.setMotherMember(firstFemaleAdult);
                    }
                    child.setAsha(asha);
                    child.setIsOrphan(false);
                    child.setHasParents(true);
                    child.setRiskScore(0);
                    child.setRiskLevel("LOW");
                    child.setCreatedAt(OffsetDateTime.now());
                    child.setUpdatedAt(OffsetDateTime.now());
                    childRepository.save(child);
                    log.info("Auto-created Child record for member id={} name={}", member.getId(), member.getName());
                }
            }
        }
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
            child = childRepository.findFirstByHouseholdMember_Id(member.getId()).orElse(null);
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
