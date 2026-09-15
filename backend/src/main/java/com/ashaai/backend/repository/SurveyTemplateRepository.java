package com.ashaai.backend.repository;

import com.ashaai.backend.entity.SurveyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SurveyTemplateRepository extends JpaRepository<SurveyTemplate, UUID> {
    Optional<SurveyTemplate> findByModuleKey(String moduleKey);
}
