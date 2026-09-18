package com.ashaai.backend.repository;

import com.ashaai.backend.entity.ModuleSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModuleSubmissionRepository extends JpaRepository<ModuleSubmission, UUID> {
    List<ModuleSubmission> findByAsha_Id(UUID ashaId);
}
