package com.vagaflow.alpr.repository;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, String> {
}
