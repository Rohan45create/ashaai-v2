package com.ashaai.backend.service;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.entity.Household;
import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.entity.Pregnancy;
import com.ashaai.backend.repository.PregnancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyProcessorService {

    private final PregnancyRepository pregnancyRepository;

    public SurveyProcessorService(PregnancyRepository pregnancyRepository) {
        this.pregnancyRepository = pregnancyRepository;
    }

    @Transactional
    public void processFamilySurveyPregnancyFlag(HouseholdMember member, Household household, Asha asha) {
        // Logic representing a Family Survey flagging a member as pregnant
        // This is a simplified version for the integration test.
        
        Pregnancy draftPregnancy = new Pregnancy();
        draftPregnancy.setMotherMember(member);
        draftPregnancy.setHousehold(household);
        draftPregnancy.setAsha(asha);
        draftPregnancy.setStatus("draft");
        
        pregnancyRepository.save(draftPregnancy);
    }
}
