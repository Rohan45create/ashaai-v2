package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Asha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AshaRepository extends JpaRepository<Asha, UUID> {
    Optional<Asha> findByAuthUserId(UUID authUserId);
}
