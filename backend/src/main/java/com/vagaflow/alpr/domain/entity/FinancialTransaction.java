package com.vagaflow.alpr.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro financeiro imutavel de liquidacao de operacao (Checkout de 1h ou Multa de No-Show 50%),
 * preservando a taxa fixa de R$ 1,00 da plataforma e o repasse ao dono do estacionamento.
 */
@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(name = "lot_id", nullable = false, length = 32)
    private String lotId;

    @Column(name = "spot_code", nullable = false, length = 16)
    private String spotCode;

    @Column(name = "license_plate", nullable = false, length = 10)
    private String licensePlate;

    @Column(name = "charged_from_customer_brl", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargedFromCustomerBrl;

    @Column(name = "transferred_to_owner_brl", nullable = false, precision = 10, scale = 2)
    private BigDecimal transferredToOwnerBrl;

    @Column(name = "transferred_to_platform_brl", nullable = false, precision = 10, scale = 2)
    private BigDecimal transferredToPlatformBrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinancialTransaction() {
    }

    public FinancialTransaction(
            String eventType,
            String lotId,
            String spotCode,
            String licensePlate,
            BigDecimal chargedFromCustomerBrl,
            BigDecimal transferredToOwnerBrl,
            BigDecimal transferredToPlatformBrl) {
        this.eventType = eventType;
        this.lotId = lotId;
        this.spotCode = spotCode;
        this.licensePlate = licensePlate;
        this.chargedFromCustomerBrl = chargedFromCustomerBrl;
        this.transferredToOwnerBrl = transferredToOwnerBrl;
        this.transferredToPlatformBrl = transferredToPlatformBrl;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getLotId() {
        return lotId;
    }

    public String getSpotCode() {
        return spotCode;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public BigDecimal getChargedFromCustomerBrl() {
        return chargedFromCustomerBrl;
    }

    public BigDecimal getTransferredToOwnerBrl() {
        return transferredToOwnerBrl;
    }

    public BigDecimal getTransferredToPlatformBrl() {
        return transferredToPlatformBrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
