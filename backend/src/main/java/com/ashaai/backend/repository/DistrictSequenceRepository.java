package com.ashaai.backend.repository;

import com.ashaai.backend.entity.DistrictSequence;
import com.ashaai.backend.entity.DistrictSequenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DistrictSequenceRepository extends JpaRepository<DistrictSequence, DistrictSequenceId> {
}
