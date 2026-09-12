package com.ashaai.backend.repository;

import com.ashaai.backend.entity.SurveySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SurveySubmissionRepository extends JpaRepository<SurveySubmission, UUID> {
}
