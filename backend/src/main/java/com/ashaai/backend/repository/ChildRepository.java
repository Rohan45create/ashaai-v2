package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChildRepository extends JpaRepository<Child, UUID> {
    List<Child> findByHouseholdMember_Household_Id(UUID householdId);
    List<Child> findByAshaId(UUID ashaId);
}
