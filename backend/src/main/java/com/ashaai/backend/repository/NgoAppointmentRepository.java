package com.ashaai.backend.repository;

import com.ashaai.backend.entity.NgoAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NgoAppointmentRepository extends JpaRepository<NgoAppointment, UUID> {
    java.util.List<NgoAppointment> findByNgo_Id(UUID ngoId);
    java.util.List<NgoAppointment> findByReferral_Asha_IdAndScheduledAtGreaterThanEqualAndStatusNotOrderByScheduledAtAsc(UUID ashaId, java.time.OffsetDateTime time, String status);
}
