package com.vagaflow.alpr.repository;

import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotRepository extends JpaRepository<Spot, Long> {

    List<Spot> findByParkingLotIdOrderByCodeAsc(String parkingLotId);

    Optional<Spot> findFirstByParkingLotIdAndStatusOrderByCodeAsc(String parkingLotId, SpotStatus status);

    Optional<Spot> findByParkingLotIdAndCode(String parkingLotId, String code);
}
