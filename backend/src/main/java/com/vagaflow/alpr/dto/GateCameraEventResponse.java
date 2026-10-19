package com.vagaflow.alpr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Payload JSON retornado pelo motor ALPR nas operacoes de Entrada (ENTRY) e Saida (EXIT).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GateCameraEventResponse(
        String event,
        String treeLookupStrategy,
        String detectedPlate,
        String detectedColor,
        String licensePlate,
        String spotActivated,
        String releasedSpot,
        String gateCommand,
        BillingSplitPayload billingSplit,
        String message
) {
    public record BillingSplitPayload(
            BigDecimal totalChargedBRL,
            BigDecimal parkingOwnerShareBRL,
            BigDecimal platformFixedFeeBRL
    ) {
    }
}
