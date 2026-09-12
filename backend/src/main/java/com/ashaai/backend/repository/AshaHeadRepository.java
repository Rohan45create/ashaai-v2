package com.ashaai.backend.repository;

import com.ashaai.backend.entity.AshaHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AshaHeadRepository extends JpaRepository<AshaHead, UUID> {
    Optional<AshaHead> findByAuthUserId(UUID authUserId);
}
