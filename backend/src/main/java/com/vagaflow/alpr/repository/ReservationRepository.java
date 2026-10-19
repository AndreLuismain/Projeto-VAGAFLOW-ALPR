package com.vagaflow.alpr.repository;

import com.vagaflow.alpr.domain.entity.Reservation;
import com.vagaflow.alpr.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    Optional<Reservation> findFirstByLotIdAndSpotCodeAndStatusIn(
            String lotId, String spotCode, List<ReservationStatus> statuses);
}
