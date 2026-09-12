package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Pregnancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PregnancyRepository extends JpaRepository<Pregnancy, UUID> {
    
    @Query("SELECT p FROM Pregnancy p WHERE p.asha.id = :ashaId AND p.status IN ('active', 'draft')")
    List<Pregnancy> findActiveOrDraftPregnancies(UUID ashaId);
}
