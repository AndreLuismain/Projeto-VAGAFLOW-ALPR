package com.vagaflow.alpr.domain.enums;

/**
 * Ciclo de vida de uma reserva de vaga.
 */
public enum ReservationStatus {
    RESERVED,
    ACTIVE,
    COMPLETED,
    EXPIRED_NO_SHOW,
    CANCELLED
}
