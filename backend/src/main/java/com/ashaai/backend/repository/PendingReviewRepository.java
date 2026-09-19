package com.ashaai.backend.repository;

import com.ashaai.backend.entity.PendingReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingReviewRepository extends JpaRepository<PendingReview, UUID> {
    List<PendingReview> findByRecordId(UUID recordId);
    Optional<PendingReview> findFirstByRecordIdAndStatus(UUID recordId, String status);
}
