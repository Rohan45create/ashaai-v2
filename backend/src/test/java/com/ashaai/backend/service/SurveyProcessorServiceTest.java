package com.ashaai.backend.service;

import com.ashaai.backend.dto.SurveySubmissionDto;
import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SurveyProcessorServiceTest {

    private PregnancyRepository pregnancyRepository;
    private SurveySubmissionRepository surveySubmissionRepository;
    private SurveyTemplateRepository surveyTemplateRepository;
    private HouseholdRepository householdRepository;
    private HouseholdMemberRepository memberRepository;
    private ChildRepository childRepository;
    private ReferralRepository referralRepository;
    private VisitRepository visitRepository;
    private AshaRepository ashaRepository;
    private LinkageService linkageService;
    private ObjectMapper objectMapper;

    private SurveyProcessorService service;

    @BeforeEach
    void setUp() {
        pregnancyRepository = mock(PregnancyRepository.class);
        surveySubmissionRepository = mock(SurveySubmissionRepository.class);
        surveyTemplateRepository = mock(SurveyTemplateRepository.class);
        householdRepository = mock(HouseholdRepository.class);
        memberRepository = mock(HouseholdMemberRepository.class);
        childRepository = mock(ChildRepository.class);
        referralRepository = mock(ReferralRepository.class);
        visitRepository = mock(VisitRepository.class);
        ashaRepository = mock(AshaRepository.class);
        linkageService = mock(LinkageService.class);
        objectMapper = new ObjectMapper();

        service = new SurveyProcessorService(
                pregnancyRepository,
                surveySubmissionRepository,
                surveyTemplateRepository,
                householdRepository,
                memberRepository,
                childRepository,
                referralRepository,
                ashaRepository,
                linkageService,
                objectMapper,
                visitRepository
        );
    }

    @Test
    @DisplayName("Submitting child_growth survey with referred_to_nrc=true atomically creates survey_submissions row and referrals row")
    void testChildGrowthSubmissionWithNrcReferral() {
        UUID ashaId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        Asha asha = new Asha();
        asha.setId(ashaId);
        when(ashaRepository.findById(ashaId)).thenReturn(Optional.of(asha));

        SurveyTemplate template = new SurveyTemplate();
        template.setId(templateId);
        template.setModuleKey("child_growth");
        when(surveyTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        Household household = new Household();
        household.setId(householdId);
        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));

        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        member.setName("Ravi Jadhav");
        member.setHousehold(household);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        Child child = new Child();
        child.setId(childId);
        child.setHouseholdMember(member);
        child.setAsha(asha);
        when(childRepository.findByHouseholdMember_Id(memberId)).thenReturn(Optional.of(child));
        when(childRepository.save(any(Child.class))).thenAnswer(i -> i.getArgument(0));

        when(surveySubmissionRepository.save(any(SurveySubmission.class))).thenAnswer(i -> {
            SurveySubmission s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        when(referralRepository.save(any(Referral.class))).thenAnswer(i -> {
            Referral r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        // Form data from confirmed report & prefilled draft
        Map<String, Object> data = new HashMap<>();
        data.put("child_name", "Ravi Jadhav");
        data.put("weight_kg", 5.4);
        data.put("height_cm", 72.0);
        data.put("muac_cm", 11.0);
        data.put("malnutritionGrade", "RED");
        data.put("referred_to_nrc", true);
        data.put("_prefill_member_id", memberId.toString());

        SurveySubmissionDto dto = new SurveySubmissionDto();
        dto.setTemplateId(templateId);
        dto.setModuleKey("child_growth");
        dto.setHouseholdId(householdId);
        dto.setHouseholdMemberId(memberId);
        dto.setAshaId(ashaId);
        dto.setData(data);
        dto.setReferredToNrc(true);

        SurveySubmission result = service.processSurveySubmission(dto, ashaId);

        assertNotNull(result);
        assertNotNull(result.getId());
        verify(surveySubmissionRepository, times(1)).save(any(SurveySubmission.class));

        // Verify that a referral was saved in the same transaction
        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository, times(1)).save(referralCaptor.capture());

        Referral savedReferral = referralCaptor.getValue();
        assertNotNull(savedReferral);
        assertEquals("pending", savedReferral.getStatus());
        assertEquals(asha, savedReferral.getAsha());
        assertEquals(child, savedReferral.getChild());
        assertEquals(member, savedReferral.getHouseholdMember());
        assertTrue(savedReferral.getReason().contains("Severe Acute Malnutrition (SAM)"));

        // Verify Child malnutrition & risk attributes were updated
        ArgumentCaptor<Child> childCaptor = ArgumentCaptor.forClass(Child.class);
        verify(childRepository, times(1)).save(childCaptor.capture());
        Child updatedChild = childCaptor.getValue();
        assertEquals("SAM", updatedChild.getMalnutritionGrade());
        assertEquals("CRITICAL", updatedChild.getRiskLevel());
        assertEquals("pending", updatedChild.getNrcReferralStatus());

        // Verify Visit was saved in the same transaction
        ArgumentCaptor<Visit> visitCaptor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository, times(1)).save(visitCaptor.capture());
        Visit savedVisit = visitCaptor.getValue();
        assertNotNull(savedVisit);
        assertEquals(child, savedVisit.getChild());
        assertEquals(asha, savedVisit.getAsha());
        assertEquals("Referred to NRC", savedVisit.getActionTaken());
    }

    @Test
    @DisplayName("Submitting child_growth survey with referred_to_nrc=false updates child and records visit without referral")
    void testChildGrowthSubmissionWithoutNrcReferral() {
        UUID ashaId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        Asha asha = new Asha();
        asha.setId(ashaId);
        when(ashaRepository.findById(ashaId)).thenReturn(Optional.of(asha));

        SurveyTemplate template = new SurveyTemplate();
        template.setId(templateId);
        template.setModuleKey("child_growth");
        when(surveyTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        Household household = new Household();
        household.setId(householdId);
        when(householdRepository.findById(householdId)).thenReturn(Optional.of(household));

        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        member.setName("Normal Child");
        member.setHousehold(household);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        Child child = new Child();
        child.setId(childId);
        child.setHouseholdMember(member);
        child.setAsha(asha);
        when(childRepository.findByHouseholdMember_Id(memberId)).thenReturn(Optional.of(child));
        when(childRepository.save(any(Child.class))).thenAnswer(i -> i.getArgument(0));

        when(surveySubmissionRepository.save(any(SurveySubmission.class))).thenAnswer(i -> {
            SurveySubmission s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Map<String, Object> data = new HashMap<>();
        data.put("child_name", "Normal Child");
        data.put("weight_kg", 9.5);
        data.put("malnutritionGrade", "NORMAL");
        data.put("referred_to_nrc", false);
        data.put("_prefill_member_id", memberId.toString());

        SurveySubmissionDto dto = new SurveySubmissionDto();
        dto.setTemplateId(templateId);
        dto.setModuleKey("child_growth");
        dto.setHouseholdId(householdId);
        dto.setHouseholdMemberId(memberId);
        dto.setAshaId(ashaId);
        dto.setData(data);
        dto.setReferredToNrc(false);

        SurveySubmission result = service.processSurveySubmission(dto, ashaId);

        assertNotNull(result);
        verify(surveySubmissionRepository, times(1)).save(any(SurveySubmission.class));
        verify(referralRepository, never()).save(any(Referral.class));

        // Verify Child was updated
        ArgumentCaptor<Child> childCaptor = ArgumentCaptor.forClass(Child.class);
        verify(childRepository, times(1)).save(childCaptor.capture());
        Child updatedChild = childCaptor.getValue();
        assertEquals("Normal", updatedChild.getMalnutritionGrade());
        assertEquals("LOW", updatedChild.getRiskLevel());
        assertNull(updatedChild.getNrcReferralStatus());

        // Verify Visit was saved
        ArgumentCaptor<Visit> visitCaptor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository, times(1)).save(visitCaptor.capture());
        Visit savedVisit = visitCaptor.getValue();
        assertNotNull(savedVisit);
        assertEquals(child, savedVisit.getChild());
        assertEquals("Routine growth monitoring", savedVisit.getActionTaken());
    }
}
