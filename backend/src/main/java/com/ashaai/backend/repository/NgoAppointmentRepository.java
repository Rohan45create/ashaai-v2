package com.ashaai.backend.repository;

import com.ashaai.backend.entity.NgoAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NgoAppointmentRepository extends JpaRepository<NgoAppointment, UUID> {
    java.util.List<NgoAppointment> findByNgo_Id(UUID ngoId);
    java.util.List<NgoAppointment> findByReferral_Asha_IdAndScheduledAtGreaterThanEqualAndStatusNotOrderByScheduledAtAsc(UUID ashaId, java.time.OffsetDateTime time, String status);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM NgoAppointment a LEFT JOIN a.referral r " +
           "WHERE (r.asha.id = :ashaId OR a.referral IS NULL) " +
           "AND a.scheduledAt >= :time " +
           "AND a.status <> :status " +
           "ORDER BY a.scheduledAt ASC")
    java.util.List<NgoAppointment> findUpcomingForAsha(
            @org.springframework.data.repository.query.Param("ashaId") UUID ashaId,
            @org.springframework.data.repository.query.Param("time") java.time.OffsetDateTime time,
            @org.springframework.data.repository.query.Param("status") String status
    );
}
