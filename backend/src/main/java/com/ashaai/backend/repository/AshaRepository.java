package com.ashaai.backend.repository;

import com.ashaai.backend.entity.Asha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AshaRepository extends JpaRepository<Asha, UUID> {
    Optional<Asha> findByAuthUserId(UUID authUserId);

    Optional<Asha> findByPhone(String phone);

    @org.springframework.data.jpa.repository.Query(
        "SELECT a FROM Asha a WHERE a.phone = :e164Phone OR a.phone = :tenDigits OR a.phone = :twelveDigits"
    )
    Optional<Asha> findByAnyPhoneFormat(
        @org.springframework.data.repository.query.Param("e164Phone") String e164Phone,
        @org.springframework.data.repository.query.Param("tenDigits") String tenDigits,
        @org.springframework.data.repository.query.Param("twelveDigits") String twelveDigits
    );
}
