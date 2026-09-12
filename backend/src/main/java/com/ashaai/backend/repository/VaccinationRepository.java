package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Vaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VaccinationRepository extends JpaRepository<Vaccination, UUID> {
    List<Vaccination> findByChild_Asha_Id(UUID ashaId);
}
