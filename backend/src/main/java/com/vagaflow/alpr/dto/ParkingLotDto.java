package com.vagaflow.alpr.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParkingLotDto(
        String id,
        String name,
        String distance,
        BigDecimal ownerHourlyRate,
        BigDecimal totalHourlyPrice,
        BigDecimal noShowPenaltyBRL,
        List<SpotDto> spots
) {
}
