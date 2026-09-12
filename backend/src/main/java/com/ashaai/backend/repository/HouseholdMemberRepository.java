package com.ashaai.backend.repository;

import com.ashaai.backend.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {
    
    @Query("SELECT m FROM HouseholdMember m WHERE m.aadhaarLast4 = :last4 AND m.household.asha.id = :ashaId")
    List<HouseholdMember> findByAadhaarLast4AndAshaId(String last4, UUID ashaId);
}
