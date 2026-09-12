package com.ashaai.backend.repository;

import com.ashaai.backend.entity.DeathRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeathRecordRepository extends JpaRepository<DeathRecord, UUID> {
}
