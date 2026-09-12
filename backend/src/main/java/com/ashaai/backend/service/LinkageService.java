package com.ashaai.backend.service;

import com.ashaai.backend.entity.DistrictSequence;
import com.ashaai.backend.entity.DistrictSequenceId;
import com.ashaai.backend.entity.EditHistory;
import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.repository.DistrictSequenceRepository;
import com.ashaai.backend.repository.EditHistoryRepository;
import com.ashaai.backend.repository.HouseholdMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LinkageService {

    private final DistrictSequenceRepository sequenceRepository;
    private final HouseholdMemberRepository memberRepository;
    private final EditHistoryRepository editHistoryRepository;

    public LinkageService(DistrictSequenceRepository sequenceRepository,
                          HouseholdMemberRepository memberRepository,
                          EditHistoryRepository editHistoryRepository) {
        this.sequenceRepository = sequenceRepository;
        this.memberRepository = memberRepository;
        this.editHistoryRepository = editHistoryRepository;
    }

    @Transactional
    public String generateTemporaryId(String district) {
        int year = LocalDate.now().getYear();
        DistrictSequenceId id = new DistrictSequenceId(district, year);
        
        DistrictSequence seq = sequenceRepository.findById(id).orElseGet(() -> {
            DistrictSequence s = new DistrictSequence();
            s.setDistrict(district);
            s.setYear(year);
            s.setCurrentValue(0);
            return s;
        });

        int nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        sequenceRepository.save(seq);

        return String.format("TMP-%s-%d-%05d", district.toUpperCase(), year, nextVal);
    }

    @Transactional
    public HouseholdMember confirmAadhaar(UUID memberId, String encryptedAadhaar, String last4, UUID editedBy) {
        HouseholdMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if ("aadhaar_confirmed".equals(member.getIdentityStatus())) {
            throw new IllegalStateException("Aadhaar already confirmed for this member.");
        }

        member.setAadhaarEncrypted(encryptedAadhaar);
        member.setAadhaarLast4(last4);
        member.setIdentityStatus("aadhaar_confirmed");

        EditHistory history = new EditHistory();
        history.setTableName("household_members");
        history.setRecordId(memberId);
        history.setFieldName("identity_status");
        history.setOldValue("temporary");
        history.setNewValue("aadhaar_confirmed");
        history.setEditedBy(editedBy);
        history.setReason("Aadhaar confirmation");

        memberRepository.save(member);
        editHistoryRepository.save(history);

        return member;
    }

    @Transactional(readOnly = true)
    public List<HouseholdMember> findMembersByAadhaar(String last4, UUID ashaId) {
        return memberRepository.findByAadhaarLast4AndAshaId(last4, ashaId);
    }
}
