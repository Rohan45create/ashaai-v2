package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
    List<Household> findByAsha_Id(UUID ashaId);
    Optional<Household> findByAsha_IdAndHouseNumber(UUID ashaId, String houseNumber);
}
