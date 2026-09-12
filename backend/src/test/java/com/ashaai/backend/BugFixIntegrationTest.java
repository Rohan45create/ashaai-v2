package com.ashaai.backend;

import com.ashaai.backend.config.TestcontainersConfig;
import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.entity.Child;
import com.ashaai.backend.entity.EditHistory;
import com.ashaai.backend.entity.Household;
import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.entity.Pregnancy;
import com.ashaai.backend.repository.AshaRepository;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.repository.EditHistoryRepository;
import com.ashaai.backend.repository.HouseholdMemberRepository;
import com.ashaai.backend.repository.HouseholdRepository;
import com.ashaai.backend.repository.PregnancyRepository;
import com.ashaai.backend.service.LinkageService;
import com.ashaai.backend.service.SurveyProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class BugFixIntegrationTest {

    @Autowired
    private SurveyProcessorService surveyProcessorService;

    @Autowired
    private LinkageService linkageService;

    @Autowired
    private PregnancyRepository pregnancyRepository;

    @Autowired
    private HouseholdMemberRepository memberRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private AshaRepository ashaRepository;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private EditHistoryRepository editHistoryRepository;

    private Asha testAsha;
    private Household testHousehold;

    @BeforeEach
    void setup() {
        testAsha = new Asha();
        testAsha.setAuthUserId(UUID.randomUUID());
        testAsha.setName("Test ASHA");
        testAsha = ashaRepository.save(testAsha);

        testHousehold = new Household();
        testHousehold.setAsha(testAsha);
        testHousehold.setHouseNumber("H-123");
        testHousehold = householdRepository.save(testHousehold);
    }

    @Test
    void testBug3_CrossModuleLinkageVisibility() {
        // Arrange: A household member is discovered to be pregnant during a family survey
        HouseholdMember member = new HouseholdMember();
        member.setHousehold(testHousehold);
        member.setName("Pregnant Member");
        member.setGender("Female");
        member.setIdentityStatus("temporary");
        member.setTemporaryId("TMP-TEST-2026-00001");
        member = memberRepository.save(member);

        // Act: The survey processor flags the pregnancy
        surveyProcessorService.processFamilySurveyPregnancyFlag(member, testHousehold, testAsha);

        // Assert: The auto-drafted pregnancy exists in the core repository and is visible to priority queries
        List<Pregnancy> priorityList = pregnancyRepository.findActiveOrDraftPregnancies(testAsha.getId());
        
        assertThat(priorityList).hasSize(1);
        assertThat(priorityList.get(0).getStatus()).isEqualTo("draft");
        assertThat(priorityList.get(0).getMotherMember().getId()).isEqualTo(member.getId());
    }

    @Test
    void testBug5_IdentityConfirmationDoesNotBreakForeignKeys() {
        // Arrange: A child is born, no Aadhaar yet.
        HouseholdMember member = new HouseholdMember();
        member.setHousehold(testHousehold);
        member.setName("Newborn");
        member.setGender("Male");
        member.setIdentityStatus("temporary");
        
        // LinkageService handles temporary ID logic normally
        String tempId = linkageService.generateTemporaryId("TEST");
        member.setTemporaryId(tempId);
        member = memberRepository.save(member);

        Child child = new Child();
        child.setHouseholdMember(member);
        child.setAsha(testAsha);
        child.setAgeMonths(1);
        child = childRepository.save(child);

        // Act: Mother later provides Aadhaar. We confirm it via LinkageService.
        UUID editorId = UUID.randomUUID();
        linkageService.confirmAadhaar(member.getId(), "encrypted_aadhaar_string", "1234", editorId);

        // Assert: 
        // 1. The member row is updated
        HouseholdMember updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getIdentityStatus()).isEqualTo("aadhaar_confirmed");
        assertThat(updatedMember.getAadhaarLast4()).isEqualTo("1234");
        
        // 2. The child record remains intact and still points to the exact same UUID (No cascading or orphan issues)
        Child fetchedChild = childRepository.findById(child.getId()).orElseThrow();
        assertThat(fetchedChild.getHouseholdMember().getId()).isEqualTo(updatedMember.getId());
        
        // 3. Edit history was captured
        List<EditHistory> history = editHistoryRepository.findAll();
        assertThat(history).isNotEmpty();
        EditHistory lastEdit = history.get(history.size() - 1);
        assertThat(lastEdit.getRecordId()).isEqualTo(member.getId());
        assertThat(lastEdit.getTableName()).isEqualTo("household_members");
        assertThat(lastEdit.getFieldName()).isEqualTo("identity_status");
        assertThat(lastEdit.getNewValue()).isEqualTo("aadhaar_confirmed");
    }
}
