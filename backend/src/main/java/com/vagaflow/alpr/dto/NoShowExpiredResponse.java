package com.vagaflow.alpr.dto;

import java.math.BigDecimal;

/**
 * Payload JSON disparado quando o cronometro de 15 minutos zera sem leitura da placa
 * na camera de entrada (RESERVATION_EXPIRED_NO_SHOW_15MIN).
 */
public record NoShowExpiredResponse(
        String event,
        String reservationId,
        String licensePlate,
        String spotReleased,
        AutomaticSplitChargePayload automaticSplitCharge
) {
    public record AutomaticSplitChargePayload(
            BigDecimal chargedFromCustomerBRL,
            BigDecimal transferredToParkingOwnerBRL,
            BigDecimal transferredToPlatformBRL,
            String ruleApplied
    ) {
    }
}
