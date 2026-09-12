package com.ashaai.backend.repository;

import com.ashaai.backend.entity.BirthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BirthRecordRepository extends JpaRepository<BirthRecord, UUID> {
}
