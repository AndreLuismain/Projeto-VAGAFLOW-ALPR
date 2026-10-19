package com.vagaflow.alpr.dto;

import java.math.BigDecimal;

/**
 * Payload JSON estruturado trocado entre App B2C, API Spring Boot e Camera ALPR
 * no momento do bloqueio da vaga por 15 minutos (POST /api/v1/reservations/sync-gate).
 */
public record ReservationSyncGateResponse(
        String event,
        String reservationId,
        String timestamp,
        String expiresAt,
        int ttlMinutes,
        long remainingSeconds,
        String lotName,
        String status,
        CustomerVehiclePayload customerVehicle,
        ParkingLotSyncPayload parkingLotSync
) {
    public record CustomerVehiclePayload(
            String driverName,
            String licensePlate,
            String vehicleColor,
            PlatePhotoReferencePayload platePhotoReference
    ) {
    }

    public record PlatePhotoReferencePayload(
            String position,
            String url,
            boolean ocrPreValidated
    ) {
    }

    public record ParkingLotSyncPayload(
            String lotId,
            String allocatedSpotCode,
            String temporaryLockStatus,
            boolean indexedInColorPrefixTree,
            PricingRulePayload pricingRule
    ) {
    }

    public record PricingRulePayload(
            BigDecimal minimumHourlyTotalBRL,
            BigDecimal ownerBaseShareBRL,
            BigDecimal platformFixedFeeBRL,
            BigDecimal noShow15MinPenaltyTotalBRL,
            BigDecimal noShowOwnerShareBRL,
            BigDecimal noShowPlatformShareBRL
    ) {
    }
}
